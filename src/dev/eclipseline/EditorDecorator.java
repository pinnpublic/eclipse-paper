package dev.eclipseline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.*;
import org.eclipse.ui.part.MultiPageEditorPart;

/** Public editor adapters and lifecycle listeners, without replacing editors or content providers. */
public final class EditorDecorator implements AutoCloseable {
    private final IWorkbench workbench;
    private final IPreferenceStore store;
    private NotebookSettings settings;
    private final Map<StyledText, NotebookSurface> surfaces = new IdentityHashMap<>();
    private final Set<IWorkbenchWindow> windows = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<IWorkbenchPage> pages = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<MultiPageEditorPart, org.eclipse.jface.dialogs.IPageChangedListener> multi = new IdentityHashMap<>();
    private final Set<IEditorPart> attaching = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean pending, closed;
    private AutoCloseable themeChanges;
    private boolean restorePending;
    private final Listener restored=event->{
        if(closed||restorePending||!(event.widget instanceof org.eclipse.swt.widgets.Shell))return;
        boolean owned=false;for(IWorkbenchWindow window:windows)if(window.getShell()==event.widget){owned=true;break;}
        if(!owned)return;restorePending=true;
        event.display.asyncExec(()->{
            restorePending=false;if(closed)return;scan();
            for(NotebookSurface surface:new ArrayList<>(surfaces.values()))surface.restored();
        });
    };
    private final IPropertyChangeListener editorPreferences=event->{
        String key=event.getProperty();
        if(key.equals(org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants.EDITOR_LINE_NUMBER_RULER)||key.toLowerCase(java.util.Locale.ROOT).contains("quickdiff"))schedule(false);
    };
    private final IPropertyChangeListener preferences = event -> {
        if (event.getProperty().startsWith(NotebookSettings.PREFIX)) schedule(true);
    };
    private final Listener focus = event -> {
        if(event.widget instanceof StyledText)decorateBeforePaint((StyledText)event.widget);
    };
    private final Listener firstPaint = event -> {
        if(event.widget instanceof StyledText)decorateBeforePaint((StyledText)event.widget);
    };
    private final IPartListener2 parts = new IPartListener2() {
        @Override public void partOpened(IWorkbenchPartReference r) { partReady(r); }
        @Override public void partActivated(IWorkbenchPartReference r) { partReady(r); }
        @Override public void partVisible(IWorkbenchPartReference r) { partReady(r); }
        @Override public void partInputChanged(IWorkbenchPartReference r) { partReady(r); }
        @Override public void partClosed(IWorkbenchPartReference r) {
            IWorkbenchPart part = r.getPart(false);
            if (part instanceof MultiPageEditorPart) removeMulti((MultiPageEditorPart) part);
        }
        @Override public void partDeactivated(IWorkbenchPartReference r) { }
        @Override public void partBroughtToTop(IWorkbenchPartReference r) { partReady(r); }
        @Override public void partHidden(IWorkbenchPartReference r) { }
    };
    private final IPageListener pageListener = new IPageListener() {
        @Override public void pageOpened(IWorkbenchPage page) { attachPage(page); }
        @Override public void pageActivated(IWorkbenchPage page) { attachPage(page); schedule(false); }
        @Override public void pageClosed(IWorkbenchPage page) { page.removePartListener(parts); pages.remove(page); }
    };
    private final IWindowListener windowListener = new IWindowListener() {
        @Override public void windowOpened(IWorkbenchWindow window) { attachWindow(window); }
        @Override public void windowActivated(IWorkbenchWindow window) { attachWindow(window); schedule(false); }
        @Override public void windowDeactivated(IWorkbenchWindow window) { }
        @Override public void windowClosed(IWorkbenchWindow window) {
            window.removePageListener(pageListener); windows.remove(window);
            for (IWorkbenchPage page : new ArrayList<>(pages)) {
                if (page.getWorkbenchWindow() == window) { page.removePartListener(parts); pages.remove(page); }
            }
        }
    };
    public EditorDecorator(IWorkbench workbench, IPreferenceStore store) {
        this.workbench = workbench; this.store = store; settings = NotebookSettings.read(store);
    }
    public void start() {
        workbench.addWindowListener(windowListener); store.addPropertyChangeListener(preferences);
        workbench.getDisplay().addFilter(SWT.FocusIn, focus);
        workbench.getDisplay().addFilter(SWT.Paint, firstPaint);
        workbench.getDisplay().addFilter(SWT.Deiconify,restored);
        org.eclipse.ui.editors.text.EditorsUI.getPreferenceStore().addPropertyChangeListener(editorPreferences);
        themeChanges=NotebookTheme.watch(workbench.getDisplay(),()->{
            readSettings();
            for(NotebookSurface surface:new ArrayList<>(surfaces.values()))surface.refresh();
        });
        for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) attachWindow(window);
        scan();
    }
    private void partReady(IWorkbenchPartReference reference) {
        readSettings();
        IWorkbenchPart part=reference.getPart(false);
        if(part instanceof IEditorPart)decorate((IEditorPart)part);
        else if(part==null)schedule(false);
    }
    private void decorateBeforePaint(StyledText text) {
        if(closed||!settings.enabled||text.isDisposed())return;
        Object existing=text.getData(NotebookSurface.DATA_KEY);
        if(existing instanceof NotebookSurface) {
            // A newly opened editor can acquire its real size only just before this paint.
            if(text.getBackgroundImage()==null)((NotebookSurface)existing).prepare();
            return;
        }
        // The first native paint must already see both paper and rules, even for lazy source pages.
        for(IWorkbenchPage page:new ArrayList<>(pages))for(IEditorReference reference:page.getEditorReferences()) {
            IEditorPart editor=reference.getEditor(false);
            if(editor!=null&&owns(editor,text)){decorate(editor);return;}
        }
    }
    private boolean owns(IEditorPart editor,StyledText text) {
        if(editor instanceof MultiPageEditorPart) {
            Object selected=((MultiPageEditorPart)editor).getSelectedPage();
            if(selected instanceof IEditorPart&&selected!=editor&&owns((IEditorPart)selected,text))return true;
            if(selected instanceof Control&&contains((Control)selected,text))return true;
        }
        Control root=editor.getAdapter(Control.class);
        if(root!=null&&contains(root,text))return true;
        ITextOperationTarget target=editor.getAdapter(ITextOperationTarget.class);
        return target instanceof ITextViewer&&((ITextViewer)target).getTextWidget()==text;
    }
    private static boolean contains(Control root,Control child) {
        for(Control current=child;current!=null;current=current.getParent())if(current==root)return true;
        return false;
    }
    private void attachWindow(IWorkbenchWindow window) {
        if (windows.add(window)) window.addPageListener(pageListener);
        for (IWorkbenchPage page : window.getPages()) attachPage(page);
    }
    private void attachPage(IWorkbenchPage page) {
        if (pages.add(page)) page.addPartListener(parts);
        schedule(false);
    }
    private boolean preferencePending;
    private void schedule(boolean preferenceChange) {
        if (closed || workbench.getDisplay().isDisposed()) return;
        preferencePending |= preferenceChange;
        if (pending) return; pending = true;
        workbench.getDisplay().asyncExec(() -> {
            pending = false; if (closed) return;
            boolean refresh = preferencePending; preferencePending = false;
            if (refresh) readSettings();
            scan();
            if (refresh) for (NotebookSurface surface : new ArrayList<>(surfaces.values())) surface.refresh();
        });
    }
    public void scan() {
        for (IWorkbenchPage page : new ArrayList<>(pages)) {
            for (IEditorReference ref : page.getEditorReferences()) {
                IEditorPart editor = ref.getEditor(false); // Never force lazy editors to open.
                if (editor != null) decorate(editor);
            }
        }
        surfaces.entrySet().removeIf(entry -> entry.getKey().isDisposed());
    }
    private void readSettings() {
        NotebookSettings next=NotebookSettings.read(store);
        if(!next.equals(settings))settings=next;
    }
    public void refreshSettings() {
        if(closed)return;readSettings();scan();
        for(NotebookSurface surface:new ArrayList<>(surfaces.values()))surface.refresh();
    }
    private void decorate(IEditorPart editor) {
        if(!attaching.add(editor))return;
        try {
            if (editor instanceof MultiPageEditorPart) {
                MultiPageEditorPart part = (MultiPageEditorPart) editor;
                if (!multi.containsKey(part)) {
                    org.eclipse.jface.dialogs.IPageChangedListener listener = event -> decorate(part);
                    multi.put(part, listener); part.addPageChangedListener(listener);
                }
                Object selected = part.getSelectedPage();
                if (selected instanceof IEditorPart && selected != editor) decorate((IEditorPart) selected);
                else if (selected instanceof Control) walk((Control) selected);
            }
            Control control = editor.getAdapter(Control.class);
            if (control != null) walk(control);
            ITextOperationTarget target = editor.getAdapter(ITextOperationTarget.class);
            if (target instanceof ITextViewer || control instanceof StyledText) {
                StyledText text=control instanceof StyledText?(StyledText)control:((ITextViewer) target).getTextWidget();walk(text);
                NotebookSurface surface=surfaces.get(text);
                if(surface!=null&&target instanceof ITextViewer)surface.attachViewer((ITextViewer)target);
                org.eclipse.jface.text.source.IVerticalRulerInfo info=editor.getAdapter(org.eclipse.jface.text.source.IVerticalRulerInfo.class);
                if(surface!=null&&info instanceof org.eclipse.jface.text.source.CompositeRuler) {
                    java.util.Iterator<org.eclipse.jface.text.source.IVerticalRulerColumn> columns=((org.eclipse.jface.text.source.CompositeRuler)info).getDecoratorIterator();
                    while(columns.hasNext()) {
                        org.eclipse.jface.text.source.IVerticalRulerColumn column=columns.next();
                        if(column instanceof org.eclipse.jface.text.source.LineNumberRulerColumn
                            || column instanceof org.eclipse.ui.texteditor.rulers.IContributedRulerColumn
                            && "org.eclipse.ui.editors.columns.linenumbers".equals(((org.eclipse.ui.texteditor.rulers.IContributedRulerColumn)column).getDescriptor().getId()))
                            surface.attachRuler(column,target instanceof ITextViewer?(ITextViewer)target:null);
                        // Projection/folding columns also extend AnnotationRulerColumn.
                        else if(column instanceof org.eclipse.jface.text.source.AnnotationRulerColumn
                            || column instanceof org.eclipse.ui.texteditor.rulers.IContributedRulerColumn
                            && "org.eclipse.ui.editors.columns.annotations".equals(((org.eclipse.ui.texteditor.rulers.IContributedRulerColumn)column).getDescriptor().getId()))
                            surface.attachAnnotations(column,target instanceof ITextViewer?(ITextViewer)target:null);
                    }
                }
            }
        } catch (RuntimeException error) { Activator.log("편집기 노트 연결에 실패했습니다: " + editor.getTitle(), error); }
        finally {attaching.remove(editor);}
    }
    private void walk(Control control) {
        if (control == null || control.isDisposed()) return;
        if (control instanceof StyledText) {
            StyledText text = (StyledText) control;
            if (!surfaces.containsKey(text) && text.getData(NotebookSurface.DATA_KEY) == null)
                surfaces.put(text, new NotebookSurface(text, () -> settings, true));
            NotebookSurface surface = surfaces.get(text);
            if (surface != null) surface.prepare();
        } else if (control instanceof Composite) {
            for (Control child : ((Composite) control).getChildren()) walk(child);
        }
    }
    private void removeMulti(MultiPageEditorPart part) {
        org.eclipse.jface.dialogs.IPageChangedListener listener = multi.remove(part);
        if (listener != null) part.removePageChangedListener(listener);
    }
    @Override public void close() {
        if (closed) return; closed = true;
        store.removePropertyChangeListener(preferences); workbench.removeWindowListener(windowListener);
        workbench.getDisplay().removeFilter(SWT.FocusIn, focus);
        workbench.getDisplay().removeFilter(SWT.Paint, firstPaint);
        workbench.getDisplay().removeFilter(SWT.Deiconify,restored);
        org.eclipse.ui.editors.text.EditorsUI.getPreferenceStore().removePropertyChangeListener(editorPreferences);
        if(themeChanges!=null)try{themeChanges.close();}catch(Exception error){Activator.log("테마 알림 해제에 실패했습니다.",error);}
        for (IWorkbenchWindow window : windows) window.removePageListener(pageListener);
        for (IWorkbenchPage page : pages) page.removePartListener(parts);
        for (MultiPageEditorPart part : new ArrayList<>(multi.keySet())) removeMulti(part);
        for (NotebookSurface surface : surfaces.values()) surface.close();
        surfaces.clear(); windows.clear(); pages.clear();
    }
}

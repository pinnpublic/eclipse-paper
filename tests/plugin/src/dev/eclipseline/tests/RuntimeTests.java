package dev.eclipseline.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import dev.eclipseline.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

/** Runs inside org.eclipse.ui.ide.workbench with isolated configuration and workspace. */
public final class RuntimeTests implements IStartup {
    private final List<String> results = new ArrayList<>();
    private Display display;
    private Path output;
    private IPreferenceStore store;
    @Override public void earlyStartup() {
        if (!Boolean.getBoolean("notebook.test.realWorkbench")) return;
        display = PlatformUI.getWorkbench().getDisplay();
        display.asyncExec(() -> display.timerExec(1800, this::runAndReport));
    }
    private void runAndReport() {
        output = Path.of(System.getProperty("notebook.test.result")).getParent();
        try {
            runTests();
            results.add("PASS: ALL " + results.size() + " checks; Java " + System.getProperty("java.version")
                + "; Eclipse " + Platform.getBundle("org.eclipse.platform").getVersion());
        } catch (Throwable error) {
            results.add("FAIL: " + error);
            java.io.StringWriter trace = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(trace)); results.add(trace.toString());
        }
        try { Files.write(output.resolve("results.txt"), results, StandardCharsets.UTF_8); }
        catch (Exception error) { error.printStackTrace(); }
        System.out.println(String.join("\n", results));
        if (!Boolean.getBoolean("notebook.test.keepOpen")) display.asyncExec(() -> PlatformUI.getWorkbench().close());
    }

    private void runTests() throws Exception {
        Activator plugin = Activator.getDefault(); plugin.initialize(); store = plugin.getPreferenceStore();
        check("Paper".equals(plugin.getBundle().getHeaders().get("Bundle-Name")),"public bundle name is Paper");
        check(plugin.getBundle().getEntry("LICENSE")!=null,"MIT license is included in the plug-in JAR");
        check(Arrays.stream(Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.ui.preferencePages"))
            .anyMatch(element->NotebookPreferencePage.ID.equals(element.getAttribute("id"))&&"Paper / 노트 배경".equals(element.getAttribute("name"))),
            "public preference page name is Paper while retaining the existing ID");
        EditorsUI.getPreferenceStore().setValue(org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants.EDITOR_LINE_NUMBER_RULER,true);
        IWorkbench workbench = PlatformUI.getWorkbench();
        if (workbench.getIntroManager().getIntro() != null) workbench.getIntroManager().closeIntro(workbench.getIntroManager().getIntro());
        IWorkbenchPage page = workbench.showPerspective("dev.eclipseline.tests.perspective", workbench.getActiveWorkbenchWindow());
        if(Boolean.getBoolean("notebook.test.themeOnly")){themeTests(page);return;}
        if(Boolean.getBoolean("notebook.test.openingOnly")){openingTests(page);return;}
        check(NotebookSettings.LINE_NAMES.length == 10 && NotebookSettings.PAPER_NAMES.length == 12, "10 line styles and 12 paper patterns available");
        check(workbench.getPreferenceManager().find("org.eclipse.ui.preferencePages.Workbench/org.eclipse.ui.preferencePages.Editors/org.eclipse.ui.preferencePages.GeneralTextEditor/" + NotebookPreferencePage.ID) != null
            || preferenceRegistered(), "Notebook preference page registered");
        renderingTests();
        textureTests();

        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("NotebookCompatibility");
        project.create(null); project.open(null);
        IProjectDescription description = project.getDescription(); description.setNatureIds(new String[] {"org.eclipse.jdt.core.javanature"});
        project.setDescription(description, null);
        IFile javaFile = file(project, "Example.java", "public class Example {\n    String memo = \"한글 공책 테스트\";\n    int pages = 10;\n}\n");
        IFile textFile = file(project, "notes.txt", longText());
        IFile xmlFile = file(project, "example.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<notebook>\n    <memo>한글 공책</memo>\n</notebook>\n");
        IFile jspFile = file(project, "example.jsp", "<%@ page contentType=\"text/html; charset=UTF-8\" %>\n<html>\n<body>한글 노트</body>\n</html>\n");
        IEditorPart javaEditor = page.openEditor(new FileEditorInput(javaFile), "org.eclipse.jdt.ui.CompilationUnitEditor");
        pump(); StyledText javaText = widget(javaEditor);
        await(() -> attached(javaText));
        check(attached(javaText), "Java source editor automatically decorated; area=" + javaText.getClientArea()
            + "; surface=" + javaText.getData(NotebookSurface.DATA_KEY) + "; visible=" + javaText.isVisible());
        String original = document(javaEditor).get();
        Font originalFont = javaText.getFont();
        check(!javaEditor.isDirty(), "decoration does not mark source dirty");
        capture(javaText, "java-notebook.png");

        for (IFile file : new IFile[] {textFile, xmlFile, jspFile}) {
            String editorId = file == textFile ? EditorsUI.DEFAULT_TEXT_EDITOR_ID : workbench.getEditorRegistry().getDefaultEditor(file.getName()).getId();
            IEditorPart editor = page.openEditor(new FileEditorInput(file), editorId);
            if (editor instanceof MultiPageEditorPart) {
                MultiPageEditorPart multi = (MultiPageEditorPart) editor;
                for (IEditorPart child : multi.findEditors(editor.getEditorInput())) {
                    if (child.getAdapter(Control.class) instanceof StyledText) { multi.setActiveEditor(child); break; }
                }
            }
            pump(); StyledText text = widget(editor);
            await(() -> attached(text));
            check(attached(text), file.getFileExtension() + " editor automatically decorated: " + editorId);
            if (editor instanceof MultiPageEditorPart) {
                MultiPageEditorPart multi = (MultiPageEditorPart) editor;
                Object source = multi.getSelectedPage();
                java.lang.reflect.Method select = MultiPageEditorPart.class.getDeclaredMethod("setActivePage", int.class);
                select.setAccessible(true); select.invoke(multi, 0); pump();
                if (source instanceof IEditorPart) multi.setActiveEditor((IEditorPart) source);
                pump(); check(attached(widget(editor)), file.getFileExtension() + " source page remains decorated after page switch");
            }
            if (file == textFile) scrollAndZoomTests(text, editor);
            else capture(text, file.getFileExtension() + "-notebook.png");
        }
        page.activate(javaEditor); pump();
        NotebookSettings defaults = NotebookSettings.read(store, true);
        defaults.write(store); pump();
        int ruledSpacing = javaText.getLineSpacing();
        store.setValue(NotebookSettings.PREFIX + "enabled", false); pump();
        check(javaText.getBackgroundImage() == null && javaText.getLineSpacing() < ruledSpacing,
            "disable restores original background and line spacing immediately");
        RGB originalBackground = javaText.getBackground().getRGB();
        store.setValue(NotebookSettings.PREFIX + "enabled", true); pump();
        check(attached(javaText) && javaText.getBackgroundImage() != null, "enable redraws already open editors without restart");
        store.setValue(NotebookSettings.PREFIX + "theme", false);
        org.eclipse.jface.preference.PreferenceConverter.setValue(store, NotebookSettings.PREFIX + "paperColor", new RGB(250, 247, 237));
        pump(); check(javaText.getBackground().getRGB().equals(new RGB(250,247,237)), "custom paper color applied");
        rulerTests(javaEditor,javaText);
        store.setValue(NotebookSettings.PREFIX + "theme", true); pump();
        check(javaText.getBackground().getRGB().equals(originalBackground), "theme background restored after custom paper color");
        check(javaText.getFont().equals(originalFont) && original.equals(document(javaEditor).get()) && !javaEditor.isDirty(),
            "font, source text and dirty state preserved across preference changes");
        currentLineAlignmentTests(javaText);
        preferenceTests(page, javaText);
        scratchTests();

        IWorkbenchWindow other = workbench.openWorkbenchWindow("dev.eclipseline.tests.perspective", ResourcesPlugin.getWorkspace().getRoot());
        pump(); IEditorPart otherEditor = other.getActivePage().openEditor(new FileEditorInput(javaFile), "org.eclipse.jdt.ui.CompilationUnitEditor");
        pump(); await(() -> attached(widget(otherEditor)));
        check(attached(widget(otherEditor)), "source editor in a second workbench window decorated");
        other.close(); pump();
        page.activate(javaEditor); pump();
        Image backgroundImage = javaText.getBackgroundImage();
        page.closeEditor(javaEditor, false); pump();
        check(javaText.isDisposed() && backgroundImage.isDisposed(), "closing editor releases generated background image");
        javaEditor = page.openEditor(new FileEditorInput(javaFile), "org.eclipse.jdt.ui.CompilationUnitEditor");
        pump(); StyledText reopened = widget(javaEditor); await(() -> attached(reopened));
        check(attached(reopened), "reopened editor decorated");
        check(new String(javaFile.getContents().readAllBytes(), StandardCharsets.UTF_8).equals(original), "UTF-8 source file unchanged on disk");
        defaults.write(store); pump();
        openingTests(page);
        themeTests(page);
    }

    private boolean preferenceRegistered() {
        return Arrays.stream(Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.ui.preferencePages"))
            .anyMatch(element -> NotebookPreferencePage.ID.equals(element.getAttribute("id")));
    }
    private void renderingTests() throws Exception {
        NotebookSettings d = NotebookSettings.read(store);
        double[] rules = {31, 63, 95, 127, 159};
        PaperRenderer.Layout layout = new PaperRenderer.Layout(480, 180, 24, 32, 0, 31, rules);
        Set<Integer> styles = new HashSet<>(), papers = new HashSet<>();
        for (int style = 0; style < 10; style++) {
            NotebookSettings s = config(d, style, 0, true);
            ImageData data = PaperRenderer.render(s, layout, d.paperColor, 100);
            styles.add(Arrays.hashCode(data.data)); save(data, "line-" + style + ".png");
        }
        check(styles.size() == 10, "all 10 line styles render distinct geometry");
        for (int paper = 0; paper < 12; paper++) {
            NotebookSettings s = config(d, 0, paper, false);
            ImageData data = PaperRenderer.render(s, layout, d.paperColor, 150);
            papers.add(Arrays.hashCode(data.data)); save(data, "paper-" + paper + ".png");
        }
        check(papers.size() == 12, "all 12 paper patterns render distinctly");
        NotebookSettings solid = new NotebookSettings(true,true,false,0,0,4,15,100,100,25,
            new RGB(100,130,160),d.paperColor,d.patternColor);
        for (int zoom : new int[] {75,100,125,150,175,200,250,300}) {
            ImageData data = PaperRenderer.render(solid, layout, d.paperColor, zoom);
            check(data.width == Math.round(480 * zoom / 100f) && data.height == Math.round(180 * zoom / 100f),
                "native vector rendering dimensions at " + zoom + "%");
            int bg = data.palette.getPixel(d.paperColor), coloredRows = 0;
            for (int y = 0; y < (int) (50 * zoom / 100.0); y++) {
                int pixel = data.getPixel(10, y);
                if (pixel != bg) {
                    coloredRows++;
                    for (int x = 11; x < data.width - 10; x++)
                        if (data.getPixel(x,y) != pixel) throw new AssertionError("Uneven straight line at " + zoom);
                }
            }
            check(coloredRows == Math.max(1, Math.round(24 * .04f * zoom / 100)),
                "pixel-aligned solid line without fringe at " + zoom + "% (rows=" + coloredRows + ")");
            save(data, "quality-" + zoom + ".png");
        }
    }

    private void scrollAndZoomTests(StyledText text, IEditorPart editor) throws Exception {
        Image before = text.getBackgroundImage();
        text.setTopPixel(67); pump();
        check(text.getBackgroundImage() != before && before.isDisposed(), "vertical scrolling regenerates aligned paper and releases previous image");
        PaperRenderer.Layout layout = NotebookSurface.layout(text, text.getLineHeight(), NotebookSettings.read(store));
        check(layout.originY < 0 && layout.rules.length > 3, "paper origin remains anchored to scrolled document");
        text.setHorizontalPixel(80); pump();
        check(NotebookSurface.layout(text, text.getLineHeight(), NotebookSettings.read(store)).horizontalPixel == text.getHorizontalPixel(), "horizontal scroll included in pattern alignment");
        Font old = text.getFont();
        int oldSize = old.getFontData()[0].getHeight();
        int baseRow = text.getLineHeight() + text.getLineSpacing();
        FontData[] data = old.getFontData();
        for (FontData fd : data) fd.setHeight(oldSize * 2);
        Font large = new Font(display, data); text.setFont(large); pump();
        check(text.getLineHeight() + text.getLineSpacing() >= baseRow * 1.7,
            "font zoom scales rules, spacing and pattern cells automatically");
        capture(text, "text-zoom-200.png"); text.setFont(old); pump(); large.dispose();
        check(text.getLineHeight() + text.getLineSpacing() == baseRow, "zoom back restores exact original row metrics");
        text.setSelection(0, Math.min(20, text.getCharCount())); pump();
        check(text.getSelectionCount() == 20, "selection remains usable over patterned background");
        text.setSelection(0); text.setTopPixel(0); pump();
        check(!editor.isDirty(), "zoom and scroll do not modify document");
    }

    private void preferenceTests(IWorkbenchPage page, StyledText javaText) throws Exception {
        PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(page.getWorkbenchWindow().getShell(), NotebookPreferencePage.ID, null, null);
        dialog.setBlockOnOpen(false); dialog.open(); pump();
        NotebookPreferencePage pref = (NotebookPreferencePage) dialog.getSelectedPage();
        check(pref != null && pref.isValid(), "preference page and live preview open without errors");
        Button theme = field(pref, "theme", Button.class); theme.setSelection(false); theme.notifyListeners(SWT.Selection,new Event());
        Combo paper = field(pref,"paper",Combo.class); paper.select(2); paper.notifyListeners(SWT.Selection,new Event());
        Spinner gap = field(pref,"gap",Spinner.class);
        check(gap.getMinimum() == -50 && gap.getMaximum() == 100, "gap preference accepts -50 through 100 percent");
        gap.setSelection(-25); gap.notifyListeners(SWT.Selection,new Event());
        Spinner spacing = field(pref,"spacing",Spinner.class);
        check(spacing.getMinimum() == 0 && spacing.getMaximum() == 200, "independent additional line spacing accepts 0 through 200 percent");
        spacing.setSelection(60); spacing.notifyListeners(SWT.Selection,new Event());
        Combo texture = field(pref,"texture",Combo.class);
        check(texture.getItemCount() == NotebookSettings.TEXTURE_NAMES.length, "paper material selector offers all textures plus none");
        texture.select(17); texture.notifyListeners(SWT.Selection,new Event());
        Spinner highlightOpacity=field(pref,"highlightOpacity",Spinner.class);
        check(highlightOpacity.getMinimum()==0&&highlightOpacity.getMaximum()==100,"highlight opacity preference accepts 0 through 100 percent");
        highlightOpacity.setSelection(25);highlightOpacity.notifyListeners(SWT.Selection,new Event());
        Combo zoom = field(pref,"zoom",Combo.class); zoom.select(4); zoom.notifyListeners(SWT.Selection,new Event()); pump();
        StyledText preview=field(pref,"preview",StyledText.class);Image previewImage=preview.getBackgroundImage();
        for(int i=0;i<20;i++){gap.notifyListeners(SWT.Modify,new Event());gap.notifyListeners(SWT.Selection,new Event());}
        check(preview.getBackgroundImage()==previewImage,"unchanged preference events retain the existing preview image");
        captureTree(dialog.getShell(), "preferences.png");
        capture(field(pref,"preview",StyledText.class), "preferences-preview.png");
        TabFolder settingsTabs = (TabFolder) texture.getParent().getParent();
        settingsTabs.setSelection(1); pump();
        captureTree(dialog.getShell(), "preferences-background.png");
        settingsTabs.setSelection(0); pump();
        check(store.getInt(NotebookSettings.PREFIX + "gap") != -25, "preview edits do not apply before Apply");
        check(pref.performOk(), "Apply saves preferences"); pump();
        check(store.getInt(NotebookSettings.PREFIX+"gap") == -25 && javaText.getBackground().getRGB().equals(NotebookSettings.TEXTURE_COLORS[17]),
            "Apply immediately updates existing editor");
        check(store.getInt(NotebookSettings.PREFIX+"spacing") == 60 && store.getInt(NotebookSettings.PREFIX+"texture") == 17,
            "independent spacing and paper texture persist together");
        String saved = org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE.getNode(Activator.ID).get("notebook.gap", null);
        check("-25".equals(saved), "negative gap flushed to persistent workspace settings");
        dialog.close(); pump();
        dialog = PreferencesUtil.createPreferenceDialogOn(page.getWorkbenchWindow().getShell(), NotebookPreferencePage.ID,null,null);
        dialog.setBlockOnOpen(false); dialog.open(); pump(); pref = (NotebookPreferencePage) dialog.getSelectedPage();
        check(field(pref,"gap",Spinner.class).getSelection() == -25 && field(pref,"spacing",Spinner.class).getSelection() == 60
            && field(pref,"texture",Combo.class).getSelectionIndex() == 17, "negative gap, spacing and texture reload when reopening preferences");
        check(field(pref,"highlightOpacity",Spinner.class).getSelection()==25,"highlight opacity saves and reloads");
        StyledText defaultPreview=field(pref,"preview",StyledText.class);Image beforeDefaults=defaultPreview.getBackgroundImage();
        java.lang.reflect.Method defaults=NotebookPreferencePage.class.getDeclaredMethod("performDefaults");defaults.setAccessible(true);defaults.invoke(pref);pump();
        NotebookSettings defaultDraft=field(pref,"draft",NotebookSettings.class);
        check(defaultDraft.equals(NotebookSettings.read(store,true))&&defaultPreview.getBackgroundImage()!=beforeDefaults,
            "Restore Defaults refreshes the preview while retaining saved settings until Apply");
        check(store.getInt(NotebookSettings.PREFIX+"gap")==-25,"Restore Defaults does not save before Apply");
        Button enabled = field(pref,"enabled",Button.class); enabled.setSelection(false); enabled.notifyListeners(SWT.Selection,new Event());
        pref.performCancel(); dialog.close(); pump();
        check(store.getBoolean(NotebookSettings.PREFIX+"enabled"), "Cancel discards preview-only edits");
    }

    private void scratchTests() throws Exception {
        Shell shell = new Shell(display); shell.setText("Notebook visual checks"); shell.setLayout(new FillLayout()); shell.setSize(720,420);
        StyledText text = new StyledText(shell, SWT.MULTI|SWT.WRAP|SWT.V_SCROLL);
        text.setFont(org.eclipse.jface.resource.JFaceResources.getTextFont());
        text.setText("Wrapped source: " + "notebook / 공책 / ".repeat(55) + "\nSecond line\n");
        text.setLineSpacing(3); RGB original = text.getBackground().getRGB();
        NotebookSettings[] s = { config(NotebookSettings.read(store),0,2,true) };
        NotebookSurface surface = new NotebookSurface(text, () -> s[0]);
        shell.open(); pump();
        PaperRenderer.Layout l = NotebookSurface.layout(text,text.getLineHeight(),s[0]);
        check(l.rules.length > text.getLineCount(), "wrapped visual rows and empty area each receive notebook rules");
        ImageData data = text.getBackgroundImage().getImageData(200);
        check(data.width == text.getClientArea().width * 2, "background provider supplies freshly rendered 200% native pixels");
        text.setLineBackground(0,1,display.getSystemColor(SWT.COLOR_YELLOW)); pump();
        ImageData expectedRules = PaperRenderer.renderRules(s[0], l, text.getBackground().getRGB(), 100);
        ImageData painted = captureData(text);
        int testX = Math.min(painted.width, expectedRules.width) - 25;
        boolean foundRule = false;
        for (int y = 0; y < Math.min(painted.height, l.rowHeight); y++) {
            if (expectedRules.getAlpha(testX,y) == 255) {
                foundRule = true;
                check(painted.palette.getRGB(painted.getPixel(testX,y)).equals(
                    expectedRules.palette.getRGB(expectedRules.getPixel(testX,y))),
                    "notebook rule remains visible over opaque current-line background");
                break;
            }
        }
        check(foundRule && expectedRules.getAlpha(testX,0) == 0, "rule overlay is transparent outside text-to-line gap");
        text.setLineBackground(0,1,null); pump();
        capture(text, "wrapped-grid.png");
        independentSpacingTests(text, surface, s);
        tabAlignmentTests(text,s[0]);
        s[0] = new NotebookSettings(false,true,false,0,2,4,15,100,38,22,
            s[0].lineColor,s[0].paperColor,s[0].patternColor); surface.refresh(); pump();
        check(text.getBackgroundImage() == null && text.getLineSpacing() == 3 && text.getBackground().getRGB().equals(original),
            "disable restores pre-existing nonzero spacing and background");
        surface.close(); shell.close(); pump();
    }

    private NotebookSettings config(NotebookSettings d, int style, int paper, boolean lines) {
        return new NotebookSettings(true,lines,false,style,paper,d.width,d.gap,d.spacing,d.scale,60,35,
            d.texture,d.textureOpacity,d.lineColor,d.paperColor,d.patternColor);
    }
    private void textureTests() throws Exception {
        NotebookSettings d = NotebookSettings.read(store,true);
        PaperRenderer.Layout small = new PaperRenderer.Layout(480,240,12,16,0,14,new double[0]);
        PaperRenderer.Layout large = new PaperRenderer.Layout(480,240,48,64,0,56,new double[0]);
        Set<Integer> hashes = new HashSet<>();
        for (int texture = 0; texture < NotebookSettings.TEXTURE_NAMES.length; texture++) {
            NotebookSettings s = new NotebookSettings(true,false,false,0,0,4,15,30,100,38,22,
                texture,65,d.lineColor,new RGB(235,224,204),d.patternColor);
            ImageData data = PaperRenderer.renderBackground(s,small,s.paperColor,100);
            hashes.add(Arrays.hashCode(data.data));
            check(Arrays.equals(data.data,PaperRenderer.renderBackground(s,large,s.paperColor,100).data),
                "texture " + texture + " keeps material scale when editor font changes");
            save(data,"texture-" + texture + ".png");
        }
        check(hashes.size() == NotebookSettings.TEXTURE_NAMES.length, "all paper material textures render distinctly on the same paper color");
        NotebookSettings zero = new NotebookSettings(true,false,false,0,0,4,15,30,100,38,22,
            6,0,d.lineColor,d.paperColor,d.patternColor);
        ImageData flat = PaperRenderer.renderBackground(zero,small,d.paperColor,100);
        int pixel = flat.getPixel(0,0);
        boolean uniform = true;
        for (int y=0;y<flat.height;y++) for (int x=0;x<flat.width;x++) uniform &= flat.getPixel(x,y) == pixel;
        check(uniform, "zero texture strength produces a uniform paper color");
    }
    private void independentSpacingTests(StyledText text,NotebookSurface surface,NotebookSettings[] current) throws Exception {
        NotebookSettings d = current[0];
        int height = text.getLineHeight();
        for (int gap : new int[] {-50,0,100}) {
            current[0] = new NotebookSettings(true,true,false,0,2,4,gap,60,100,38,22,
                0,40,d.lineColor,d.paperColor,d.patternColor);
            surface.refresh(); pump();
            PaperRenderer.Layout layout = NotebookSurface.layout(text,height,current[0]);
            check(text.getLineSpacing() == 3 + (int)Math.ceil(height * .60),
                "changing gap to " + gap + " does not change independent line spacing");
            double expected = text.getLocationAtOffset(0).y + height + height * gap / 100.0 + Math.max(1,height * .04) / 2;
            check(Math.abs(layout.rules[0] - expected) < .01, "gap " + gap + " moves rule to requested text-relative position");
            if (gap == -50) capture(text,"negative-gap.png");
        }
        current[0] = new NotebookSettings(true,false,false,0,2,4,-50,200,100,38,22,
            0,40,d.lineColor,d.paperColor,d.patternColor);
        surface.refresh(); pump();
        check(text.getLineSpacing() == 3 + height * 2, "200 percent additional spacing remains active with rules hidden");
        current[0] = new NotebookSettings(true,true,false,0,2,4,-50,0,100,38,22,
            0,40,d.lineColor,d.paperColor,d.patternColor);
        surface.refresh(); pump();
        check(text.getLineSpacing() == 3, "zero additional spacing preserves original editor spacing");
    }
    private void tabAlignmentTests(StyledText text,NotebookSettings settings) throws Exception {
        text.setWordWrap(false); text.setText("\t\t" + "source ".repeat(100) + "\n");
        for (int tabSize : new int[] {4,8}) {
            text.setTabs(tabSize); pump();
            PaperRenderer.Layout layout = NotebookSurface.layout(text,text.getLineHeight(),settings);
            check(layout.firstTab == text.getLocationAtOffset(1).x && layout.secondTab == text.getLocationAtOffset(2).x,
                "notebook and Cornell margins match actual tab stops for tab size " + tabSize);
        }
        Font old = text.getFont(); FontData[] fd = old.getFontData();
        for (FontData item : fd) item.setHeight(item.getHeight()*2);
        Font large = new Font(display,fd); text.setFont(large); pump();
        PaperRenderer.Layout layout = NotebookSurface.layout(text,text.getLineHeight(),settings);
        check(layout.firstTab == text.getLocationAtOffset(1).x && layout.secondTab == text.getLocationAtOffset(2).x,
            "tab-aligned margins follow font zoom");
        text.setFont(old); large.dispose(); text.setTabStops(new int[] {37,91}); text.setHorizontalPixel(20); pump();
        layout = NotebookSurface.layout(text,text.getLineHeight(),settings);
        check(layout.firstTab == text.getLocationAtOffset(1).x && layout.secondTab == text.getLocationAtOffset(2).x,
            "explicit tab stops and horizontal scrolling keep both margins aligned");
        capture(text,"tab-alignment.png");
    }
    private void currentLineAlignmentTests(StyledText text) throws Exception {
        text.setCaretOffset(text.getOffsetAtLine(1)); text.redraw(); pump();
        NotebookSurface surface = (NotebookSurface) text.getData(NotebookSurface.DATA_KEY);
        PaperRenderer.Layout layout = field(surface,"lastLayout",PaperRenderer.Layout.class);
        check(layout.highlight != null, "native current-line highlight color is retained in aligned paper layer");
        NotebookSettings settings = NotebookSettings.read(store);
        for (int zoom : new int[] {100,125,150,200}) {
            ImageData background = PaperRenderer.renderBackground(settings,layout,text.getBackground().getRGB(),zoom);
            ImageData withoutHighlight = PaperRenderer.renderBackground(settings,
                layout.withGeometry(layout.firstTab,layout.secondTab,null),text.getBackground().getRGB(),zoom);
            ImageData rules = PaperRenderer.renderRules(settings,layout,text.getBackground().getRGB(),zoom);
            int x = background.width-20, blueEnd = -1, ruleEnd = -1;
            int from = Math.max(0,(int)(layout.highlight.bottom*zoom/100.0)-5);
            int to = Math.min(background.height,from+12);
            for (int y=from;y<to;y++) {
                if (background.getPixel(x,y) != withoutHighlight.getPixel(x,y)) blueEnd=y;
                if (rules.getAlpha(x,y)==255) ruleEnd=y;
            }
            check(blueEnd == ruleEnd && blueEnd >= 0, "highlight bottom and notebook rule share the exact pixel edge at " + zoom + "%");
        }
        capture(text,"aligned-current-line.png");
        text.setSelection(0,text.getOffsetAtLine(2)); text.redraw(); pump();
        layout = field(surface,"lastLayout",PaperRenderer.Layout.class);
        check(layout.highlight == null, "multiline native selection does not retain a custom current-line highlight");
        text.setSelection(0); text.redraw(); pump();
    }
    private void themeTests(IWorkbenchPage page)throws Exception {
        org.eclipse.e4.ui.css.swt.theme.IThemeEngine engine=PlatformUI.getWorkbench().getService(org.eclipse.e4.ui.css.swt.theme.IThemeEngine.class);
        check(engine!=null,"CSS theme engine service available");
        org.eclipse.e4.ui.css.swt.theme.ITheme original=engine.getActiveTheme(),light=null,dark=null;
        for(org.eclipse.e4.ui.css.swt.theme.ITheme theme:engine.getThemes()) {
            if(theme.getId().equals("org.eclipse.e4.ui.css.theme.e4_default"))light=theme;
            if(theme.getId().equals("org.eclipse.e4.ui.css.theme.e4_dark"))dark=theme;
        }
        check(light!=null&&dark!=null,"built-in light and dark themes available");
        NotebookSettings before=NotebookSettings.read(store);
        RGB lightColor=new RGB(248,242,226),darkColor=new RGB(35,39,46);
        NotebookSettings settings=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,12,65,
            before.lineColor,lightColor,before.patternColor,50,darkColor);
        IEditorPart editor=null;
        try {
            engine.setTheme(light,false);settings.write(store);pump();
            NotebookSettings saved=NotebookSettings.read(store);
            results.add("INFO: stored paper="+saved.paperColor+", theme="+saved.theme+", decorator paper="+field(Activator.getDefault().decorator(),"settings",NotebookSettings.class).paperColor);
            IProject project=ResourcesPlugin.getWorkspace().getRoot().getProject("NotebookThemeChecks");project.create(null);project.open(null);
            IFile source=file(project,"theme.txt","안녕하세요.\n반갑습니다.\n라이트와 다크 종이\n\t같은 질감과 선을 사용합니다.\n\n");
            editor=page.openEditor(new FileEditorInput(source),EditorsUI.DEFAULT_TEXT_EDITOR_ID);pump();
            StyledText text=widget(editor);await(()->attached(text));
            check(attached(text),"theme test editor decorated; area="+text.getClientArea()+"; visible="+text.isVisible());
            String content=document(editor).get();int spacing=text.getLineSpacing();
            for(int pass=0;pass<4;pass++) {
                boolean useDark=pass%2==1;engine.setTheme(useDark?dark:light,false);pump();
                RGB expected=useDark?darkColor:lightColor;
                await(()->text.getBackground().getRGB().equals(expected));
                if(!text.getBackground().getRGB().equals(expected)) {
                    NotebookSurface diagnostic=(NotebookSurface)text.getData(NotebookSurface.DATA_KEY);
                    NotebookSettings rendered=field(diagnostic,"lastSettings",NotebookSettings.class);
                    results.add("INFO: background="+text.getBackground().getRGB()+", expected="+expected+", parent="+text.getParent().getBackground().getRGB()
                        +", rendered paper="+(rendered==null?null:rendered.paperColor)+", rendered theme="+(rendered==null?null:rendered.theme));
                    captureTree(((org.eclipse.jface.viewers.Viewer)editor.getAdapter(ITextOperationTarget.class)).getControl(),"background-diagnostic.png");
                }
                check(NotebookTheme.isDark(text)==useDark,"theme profile follows actual CSS theme on pass "+pass);
                check(text.getBackground().getRGB().equals(expected),"existing editor uses saved "+(useDark?"dark":"light")+" paper on pass "+pass);
                check(text.getLineSpacing()==spacing,"theme changes preserve line spacing on pass "+pass);
                NotebookSurface surface=(NotebookSurface)text.getData(NotebookSurface.DATA_KEY);
                check(field(surface,"rulesImage",Image.class)!=null,"rules remain ready on theme pass "+pass);
                RGB foreground=text.getForeground().getRGB();
                check(useDark?foreground.red+foreground.green+foreground.blue>384:foreground.red+foreground.green+foreground.blue<384,
                    "native editor text contrasts with "+(useDark?"dark":"light")+" paper; foreground="+foreground);
                Object viewer=editor.getAdapter(ITextOperationTarget.class);
                frameSeamTests((ITextViewer)viewer,text,surface,expected,pass);
                if(pass<2)restoreTests(page,editor,text,(ITextViewer)viewer,useDark);
                if(pass<2)captureTree(((org.eclipse.jface.viewers.Viewer)viewer).getControl(),useDark?"dark-paper-editor.png":"light-paper-editor.png");
                org.eclipse.jface.text.source.IVerticalRulerInfo info=editor.getAdapter(org.eclipse.jface.text.source.IVerticalRulerInfo.class);
                java.util.Iterator<org.eclipse.jface.text.source.IVerticalRulerColumn> columns=((org.eclipse.jface.text.source.CompositeRuler)info).getDecoratorIterator();
                boolean checked=false,annotationPaper=false;
                while(columns.hasNext()) {
                    org.eclipse.jface.text.source.IVerticalRulerColumn nativeColumn=columns.next();
                    Control ruler=nativeColumn.getControl();Control overlay=(Control)ruler.getData("dev.eclipseline.rulerOverlay");
                    if(overlay==null)continue;
                    PaperRenderer.Layout layout=field(surface,"lastLayout",PaperRenderer.Layout.class);Point offset=display.map(text,ruler,0,0),size=ruler.getSize();
                    ImageData paper=PaperRenderer.render(NotebookSettings.read(store),layout.forRuler(size.x,size.y,offset.x,offset.y),expected,100),actual=captureData(overlay);
                    check(actual.palette.getRGB(actual.getPixel(3,8)).equals(paper.palette.getRGB(paper.getPixel(3,8))),"line number paper follows the same theme profile on pass "+pass);checked=true;
                    if(nativeColumn instanceof org.eclipse.ui.texteditor.rulers.IContributedRulerColumn
                        &&"org.eclipse.ui.editors.columns.annotations".equals(((org.eclipse.ui.texteditor.rulers.IContributedRulerColumn)nativeColumn).getDescriptor().getId())) {
                        boolean same=actual.width==paper.width&&actual.height==paper.height;
                        for(int y=0;y<actual.height&&same;y++)for(int x=0;x<actual.width;x++)if(!actual.palette.getRGB(actual.getPixel(x,y)).equals(paper.palette.getRGB(paper.getPixel(x,y)))){same=false;break;}
                        check(same,"entire empty annotation gutter is continuous paper on theme pass "+pass);annotationPaper=true;
                    }
                }
                check(checked,"themed line number overlay found");
                check(annotationPaper,"annotation gutter paper overlay found");
            }
            markerPresentationTests(editor,source,text);
            PreferenceDialog dialog=PreferencesUtil.createPreferenceDialogOn(page.getWorkbenchWindow().getShell(),NotebookPreferencePage.ID,new String[]{NotebookPreferencePage.ID},null);
            dialog.setBlockOnOpen(false);dialog.open();pump();NotebookPreferencePage preference=(NotebookPreferencePage)dialog.getSelectedPage();
            try {
                StyledText preview=field(preference,"preview",StyledText.class);
                await(()->preview.getBackground().getRGB().equals(darkColor));
                check(preview.getBackground().getRGB().equals(darkColor),"preference preview uses actual dark theme paper; actual="+preview.getBackground().getRGB()
                    +"; draft="+field(preference,"draft",NotebookSettings.class).darkPaperColor+"; dark="+NotebookTheme.isDark(preview));
                org.eclipse.jface.preference.ColorSelector lightSelector=field(preference,"paperColor",org.eclipse.jface.preference.ColorSelector.class);
                org.eclipse.jface.preference.ColorSelector darkSelector=field(preference,"darkPaperColor",org.eclipse.jface.preference.ColorSelector.class);
                check(lightSelector.getColorValue().equals(lightColor)&&darkSelector.getColorValue().equals(darkColor),"light and dark paper selectors retain separate saved colors");
                captureTree(dialog.getShell(),"dark-paper-preferences.png");
                RGB custom=new RGB(29,34,43);darkSelector.setColorValue(custom);check(preference.performOk(),"dark paper color applies successfully");pump();
                check(NotebookSettings.read(store).darkPaperColor.equals(custom)&&NotebookSettings.read(store).paperColor.equals(lightColor),"saving dark paper preserves the light paper color");
                check(text.getBackground().getRGB().equals(custom),"edited dark color applies to existing editor");
            } finally {dialog.close();pump();}
            engine.setTheme(light,false);pump();check(text.getBackground().getRGB().equals(lightColor),"returning to light theme restores its original paper color");
            check(document(editor).get().equals(content)&&!editor.isDirty(),"theme changes preserve source and dirty state");
        } finally {
            if(editor!=null)page.closeEditor(editor,false);
            before.write(store);if(original!=null)engine.setTheme(original,false);pump();
        }
    }
    private void markerPresentationTests(IEditorPart editor,IFile file,StyledText text)throws Exception {
        org.eclipse.jface.text.source.CompositeRuler ruler=(org.eclipse.jface.text.source.CompositeRuler)editor.getAdapter(org.eclipse.jface.text.source.IVerticalRulerInfo.class);
        java.util.Iterator<org.eclipse.jface.text.source.IVerticalRulerColumn> columns=ruler.getDecoratorIterator();Control annotation=null,nativeControl=null;
        while(columns.hasNext()) {
            org.eclipse.jface.text.source.IVerticalRulerColumn column=columns.next();
            if(column instanceof org.eclipse.ui.texteditor.rulers.IContributedRulerColumn&&"org.eclipse.ui.editors.columns.annotations".equals(((org.eclipse.ui.texteditor.rulers.IContributedRulerColumn)column).getDescriptor().getId())) {
                nativeControl=column.getControl();annotation=(Control)nativeControl.getData("dev.eclipseline.rulerOverlay");break;
            }
        }
        check(annotation!=null,"native marker column has a paper overlay");
        NotebookSurface surface=(NotebookSurface)text.getData(NotebookSurface.DATA_KEY);final Control overlay=annotation,original=nativeControl;
        java.util.function.IntSupplier residual=()->{
            try {
                ImageData actual=captureData(overlay);PaperRenderer.Layout source=field(surface,"lastLayout",PaperRenderer.Layout.class);
                Point offset=display.map(text,original,0,0),size=original.getSize();
                ImageData paper=PaperRenderer.render(NotebookSettings.read(store),source.forRuler(size.x,size.y,offset.x,offset.y),text.getBackground().getRGB(),100);
                int ink=0;for(int y=0;y<actual.height;y++)for(int x=0;x<actual.width;x++) {
                    RGB a=actual.palette.getRGB(actual.getPixel(x,y)),b=paper.palette.getRGB(paper.getPixel(x,y));
                    if(Math.abs(a.red-b.red)+Math.abs(a.green-b.green)+Math.abs(a.blue-b.blue)>80)ink++;
                }
                return ink;
            }catch(Exception error){throw new RuntimeException(error);}
        };
        IMarker marker=file.createMarker(IMarker.PROBLEM);
        try {
            marker.setAttributes(new String[]{IMarker.MESSAGE,IMarker.SEVERITY,IMarker.LINE_NUMBER,IMarker.CHAR_START,IMarker.CHAR_END},
                new Object[]{"Notebook marker preservation",IMarker.SEVERITY_ERROR,2,text.getOffsetAtLine(1),text.getOffsetAtLine(1)+1});
            await(()->residual.getAsInt()>5);check(residual.getAsInt()>5,"real workspace error marker remains visible above textured paper");
            captureTree(((org.eclipse.jface.viewers.Viewer)editor.getAdapter(ITextOperationTarget.class)).getControl(),"dark-paper-error-marker.png");
        } finally {marker.delete();}
        await(()->residual.getAsInt()==0);check(residual.getAsInt()==0,"removing a real marker restores exact paper with no background patch");
    }
    private void restoreTests(IWorkbenchPage page,IEditorPart editor,StyledText text,ITextViewer viewer,boolean dark)throws Exception {
        String mode=dark?"dark":"light",content=document(editor).get();
        Shell shell=page.getWorkbenchWindow().getShell();
        Composite host=(Composite)((org.eclipse.jface.viewers.Viewer)viewer).getControl();
        NotebookSurface surface=(NotebookSurface)text.getData(NotebookSurface.DATA_KEY);
        pump();Rectangle original=text.getBounds();
        Image body=text.getBackgroundImage(),frame=host.getBackgroundImage();
        for(int i=0;i<50;i++)surface.prepare();
        check(body==text.getBackgroundImage()&&frame==host.getBackgroundImage(),mode+": unchanged prepare retains image caches");
        for(int cycle=0;cycle<4;cycle++) {
            shell.setMinimized(true);pump();shell.setMinimized(false);pump();pump();
            check(text.getBounds().equals(original),mode+": workbench restore preserves editor geometry cycle "+cycle);
            check(text.getTopMargin()==0&&org.eclipse.jface.text.JFaceTextUtil.getPartialTopIndex(viewer)>=0,mode+": restore preserves native viewport index cycle "+cycle);
            rulerTests(editor,text);
        }
        check(content.equals(document(editor).get())&&!editor.isDirty(),mode+": restore preserves source and dirty state");
    }
    private void frameSeamTests(ITextViewer viewer,StyledText text,NotebookSurface surface,RGB color,int pass)throws Exception {
        Composite host=(Composite)((org.eclipse.jface.viewers.Viewer)viewer).getControl();
        Image backdrop=host.getBackgroundImage();check(backdrop!=null,"viewer has a continuous backdrop on theme pass "+pass);
        ImageData pixels=backdrop.getImageData(100);Rectangle bounds=host.getClientArea(),client=text.getClientArea();Point offset=display.map(text,host,0,0);
        check(pixels.width==bounds.width&&pixels.height==bounds.height,"backdrop covers the whole viewer rather than repeating a top strip on theme pass "+pass);
        int right=Math.min(bounds.width,offset.x+client.x+client.width),bottom=Math.min(bounds.height,offset.y+client.y+client.height);
        RGB chrome=display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND).getRGB();boolean uniform=true;
        for(int y=0;y<pixels.height&&uniform;y++)for(int x=0;x<pixels.width;x++)if((x>=right||y>=bottom)&&!pixels.palette.getRGB(pixels.getPixel(x,y)).equals(chrome)){uniform=false;break;}
        check(uniform,"all scrollbar-side corners and filler regions use native chrome on theme pass "+pass);
        PaperRenderer.Layout layout=field(surface,"lastLayout",PaperRenderer.Layout.class);
        ImageData paper=PaperRenderer.render(NotebookSettings.read(store),layout.forRuler(bounds.width,bounds.height,offset.x,offset.y),color,100);
        RGB first=pixels.palette.getRGB(pixels.getPixel(0,0)),expected=paper.palette.getRGB(paper.getPixel(0,0));
        check(first.equals(expected),"top/left paper uses the same coordinate origin as source on theme pass "+pass);
    }
    private void openingTests(IWorkbenchPage page)throws Exception {
        new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,13,65,
            new RGB(112,148,173),new RGB(249,247,237),new RGB(137,159,171)).write(store);
        pump();
        IProject project=ResourcesPlugin.getWorkspace().getRoot().getProject("NotebookFirstPaint");
        project.create(null);project.open(null);
        IProjectDescription description=project.getDescription();description.setNatureIds(new String[]{"org.eclipse.jdt.core.javanature"});project.setDescription(description,null);
        String[] names={"First.java","first.txt","first.xml","first.jsp"};
        String[] contents={"// notebook-first-paint-0\npublic class First {}\n","notebook-first-paint-1\n노트 배경 테스트\n",
            "<?xml version=\"1.0\"?><note>notebook-first-paint-2</note>\n","<%@ page contentType=\"text/html; charset=UTF-8\" %>notebook-first-paint-3\n"};
        int[] paints=new int[names.length];List<String> failures=new ArrayList<>();
        Listener observer=event->{
            if(!(event.widget instanceof StyledText))return;
            StyledText text=(StyledText)event.widget;String value=text.getText();
            for(int i=0;i<names.length;i++)if(value.contains("notebook-first-paint-"+i)&&paints[i]++==0) {
                if(!attached(text))failures.add(names[i]+": first native paint has no paper");
                else try {
                    Image rules=field(text.getData(NotebookSurface.DATA_KEY),"rulesImage",Image.class);
                    if(rules==null||rules.isDisposed())failures.add(names[i]+": first native paint has no rules");
                }catch(Exception error){failures.add(error.toString());}
            }
        };
        display.addFilter(SWT.Paint,observer);
        try {
            for(int i=0;i<names.length;i++) {
                IFile source=file(project,names[i],contents[i]);
                String id=i==0?"org.eclipse.jdt.ui.CompilationUnitEditor":i==1?EditorsUI.DEFAULT_TEXT_EDITOR_ID:
                    PlatformUI.getWorkbench().getEditorRegistry().getDefaultEditor(names[i]).getId();
                long started=System.nanoTime();
                IEditorPart editor=page.openEditor(new FileEditorInput(source),id);
                if(editor instanceof MultiPageEditorPart) {
                    MultiPageEditorPart multi=(MultiPageEditorPart)editor;
                    for(IEditorPart child:multi.findEditors(editor.getEditorInput()))if(child.getAdapter(Control.class) instanceof StyledText){multi.setActiveEditor(child);break;}
                }
                StyledText text=widget(editor);
                check(text.getData(NotebookSurface.DATA_KEY)!=null,names[i]+": decorator attached before openEditor returns, without a queued scan");
                final int index=i;await(()->paints[index]>0);
                check(paints[i]>0,names[i]+": observed native source paint");
                check(failures.isEmpty(),names[i]+": first native paint already has paper and rules; "+failures);
                Image rules=field(text.getData(NotebookSurface.DATA_KEY),"rulesImage",Image.class);
                check(attached(text)&&rules!=null&&!rules.isDisposed(),names[i]+": both paper and rules retained after first paint");
                nativeGutterTests(editor,text,names[i]);
                results.add("INFO: "+names[i]+" open and first-paint observation "+((System.nanoTime()-started)/1_000_000)+" ms (includes IDE startup work)");
                check(!editor.isDirty(),names[i]+": opening decoration preserves source state");
                page.closeEditor(editor,false);pump();
            }
            Shell other=new Shell(display);other.setLayout(new FillLayout());StyledText unrelated=new StyledText(other,SWT.NONE);unrelated.setText("ordinary view text");other.setSize(200,100);other.open();
            try {pump();check(unrelated.getData(NotebookSurface.DATA_KEY)==null,"first-paint hook does not decorate non-editor StyledText");}
            finally {other.dispose();}
        } finally {display.removeFilter(SWT.Paint,observer);}
    }
    private void nativeGutterTests(IEditorPart editor,StyledText text,String name)throws Exception {
        Object target=editor.getAdapter(ITextOperationTarget.class);
        if(!(target instanceof ITextViewer)&&editor instanceof MultiPageEditorPart) {
            Object source=((MultiPageEditorPart)editor).getSelectedPage();
            if(source instanceof IEditorPart)target=((IEditorPart)source).getAdapter(ITextOperationTarget.class);
        }
        check(target instanceof ITextViewer,name+": native source viewer available");
        ITextViewer viewer=(ITextViewer)target;
        text.setSelection(0);text.setTopPixel(0);pump();
        check(text.getTopMargin()==0&&org.eclipse.jface.text.JFaceTextUtil.getPartialTopIndex(viewer)>=0,name+": top padding preserves nonnegative native viewport indices");
        IEditorPart sourceEditor=editor;
        if(editor instanceof MultiPageEditorPart&&((MultiPageEditorPart)editor).getSelectedPage() instanceof IEditorPart)sourceEditor=(IEditorPart)((MultiPageEditorPart)editor).getSelectedPage();
        org.eclipse.jface.text.source.IVerticalRulerInfo info=sourceEditor.getAdapter(org.eclipse.jface.text.source.IVerticalRulerInfo.class);
        check(info instanceof org.eclipse.jface.text.source.CompositeRuler,name+": native ruler available");
        org.eclipse.jface.text.source.CompositeRuler ruler=(org.eclipse.jface.text.source.CompositeRuler)info;
        Map<Control,ImageData> stable=new IdentityHashMap<>();
        for(int pass=0;pass<3;pass++) {
            text.setSelection(pass%2==0?0:text.getCharCount(),pass%2==0?0:Math.min(text.getCharCount(),1));
            if(viewer instanceof org.eclipse.jface.viewers.Viewer) {
                Control frame=((org.eclipse.jface.viewers.Viewer)viewer).getControl();Point size=frame.getSize();
                frame.setSize(Math.max(50,size.x-40),Math.max(50,size.y-30));frame.setSize(size);
            }
            text.redraw();pump();
            java.util.Iterator<org.eclipse.jface.text.source.IVerticalRulerColumn> columns=ruler.getDecoratorIterator();
            while(columns.hasNext()) {
                Control column=columns.next().getControl();if(column.getSize().x<=0||column.getSize().y<=0)continue;
                ImageData pixels=captureData(column); // Native painting must complete without annotation/overview exceptions.
                if(column.getData("dev.eclipseline.rulerOverlay")!=null)continue;
                int dark=0;
                for(int y=0;y<pixels.height;y++)for(int x=0;x<pixels.width;x++) {
                    RGB rgb=pixels.palette.getRGB(pixels.getPixel(x,y));if(rgb.red<24&&rgb.green<24&&rgb.blue<24)dark++;
                }
                check(dark<pixels.width*pixels.height/4,name+": native gutter has no black or unpainted buffer on repaint "+pass);
                if(name.endsWith(".txt")) {
                    ImageData before=stable.putIfAbsent(column,pixels);
                    if(before!=null) {
                        boolean same=before.width==pixels.width&&before.height==pixels.height;
                        if(same)for(int y=0;y<pixels.height&&same;y++)for(int x=0;x<pixels.width;x++)if(before.getPixel(x,y)!=pixels.getPixel(x,y)){same=false;break;}
                        check(same,name+": native gutter pixels stable after selection and resize "+pass);
                    }
                }
            }
        }
    }
    private void rulerTests(IEditorPart editor,StyledText text)throws Exception {
        org.eclipse.jface.text.source.IVerticalRulerInfo info=editor.getAdapter(org.eclipse.jface.text.source.IVerticalRulerInfo.class);
        check(info instanceof org.eclipse.jface.text.source.CompositeRuler,"source exposes native composite ruler");
        java.util.Iterator<org.eclipse.jface.text.source.IVerticalRulerColumn> columns=((org.eclipse.jface.text.source.CompositeRuler)info).getDecoratorIterator();
        Control ruler=null;
        while(columns.hasNext()) {
            org.eclipse.jface.text.source.IVerticalRulerColumn column=columns.next();
            if(column instanceof org.eclipse.jface.text.source.LineNumberRulerColumn
                || column instanceof org.eclipse.ui.texteditor.rulers.IContributedRulerColumn
                && "org.eclipse.ui.editors.columns.linenumbers".equals(((org.eclipse.ui.texteditor.rulers.IContributedRulerColumn)column).getDescriptor().getId())){ruler=column.getControl();break;}
        }
        check(ruler!=null&&ruler.getData("dev.eclipseline.rulerPaper")!=null,"native line number column has paper decorator; ruler="+ruler);
        ruler.redraw();pump();
        NotebookSurface surface=(NotebookSurface)text.getData(NotebookSurface.DATA_KEY);
        PaperRenderer.Layout source=field(surface,"lastLayout",PaperRenderer.Layout.class);
        Point shift=display.map(text,ruler,0,0),size=ruler.getSize();
        ImageData expected=PaperRenderer.render(NotebookSettings.read(store),source.forRuler(size.x,size.y,shift.x,shift.y),text.getBackground().getRGB(),100);
        Control overlay=(Control)ruler.getData("dev.eclipseline.rulerOverlay");
        check(overlay!=null&&overlay.isVisible(),"line number overlay clips native direct redraws");
        ImageData actual=captureData(overlay);
        check(actual.palette.getRGB(actual.getPixel(3,8)).equals(expected.palette.getRGB(expected.getPixel(3,8))),
            "line number gutter matches source paper color and highlight");
        for(int line=0;line<3;line++) {
            int ink=0,top=text.getLinePixel(line);
            for(int y=Math.max(0,top);y<Math.min(actual.height,top+text.getLineHeight());y++)for(int x=0;x<actual.width;x++) {
                RGB n=actual.palette.getRGB(actual.getPixel(x,y)),b=expected.palette.getRGB(expected.getPixel(x,y));
                if(Math.abs(n.red-b.red)+Math.abs(n.green-b.green)+Math.abs(n.blue-b.blue)>80)ink++;
            }
            check(ink>=3,"native line number "+(line+1)+" remains visible over paper");
        }
        capture(ruler.getParent(),"editor-with-paper-ruler.png");
    }
    private IFile file(IProject p,String name,String content) throws Exception {
        IFile file = p.getFile(name); file.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),true,null);
        file.setCharset("UTF-8",null); return file;
    }
    private String longText() {
        StringBuilder text = new StringBuilder();
        for (int i=0;i<250;i++) text.append(String.format("%03d  공책 노트 · font-aware vector rules · ",i)).append("source ".repeat(28)).append('\n');
        return text.toString();
    }
    private StyledText widget(IEditorPart editor) {
        if (editor instanceof MultiPageEditorPart) {
            Object selected = ((MultiPageEditorPart) editor).getSelectedPage();
            if (selected instanceof IEditorPart) return widget((IEditorPart) selected);
        }
        Control control = editor.getAdapter(Control.class);
        if (control instanceof StyledText) return (StyledText) control;
        ITextOperationTarget target = editor.getAdapter(ITextOperationTarget.class);
        if (target instanceof ITextViewer) return ((ITextViewer) target).getTextWidget();
        throw new AssertionError("No source widget: " + editor.getClass());
    }
    private IDocument document(IEditorPart editor) {
        ITextEditor textEditor = editor.getAdapter(ITextEditor.class);
        if (textEditor == null && editor instanceof ITextEditor) textEditor = (ITextEditor) editor;
        return textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
    }
    private boolean attached(StyledText text) { return text != null && text.getData(NotebookSurface.DATA_KEY) != null && text.getBackgroundImage() != null; }
    private <T> T field(Object target,String name,Class<T> type) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(target));
    }
    private void capture(Control control,String name) throws Exception {
        pump(); save(captureData(control),name);
    }
    private ImageData captureData(Control control) {
        Rectangle bounds = control.getBounds();
        Image image = new Image(display,Math.max(1,bounds.width),Math.max(1,bounds.height));
        GC gc = new GC(image);
        try { control.print(gc); return image.getImageData(100); }
        finally { gc.dispose(); image.dispose(); }
    }
    private void captureTree(Control root,String name) throws Exception {
        Rectangle bounds = root.getBounds();
        Image image = new Image(display,Math.max(1,bounds.width),Math.max(1,bounds.height));
        GC gc = new GC(image);
        try { printTree(root,root,gc); save(image.getImageData(),name); }
        finally { gc.dispose(); image.dispose(); }
    }
    private void printTree(Control root,Control control,GC outputGc) {
        if (!control.isVisible()) return;
        Point origin = display.map(control,root,0,0); Rectangle bounds = control.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) return;
        Image layer = new Image(display,bounds.width,bounds.height); GC gc = new GC(layer);
        try { control.print(gc); outputGc.drawImage(layer,origin.x,origin.y); }
        finally { gc.dispose(); layer.dispose(); }
        if (control instanceof Composite) for (Control child : ((Composite)control).getChildren()) printTree(root,child,outputGc);
    }
    private void save(ImageData data,String name) {
        ImageLoader loader = new ImageLoader(); loader.data = new ImageData[] {data}; loader.save(output.resolve(name).toString(),SWT.IMAGE_PNG);
    }
    private void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message); results.add("PASS: " + message);
        try { Files.write(output.resolve("progress.txt"),results,StandardCharsets.UTF_8); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private void pump() {
        long until = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < until) {
            if (!display.readAndDispatch()) { try { Thread.sleep(8); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; } }
        }
    }
    private void await(java.util.function.BooleanSupplier condition) {
        long until = System.currentTimeMillis() + 8000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < until) pump();
    }
}

package dev.eclipseline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.LineBackgroundListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Listener;

/** Owns only the background and added spacing; text, selection and syntax paint normally above it. */
public final class NotebookSurface implements AutoCloseable {
    public static final String DATA_KEY = "dev.eclipseline.surface";
    private final StyledText text;
    private final Supplier<NotebookSettings> settings;
    private final java.util.Map<org.eclipse.swt.widgets.Control,RulerPaper> rulers=new java.util.IdentityHashMap<>();
    private final boolean alignHighlight;
    private RGB highlightColor;
    private boolean highlightPending;
    private final LineBackgroundListener lineBackground;
    private Color originalBackground, ownedBackground;
    private final Image originalImage;
    private Image image, rulesImage;
    private int originalSpacing, appliedSpacing = -1;
    private int originalTopMargin, appliedTopMargin = -1;
    private NotebookPadding padding;
    private PaperRenderer.Layout lastLayout;
    private NotebookSettings lastSettings;
    private RGB lastBackground;
    private int lastZoom;
    private boolean closed, preparing, disposing;
    private final Listener paintFilter;
    private final Listener rulesPainter;
    private final Listener disposeListener = event -> { disposing = true; close(); };

    public NotebookSurface(StyledText text, Supplier<NotebookSettings> settings) {
        this(text,settings,false);
    }
    public NotebookSurface(StyledText text, Supplier<NotebookSettings> settings, boolean alignHighlight) {
        this.text = text; this.settings = settings;
        this.alignHighlight = alignHighlight;
        lineBackground = event -> {
            NotebookSettings s = settings.get();
            if (!s.enabled || !singleSelectedLine()) return;
            if (text.getLineAtOffset(event.lineOffset) != text.getLineAtOffset(text.getCaretOffset())) return;
            RGB color = event.lineBackground == null ? null : event.lineBackground.getRGB();
            if (!java.util.Objects.equals(color,highlightColor)) {
                highlightColor = color;
                if (!highlightPending) {
                    highlightPending = true;
                    text.getDisplay().asyncExec(() -> { highlightPending = false; refresh(); });
                }
            }
            // Let the normal renderer paint text and selection above our precisely bounded highlight.
            if (color != null) event.lineBackground = null;
        };
        paintFilter = event -> { if (event.widget == text) prepare(); };
        rulesPainter = event -> {
            if (rulesImage != null && !rulesImage.isDisposed()) event.gc.drawImage(rulesImage, 0, 0);
        };
        originalBackground = text.getBackground(); originalImage = text.getBackgroundImage();
        originalSpacing = text.getLineSpacing();
        originalTopMargin = text.getTopMargin();
        text.setData(DATA_KEY, this);
        text.getDisplay().addFilter(SWT.Paint, paintFilter);
        text.addListener(SWT.Dispose, disposeListener);
        text.addListener(SWT.Paint, rulesPainter);
        if (alignHighlight) text.addLineBackgroundListener(lineBackground);
        prepare();
    }

    public void refresh() {
        lastLayout = null;
        if (!closed && !text.isDisposed()) { prepare(); text.redraw(); }
    }
    public void restored() {
        if(closed||text.isDisposed())return;
        if(padding!=null)padding.restored();refresh();
        for(RulerPaper ruler:rulers.values())ruler.refresh();
    }

    public void prepare() {
        if (closed || preparing || text.isDisposed()) return;
        preparing = true;
        try {
            rulers.entrySet().removeIf(entry->entry.getKey().isDisposed());
            NotebookSettings s = settings.get();
            if (alignHighlight) {
                // CursorLinePainter may reactivate after selection changes. Observe it last.
                text.removeLineBackgroundListener(lineBackground);
                text.addLineBackgroundListener(lineBackground);
            }
            if (text.getLineSpacing() != appliedSpacing && appliedSpacing >= 0)
                originalSpacing = text.getLineSpacing();
            if (appliedTopMargin >= 0 && text.getTopMargin() != appliedTopMargin)
                originalTopMargin = text.getTopMargin();
            if (ownedBackground != null && !ownedBackground.equals(text.getBackground()))
                originalBackground = text.getBackground();
            if (!s.enabled) { restore(); lastSettings = s; return; }
            int fontHeight = Math.max(1, text.getLineHeight());
            int inset=originalTopMargin+Math.max(1,(int)Math.round(fontHeight*.5));
            appliedTopMargin = padding!=null||alignHighlight?0:inset;
            if (text.getTopMargin() != appliedTopMargin) text.setTopMargin(appliedTopMargin);
            if(padding!=null)padding.setHeight(inset);
            int added = (int) Math.ceil(fontHeight * s.spacing / 100.0);
            appliedSpacing = originalSpacing + added;
            if (text.getLineSpacing() != appliedSpacing) text.setLineSpacing(appliedSpacing);
            updateBackground(s);
            Rectangle area = text.getClientArea();
            if (area.width <= 0 || area.height <= 0) return;
            PaperRenderer.Layout layout = layout(text, fontHeight, s);
            if (alignHighlight && highlightColor != null && singleSelectedLine()) {
                int line = text.getLineAtOffset(text.getCaretOffset());
                int offset = text.getOffsetAtLine(line), glyphHeight = text.getLineHeight(offset);
                double shift = fontHeight * s.gap / 100.0 + Math.max(1,fontHeight*s.width/100.0)/2;
                double first = text.getLocationAtOffset(offset).y + glyphHeight + shift;
                double last = text.getLinePixel(line + 1) - text.getLineSpacing() + shift;
                layout = layout.withGeometry(layout.firstTab,layout.secondTab,
                    s.lines?new PaperRenderer.Highlight(first-glyphHeight-text.getLineSpacing(),last,highlightColor)
                        :new PaperRenderer.Highlight(text.getLinePixel(line),text.getLinePixel(line+1),highlightColor));
            }
            RGB background = text.getBackground().getRGB();
            if(padding!=null)padding.paper(s,layout,background);
            int zoom = text.getMonitor().getZoom();
            if (layout.equals(lastLayout) && s == lastSettings && background.equals(lastBackground) && zoom == lastZoom) return;
            NotebookSettings captured = s;
            PaperRenderer.Layout capturedLayout = layout;
            Image next = new Image(text.getDisplay(), (ImageDataProvider) requestedZoom ->
                PaperRenderer.renderBackground(captured, capturedLayout, background, requestedZoom));
            Image nextRules = null;
            try {
                if (s.lines) nextRules = new Image(text.getDisplay(), (ImageDataProvider) requestedZoom ->
                    PaperRenderer.renderRules(captured, capturedLayout, background, requestedZoom));
            } catch (RuntimeException error) { next.dispose(); throw error; }
            Image previous = image, previousRules = rulesImage; image = next; rulesImage = nextRules;
            lastLayout = layout; lastSettings = s; lastBackground = background; lastZoom = zoom;
            text.setBackgroundImage(next);
            if (previous != null) previous.dispose();
            if (previousRules != null) previousRules.dispose();
            for(RulerPaper ruler:rulers.values())ruler.refresh();
        } catch (RuntimeException error) {
            // A third-party editor must remain usable if its widget cannot accept decoration.
            restore();
            Activator.log("노트 배경을 그리지 못했습니다.", error);
        } finally { preparing = false; }
    }

    private void updateBackground(NotebookSettings s) {
        if (s.theme) {
            if (ownedBackground != null) {
                if (ownedBackground.equals(text.getBackground()))
                    text.setBackground(validOriginal());
                ownedBackground.dispose(); ownedBackground = null;
            }
            originalBackground = text.getBackground();
        } else if (ownedBackground == null || !ownedBackground.getRGB().equals(s.paperColor(NotebookTheme.isDark(text)))) {
            Color previous = ownedBackground;
            if (previous == null) originalBackground = text.getBackground();
            ownedBackground = new Color(text.getDisplay(), s.paperColor(NotebookTheme.isDark(text)));
            text.setBackground(ownedBackground);
            if (previous != null) previous.dispose();
        } else if (!ownedBackground.equals(text.getBackground())) text.setBackground(ownedBackground);
    }

    private Color validOriginal() {
        return originalBackground != null && !originalBackground.isDisposed() ? originalBackground : null;
    }
    private boolean singleSelectedLine() {
        org.eclipse.swt.graphics.Point selection = text.getSelection();
        return text.getLineAtOffset(selection.x) == text.getLineAtOffset(selection.y);
    }
    void attachRuler(org.eclipse.swt.widgets.Control control) {
        attachRuler(control,null);
    }
    void attachRuler(org.eclipse.swt.widgets.Control control,org.eclipse.jface.text.ITextViewer viewer) {
        attachViewer(viewer);
        if(control!=null&&!control.isDisposed()&&!rulers.containsKey(control))rulers.put(control,new RulerPaper(control,this,viewer));
        if(control!=null&&rulers.containsKey(control))rulers.get(control).numbers();
    }
    void attachRuler(org.eclipse.jface.text.source.IVerticalRulerColumn column,org.eclipse.jface.text.ITextViewer viewer) {
        attachRuler(column.getControl(),viewer);attachAnnotations(column,viewer);
    }
    void attachAnnotations(org.eclipse.jface.text.source.IVerticalRulerColumn column,org.eclipse.jface.text.ITextViewer viewer) {
        org.eclipse.swt.widgets.Control control=column.getControl();
        if(viewer==null||control==null||control.isDisposed())return;
        attachViewer(viewer);
        java.util.function.Supplier<org.eclipse.jface.text.source.IAnnotationModel> model=()->{
            if(column instanceof org.eclipse.jface.text.source.IVerticalRulerInfoExtension)return ((org.eclipse.jface.text.source.IVerticalRulerInfoExtension)column).getModel();
            if(viewer instanceof org.eclipse.jface.text.source.ISourceViewerExtension2)return ((org.eclipse.jface.text.source.ISourceViewerExtension2)viewer).getVisualAnnotationModel();
            return viewer instanceof org.eclipse.jface.text.source.ISourceViewer?((org.eclipse.jface.text.source.ISourceViewer)viewer).getAnnotationModel():null;
        };
        if(!rulers.containsKey(control))rulers.put(control,new RulerPaper(control,this,viewer));
        rulers.get(control).watch(column,model);
    }
    void attachViewer(org.eclipse.jface.text.ITextViewer viewer) {
        if(padding!=null||!(viewer instanceof org.eclipse.jface.viewers.Viewer))return;
        org.eclipse.swt.widgets.Control host=((org.eclipse.jface.viewers.Viewer)viewer).getControl();
        if(host instanceof org.eclipse.swt.widgets.Composite&&text.getParent()==host&&((org.eclipse.swt.widgets.Composite)host).getLayout()!=null) {
            padding=new NotebookPadding((org.eclipse.swt.widgets.Composite)host,text);refresh();
        }
    }
    int topPadding(){return padding==null?text.getTopMargin():padding.height();}
    StyledText textWidget(){return text;}
    NotebookSettings currentSettings(){return settings.get();}
    RGB currentBackground(){return text.getBackground().getRGB();}
    PaperRenderer.Layout rulerLayout(org.eclipse.swt.widgets.Control control) {
        prepare();if(closed||lastLayout==null||!settings.get().enabled)return null;
        org.eclipse.swt.graphics.Point p=text.getDisplay().map(text,control,0,0);
        org.eclipse.swt.graphics.Point size=control.getSize();return lastLayout.forRuler(size.x,size.y,p.x,p.y);
    }

    public static PaperRenderer.Layout layout(StyledText text, int fontHeight, NotebookSettings s) {
        Rectangle area = text.getClientArea();
        int spacing = text.getLineSpacing(), rowHeight = fontHeight + spacing;
        List<Double> rules = new ArrayList<>();
        int start = text.getLineIndex(0), end = text.getLineIndex(area.height);
        double y = text.getLinePixel(start);
        for (int line = start; line <= end; line++) {
            int offset = text.getOffsetAtLine(line);
            int glyphHeight = text.getLineHeight(offset);
            int top = text.getLocationAtOffset(offset).y;
            int bottom = text.getLinePixel(line + 1);
            int step = Math.max(1, glyphHeight + spacing);
            for (int rowTop = top; rowTop < bottom; rowTop += step) {
                double rule = rowTop + glyphHeight + fontHeight * s.gap / 100.0
                    + Math.max(1, fontHeight * s.width / 100.0) / 2;
                if (rule >= -rowHeight && rule <= area.height + rowHeight) rules.add(rule);
            }
            y = bottom;
        }
        // Keep the empty paper below EOF ruled too.
        while (y < area.height + rowHeight) {
            rules.add(y + fontHeight + fontHeight * s.gap / 100.0
                + Math.max(1, fontHeight * s.width / 100.0) / 2);
            y += rowHeight;
        }
        double[] array = new double[rules.size()];
        for (int i = 0; i < array.length; i++) array[i] = rules.get(i);
        double paperOriginY = text.getLinePixel(0) + fontHeight + fontHeight * s.gap / 100.0
            + Math.max(1, fontHeight * s.width / 100.0) / 2;
        int[] tabs = text.getTabStops();
        double firstTab = text.getLeftMargin() + tabs[0] - text.getHorizontalPixel();
        double secondTab = text.getLeftMargin() + (tabs.length > 1 ? tabs[1] : tabs[0]*2.0) - text.getHorizontalPixel();
        return new PaperRenderer.Layout(area.width, area.height, fontHeight, rowHeight,
            text.getHorizontalPixel(), paperOriginY, array).withGeometry(firstTab,secondTab,null);
    }

    private void restore() {
        if(padding!=null)padding.clear();
        if (!disposing && !text.isDisposed()) {
            if (image != null && text.getBackgroundImage() == image)
                text.setBackgroundImage(originalImage != null && !originalImage.isDisposed() ? originalImage : null);
            if (appliedSpacing >= 0 && text.getLineSpacing() == appliedSpacing) text.setLineSpacing(originalSpacing);
            if (appliedTopMargin >= 0 && text.getTopMargin() == appliedTopMargin) text.setTopMargin(originalTopMargin);
            if (ownedBackground != null && ownedBackground.equals(text.getBackground())) text.setBackground(validOriginal());
        }
        if (image != null) { image.dispose(); image = null; }
        if (rulesImage != null) { rulesImage.dispose(); rulesImage = null; }
        if (ownedBackground != null) { ownedBackground.dispose(); ownedBackground = null; }
        appliedSpacing = -1; appliedTopMargin = -1; lastLayout = null;
        for(RulerPaper ruler:rulers.values())ruler.refresh();
    }

    @Override public void close() {
        if (closed) return; closed = true;
        for(RulerPaper ruler:rulers.values())ruler.close();rulers.clear();
        text.getDisplay().removeFilter(SWT.Paint, paintFilter);
        if (!disposing && !text.isDisposed()) {
            text.removeListener(SWT.Dispose, disposeListener);
            text.removeListener(SWT.Paint, rulesPainter);
            if (alignHighlight) text.removeLineBackgroundListener(lineBackground);
            text.setData(DATA_KEY, null);
        }
        restore();
        if(padding!=null){padding.close();padding=null;}
    }
}

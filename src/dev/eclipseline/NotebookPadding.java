package dev.eclipseline;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/** Keeps the first row inset outside StyledText, where JFace's viewport math remains valid. */
final class NotebookPadding implements AutoCloseable {
    private final Composite host;
    private final Display display;
    private final StyledText text;
    private final Layout originalLayout;
    private final Image originalImage;
    private Color originalBackground;
    private Image image;
    private Color background;
    private final java.util.Map<Control,Color> sideBackgrounds=new java.util.IdentityHashMap<>();
    private final Listener chromePaint=event->{
        if(background!=null&&sideBackgrounds.containsKey(event.widget))applyChrome((Control)event.widget,background);
    };
    private PaperRenderer.Layout lastLayout;
    private NotebookSettings lastSettings;
    private RGB lastColor;
    private RGB lastChrome;
    private Rectangle lastPage;
    private int lastZoom,height;
    private boolean layingOut,closed;
    private final java.util.Map<Control,Rectangle> layoutBounds=new java.util.IdentityHashMap<>();
    private java.util.List<Object> lastLayoutInputs;
    private final Layout insetLayout=new Layout() {
        @Override protected Point computeSize(Composite composite,int width,int wantedHeight,boolean flush) {
            composite.setLayout(originalLayout);
            try {
                Point size=composite.computeSize(width,wantedHeight==SWT.DEFAULT?SWT.DEFAULT:Math.max(0,wantedHeight-height),flush);
                size.y+=height;return size;
            } finally {if(!composite.isDisposed())composite.setLayout(this);}
        }
        @Override protected void layout(Composite composite,boolean flush) {
            if(layingOut)return;layingOut=true;composite.setLayout(originalLayout);
            try {
                layoutBounds.keySet().removeIf(Control::isDisposed);
                java.util.List<Object> inputs=layoutInputs(flush);
                boolean floating=false,unchanged=true;
                for(Control child:composite.getChildren()) {
                    Rectangle previous=layoutBounds.get(child);
                    if(previous==null)floating=true;
                    else if(!previous.equals(child.getBounds()))unchanged=false;
                }
                // Inline Find/Replace requests its parent's layout after each source resize.
                // Reapplying full-height bounds followed by the inset emits two more resizes
                // forever. Let a floating control lay out without resizing a settled viewer.
                if(floating&&unchanged&&inputs.equals(lastLayoutInputs))return;
                java.util.Map<Control,Rectangle> before=new java.util.IdentityHashMap<>();
                for(Control child:composite.getChildren())before.put(child,child.getBounds());
                composite.layout(flush);
                for(Control child:composite.getChildren()) {
                    Rectangle bounds=child.getBounds();
                    // SourceViewer's layout leaves overlays alone; preserve their geometry.
                    if(!layoutBounds.containsKey(child)&&bounds.equals(before.get(child)))continue;
                    if(height>0&&bounds.width>0&&bounds.height>0) {
                        int trim=child==text||bounds.height>=Math.max(1,composite.getClientArea().height/2)?height:0;
                        child.setBounds(bounds.x,bounds.y+height,bounds.width,Math.max(0,bounds.height-trim));
                    }
                    layoutBounds.put(child,child.getBounds());
                }
                lastLayoutInputs=layoutInputs(false);
            } finally {if(!composite.isDisposed())composite.setLayout(this);layingOut=false;}
        }
    };
    NotebookPadding(Composite host,StyledText text) {
        this.host=host;this.text=text;display=host.getDisplay();originalLayout=host.getLayout();
        originalImage=host.getBackgroundImage();originalBackground=host.getBackground();
        for(Control child:host.getChildren())layoutBounds.put(child,child.getBounds());
        host.setLayout(insetLayout);
        display.addFilter(SWT.Paint,chromePaint);
    }
    int height(){return height;}
    private java.util.List<Object> layoutInputs(boolean flush) {
        java.util.List<Object> inputs=new java.util.ArrayList<>();
        inputs.add(host.getClientArea());inputs.add(height);
        // The delegate's preferred size includes the ruler's requested width. Merely
        // comparing actual bounds would miss line-number width and font changes.
        inputs.add(host.computeSize(SWT.DEFAULT,SWT.DEFAULT,flush));
        inputs.add(text.computeTrim(0,0,0,0));
        inputs.add(text.getHorizontalBar()!=null&&text.getHorizontalBar().getVisible());
        inputs.add(text.getVerticalBar()!=null&&text.getVerticalBar().getVisible());
        for(Control child:host.getChildren())if(layoutBounds.containsKey(child)) {
            inputs.add(child);inputs.add(child.getVisible());inputs.add(child.getFont());
            inputs.add(child.computeSize(SWT.DEFAULT,SWT.DEFAULT,flush));
        }
        return inputs;
    }
    void restored(){if(!closed&&!host.isDisposed()){lastLayout=null;host.layout(true,true);host.redraw(0,0,host.getClientArea().width,host.getClientArea().height,true);}}
    void setHeight(int value) {
        if(closed||host.isDisposed())return;
        if(height!=value){height=value;host.layout(true);}
    }
    void paper(NotebookSettings settings,PaperRenderer.Layout source,RGB color) {
        if(closed||host.isDisposed()||host.getClientArea().width<=0||host.getClientArea().height<=0)return;
        Point offset=host.getDisplay().map(text,host,0,0);
        Rectangle client=text.getClientArea(),area=host.getClientArea();
        Rectangle page=new Rectangle(0,0,Math.min(area.width,offset.x+client.x+client.width),Math.min(area.height,offset.y+client.y+client.height));
        PaperRenderer.Layout layout=source.forRuler(area.width,area.height,offset.x,offset.y);
        PaperRenderer.Layout key=layout.fullKey(settings);
        // The editor's surrounding composite follows Eclipse CSS, including dark mode.
        // A system widget color can still be light when Eclipse itself uses a dark theme.
        RGB chrome=host.getParent().getBackground().getRGB();
        int zoom=host.getMonitor().getZoom();
        if(background!=null&&!background.equals(host.getBackground()))originalBackground=host.getBackground();
        Color previous=background;
        if(background==null||!background.getRGB().equals(chrome))background=new Color(host.getDisplay(),chrome);
        if(!background.equals(host.getBackground()))host.setBackground(background);
        // The overview canvas/header can also have a CSS background of their own.
        // Explicit colors stop image inheritance and are reapplied before native paint.
        sideBackgrounds.entrySet().removeIf(entry->{
            Control control=entry.getKey();
            if(control.isDisposed())return true;
            if(control.getBounds().x>=page.width)return false;
            restoreSide(control,entry.getValue(),previous);return true;
        });
        for(Control child:host.getChildren())if(child!=text&&child.getBounds().x>=page.width)applyChrome(child,previous);
        if(previous!=null&&previous!=background)previous.dispose();
        if(key.equals(lastLayout)&&settings==lastSettings&&color.equals(lastColor)&&zoom==lastZoom&&chrome.equals(lastChrome)&&page.equals(lastPage)
            &&host.getBackgroundImage()==image&&background!=null&&background.equals(host.getBackground()))return;
        Image next=new Image(host.getDisplay(),(ImageDataProvider) z->backdrop(settings,layout,color,chrome,page,z));
        // OverviewRuler fills its canvas with getBackground(), which may resolve to
        // this host. Keep that inherited color as chrome; the image supplies paper
        // only inside the page, and StyledText owns its separate paper background.
        boolean chromeChanged=!chrome.equals(lastChrome);
        host.setBackgroundImage(next);if(image!=null)image.dispose();image=next;
        if(chromeChanged)host.redraw(0,0,area.width,area.height,true);
        lastLayout=key;lastSettings=settings;lastColor=color;lastZoom=zoom;lastChrome=chrome;lastPage=page;
    }
    private void applyChrome(Control control,Color previous) {
        Color current=control.getBackground();
        if(!sideBackgrounds.containsKey(control))sideBackgrounds.put(control,previous!=null&&previous.equals(current)?null:current);
        else if(previous==null||!previous.equals(current))sideBackgrounds.put(control,current);
        if(!background.equals(current))control.setBackground(background);
    }
    private void restoreSide(Control control,Color original,Color applied) {
        if(!control.isDisposed()&&applied!=null&&applied.equals(control.getBackground()))
            control.setBackground(original!=null&&!original.isDisposed()?original:null);
    }
    static ImageData backdrop(NotebookSettings settings,PaperRenderer.Layout layout,RGB paper,RGB chrome,Rectangle page,int zoom) {
        ImageData data=PaperRenderer.render(settings,layout,paper,zoom);
        int right=Math.max(0,Math.min(data.width,(int)Math.round((page.x+page.width)*zoom/100.0)));
        int bottom=Math.max(0,Math.min(data.height,(int)Math.round((page.y+page.height)*zoom/100.0)));
        int[] row=new int[data.width];java.util.Arrays.fill(row,data.palette.getPixel(chrome));
        for(int y=0;y<data.height;y++) {
            int start=y<bottom?right:0;
            if(start<data.width)data.setPixels(start,y,data.width-start,row,0);
        }
        return data;
    }
    void clear() {
        sideBackgrounds.forEach((control,original)->restoreSide(control,original,background));sideBackgrounds.clear();
        setHeight(0);
        if(!host.isDisposed()) {
            if(image!=null&&host.getBackgroundImage()==image)host.setBackgroundImage(originalImage!=null&&!originalImage.isDisposed()?originalImage:null);
            if(background!=null&&background.equals(host.getBackground()))host.setBackground(originalBackground!=null&&!originalBackground.isDisposed()?originalBackground:null);
        }
        if(image!=null){image.dispose();image=null;}
        if(background!=null){background.dispose();background=null;}lastLayout=null;
    }
    @Override public void close() {
        if(closed)return;clear();closed=true;
        if(!display.isDisposed())display.removeFilter(SWT.Paint,chromePaint);
        if(!host.isDisposed()&&host.getLayout()==insetLayout){host.setLayout(originalLayout);host.layout(true);}
    }
}

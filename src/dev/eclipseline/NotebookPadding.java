package dev.eclipseline;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/** Keeps the first row inset outside StyledText, where JFace's viewport math remains valid. */
final class NotebookPadding implements AutoCloseable {
    private final Composite host;
    private final StyledText text;
    private final Layout originalLayout;
    private final Image originalImage;
    private Color originalBackground;
    private Image image;
    private Color background;
    private PaperRenderer.Layout lastLayout;
    private NotebookSettings lastSettings;
    private RGB lastColor;
    private RGB lastChrome;
    private Rectangle lastPage;
    private int lastZoom,height;
    private boolean layingOut,closed;
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
                composite.layout(flush);
                if(height>0)for(Control child:composite.getChildren()) {
                    Rectangle bounds=child.getBounds();
                    if(bounds.width<=0||bounds.height<=0)continue;
                    int trim=child==text||bounds.height>=Math.max(1,composite.getClientArea().height/2)?height:0;
                    child.setBounds(bounds.x,bounds.y+height,bounds.width,Math.max(0,bounds.height-trim));
                }
            } finally {if(!composite.isDisposed())composite.setLayout(this);layingOut=false;}
        }
    };
    NotebookPadding(Composite host,StyledText text) {
        this.host=host;this.text=text;originalLayout=host.getLayout();
        originalImage=host.getBackgroundImage();originalBackground=host.getBackground();
        host.setLayout(insetLayout);
    }
    int height(){return height;}
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
        RGB chrome=host.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND).getRGB();
        int zoom=host.getMonitor().getZoom();
        if(key.equals(lastLayout)&&settings==lastSettings&&color.equals(lastColor)&&zoom==lastZoom&&chrome.equals(lastChrome)&&page.equals(lastPage)
            &&host.getBackgroundImage()==image&&background!=null&&background.equals(host.getBackground()))return;
        Image next=new Image(host.getDisplay(),(ImageDataProvider) z->backdrop(settings,layout,color,chrome,page,z));
        if(background!=null&&!background.equals(host.getBackground()))originalBackground=host.getBackground();
        // SWT resolves child background colors through an inherited background image.
        // Keep that color as paper; the full backdrop paints chrome only outside the page.
        if(background==null||!background.getRGB().equals(color)) {
            Color previous=background;background=new Color(host.getDisplay(),color);host.setBackground(background);
            if(previous!=null)previous.dispose();
        } else if(!background.equals(host.getBackground()))host.setBackground(background);
        host.setBackgroundImage(next);if(image!=null)image.dispose();image=next;
        lastLayout=key;lastSettings=settings;lastColor=color;lastZoom=zoom;lastChrome=chrome;lastPage=page;
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
        if(!host.isDisposed()&&host.getLayout()==insetLayout){host.setLayout(originalLayout);host.layout(true);}
    }
}

package dev.eclipseline;

import java.util.*;
import java.util.function.Supplier;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelListener;
import org.eclipse.jface.text.source.IVerticalRulerColumn;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;

/** Retains native ruler painting and input; substitutes paper for its solid background only. */
final class RulerPaper implements AutoCloseable {
    private final Control control;
    private final NotebookSurface owner;
    private final Canvas overlay;
    private final Display display;
    private final Listener refresh;
    private final Listener disposed=e->close();
    private final Map<Object,Supplier<IAnnotationModel>> sources=new IdentityHashMap<>();
    private final Map<IVerticalRulerColumn,RulerModel> cleanModels=new IdentityHashMap<>();
    private final Set<IAnnotationModel> models=Collections.newSetFromMap(new IdentityHashMap<>());
    private final IAnnotationModelListener modelChanges=ignored->requestPaint();
    private final IPropertyChangeListener preferences=ignored->requestPaint();
    private org.eclipse.jface.preference.IPreferenceStore preferenceStore;
    private ImageData paper;
    private Image frame,nativeImage;
    private int nativeWidth,nativeHeight,nativeZoom;
    private PaperRenderer.Layout lastLayout;
    private NotebookSettings lastSettings;
    private RGB lastBackground;
    private int lastZoom;
    private boolean painting,capturing,smoothNumbers;
    private volatile boolean closed,pending;
    private static final int[] INPUT_EVENTS={SWT.MouseDown,SWT.MouseUp,SWT.MouseDoubleClick,SWT.MouseMove,
        SWT.MouseEnter,SWT.MouseExit,SWT.MouseHover,SWT.MouseWheel,SWT.MouseHorizontalWheel,SWT.DragDetect,SWT.MenuDetect};
    RulerPaper(Control control,NotebookSurface owner,ITextViewer viewer) {this(control,owner,viewer,null);}
    RulerPaper(Control control,NotebookSurface owner,ITextViewer viewer,Supplier<IAnnotationModel> model) {
        this.control=control;this.owner=owner;display=control.getDisplay();
        overlay=new Canvas((Composite)control,SWT.NO_BACKGROUND|SWT.DOUBLE_BUFFERED|SWT.NO_FOCUS);
        if(model!=null)watch(model,model);
        overlay.addListener(SWT.Paint,e->paint(e.gc));
        for(int type:INPUT_EVENTS)overlay.addListener(type,e->{
            Event forwarded=new Event();forwarded.x=e.x;forwarded.y=e.y;forwarded.button=e.button;
            forwarded.stateMask=e.stateMask;forwarded.count=e.count;forwarded.time=e.time;
            forwarded.detail=e.detail;forwarded.doit=e.doit;control.notifyListeners(e.type,forwarded);e.doit=forwarded.doit;
        });
        refresh=e->{if(!capturing)refresh();};control.addListener(SWT.Paint,refresh);control.addListener(SWT.Resize,refresh);
        control.addListener(SWT.Dispose,disposed);
        if(PlatformUI.isWorkbenchRunning()){preferenceStore=EditorsUI.getPreferenceStore();preferenceStore.addPropertyChangeListener(preferences);}
        control.setData("dev.eclipseline.rulerPaper",this);control.setData("dev.eclipseline.rulerOverlay",overlay);refresh();
    }
    void watch(Object key,Supplier<IAnnotationModel> source){if(!sources.containsKey(key))sources.put(key,source);}
    void numbers(){smoothNumbers=true;}
    private void syncModels(boolean enabled) {
        for(Map.Entry<Object,Supplier<IAnnotationModel>> entry:sources.entrySet()) {
            if(!(entry.getKey() instanceof IVerticalRulerColumn))continue;
            IVerticalRulerColumn column=(IVerticalRulerColumn)entry.getKey();
            IAnnotationModel current=entry.getValue().get();RulerModel installed=cleanModels.get(column);
            if(installed!=null) {
                if(current==installed) {
                    if(enabled)continue;
                    column.setModel(installed.original);
                    if(entry.getValue().get()==installed)continue;
                    current=installed.original;
                }
                cleanModels.remove(column);installed.close();
            }
            if(enabled&&current!=null&&!(current instanceof ProjectionAnnotationModel)&&!(current instanceof RulerModel)) {
                RulerModel next=new RulerModel(current);column.setModel(next);
                if(entry.getValue().get()==next)cleanModels.put(column,next);
                else next.close();
            }
        }
    }
    private void bindModels() {
        Set<IAnnotationModel> current=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Supplier<IAnnotationModel> source:sources.values()){IAnnotationModel model=source.get();if(model!=null)current.add(model);}
        for(IAnnotationModel model:new ArrayList<>(models))if(!current.contains(model)){model.removeAnnotationModelListener(modelChanges);models.remove(model);}
        for(IAnnotationModel model:current)if(models.add(model))model.addAnnotationModelListener(modelChanges);
    }
    private void requestPaint() {
        if(closed||pending||display.isDisposed())return;pending=true;
        display.asyncExec(()->{pending=false;if(!closed&&!overlay.isDisposed())overlay.redraw();});
    }
    void refresh() {
        if(closed||control.isDisposed()||overlay.isDisposed())return;
        Rectangle bounds=((Composite)control).getClientArea();
        if(!overlay.getBounds().equals(bounds))overlay.setBounds(bounds);
        boolean visible=owner.currentSettings().enabled;
        syncModels(visible);
        if(overlay.getVisible()!=visible)overlay.setVisible(visible);
        if(!visible&&frame!=null){frame.dispose();frame=null;}
        if(visible)overlay.redraw();
    }
    void scrolled() {
        if(closed||control.isDisposed())return;
        refresh();
        if(!overlay.isDisposed()&&overlay.isVisible())overlay.update();
    }
    private void paint(GC output) {
        if(closed||painting||capturing||!owner.currentSettings().enabled)return;painting=true;
        try {
            Rectangle bounds=((Composite)control).getClientArea();if(bounds.width<=0||bounds.height<=0)return;
            // If this column paints first, keep the entire previous scene (digits too).
            // The source paint will synchronously commit the next scene to every gutter.
            if(frame!=null&&!owner.isRulerFrameCurrent()){output.drawImage(frame,0,0);return;}
            PaperRenderer.Layout layout=owner.rulerLayout(control);if(layout==null)return;
            syncModels(true);bindModels();NotebookSettings settings=owner.currentSettings();RGB background=owner.currentBackground();int zoom=control.getMonitor().getZoom();
            PaperRenderer.Layout key=layout.fullKey(settings);
            if(paper==null||!key.equals(lastLayout)||settings!=lastSettings||!background.equals(lastBackground)||zoom!=lastZoom) {
                paper=PaperRenderer.render(settings,layout,background,zoom);
                lastLayout=key;lastSettings=settings;lastBackground=background;lastZoom=zoom;
            }
            if(nativeImage==null||nativeWidth!=bounds.width||nativeHeight!=bounds.height||nativeZoom!=zoom) {
                Image next=new Image(display,bounds.width,bounds.height);
                if(nativeImage!=null)nativeImage.dispose();nativeImage=next;
                nativeWidth=bounds.width;nativeHeight=bounds.height;nativeZoom=zoom;
            }
            GC nativeGC=null;
            try {
                nativeGC=new GC(nativeImage);
                // Invoke the native canvas painters with an offscreen GC. Control.print includes
                // child windows and can capture the overlay instead of the native foreground.
                nativeGC.setBackground(control.getBackground());nativeGC.fillRectangle(bounds);
                Event paint=new Event();paint.gc=nativeGC;paint.width=bounds.width;paint.height=bounds.height;
                capturing=true;try {control.notifyListeners(SWT.Paint,paint);}finally{capturing=false;}
                ImageData nativePixels=nativeImage.getImageData(zoom),result=(ImageData)paper.clone();
                composite(nativePixels,result,smoothNumbers?control.getForeground().getRGB():null);
                Image combined=new Image(display,(ImageDataProvider) z->z==zoom?result:result.scaledTo(
                    Math.max(1,(int)Math.round(layout.width*z/100.0)),Math.max(1,(int)Math.round(layout.height*z/100.0))));
                boolean retained=false;
                try {
                    output.drawImage(combined,0,0);
                    Image previous=frame;frame=combined;retained=true;
                    if(previous!=null)previous.dispose();
                } finally {if(!retained)combined.dispose();}
            } finally {if(nativeGC!=null)nativeGC.dispose();}
        } finally {painting=false;}
    }
    private static void composite(ImageData nativePixels,ImageData paper) {
        composite(nativePixels,paper,null);
    }
    private static void composite(ImageData nativePixels,ImageData paper,RGB numberInk) {
        int width=Math.min(nativePixels.width,paper.width),height=Math.min(nativePixels.height,paper.height);
        Map<Integer,Integer> histogram=new HashMap<>();
        Map<Integer,Integer> colors=new HashMap<>();
        int[] nativeRow=new int[width],paperRow=new int[width];
        // Native gutters contain long flat runs. Decode each distinct pixel value once
        // and count runs instead of allocating RGB/Integer objects for every pixel.
        for(int y=0;y<height;y++) {
            nativePixels.getPixels(0,y,width,nativeRow,0);
            for(int x=0;x<width;) {
                int pixel=nativeRow[x],end=x+1;while(end<width&&nativeRow[end]==pixel)end++;
                Integer rgb=colors.get(pixel);
                if(rgb==null){RGB c=nativePixels.palette.getRGB(pixel);rgb=c.red<<16|c.green<<8|c.blue;colors.put(pixel,rgb);}
                histogram.merge(rgb,end-x,Integer::sum);x=end;
            }
        }
        int background=histogram.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
        Integer ink=numberInk==null?null:findInk(colors.values(),background,numberInk);
        Map<Integer,Integer> foreground=new HashMap<>();
        Map<Integer,int[]> edges=new HashMap<>();
        for(Map.Entry<Integer,Integer> color:colors.entrySet()) {
            int rgb=color.getValue();
            if(rgb!=background) {
                foreground.put(color.getKey(),paper.palette.getPixel(new RGB(rgb>>>16&255,rgb>>>8&255,rgb&255)));
                if(ink!=null){int[] edge=transmission(rgb,background,ink);if(edge!=null)edges.put(color.getKey(),edge);}
            }
        }
        for(int y=0;y<height;y++) {
            nativePixels.getPixels(0,y,width,nativeRow,0);boolean changed=false;
            for(int x=0;x<width;) {
                int pixel=nativeRow[x],end=x+1;while(end<width&&nativeRow[end]==pixel)end++;
                Integer color=foreground.get(pixel);
                if(color!=null) {
                    if(!changed){paper.getPixels(0,y,width,paperRow,0);changed=true;}
                    int[] edge=edges.get(pixel);
                    if(edge==null)Arrays.fill(paperRow,x,end,color);
                    else for(int at=x;at<end;at++) {
                        RGB under=paper.palette.getRGB(paperRow[at]);
                        paperRow[at]=paper.palette.getPixel(new RGB(
                            blendEdge(edge[0],under.red,background>>>16&255,edge[3]),
                            blendEdge(edge[1],under.green,background>>>8&255,edge[4]),
                            blendEdge(edge[2],under.blue,background&255,edge[5])));
                    }
                }
                x=end;
            }
            if(changed)paper.setPixels(0,y,width,paperRow,0);
        }
    }
    private static Integer findInk(Collection<Integer> colors,int background,RGB hint) {
        int br=background>>>16&255,bg=background>>>8&255,bb=background&255;
        double hr=hint.red-br,hg=hint.green-bg,hb=hint.blue-bb,length=hr*hr+hg*hg+hb*hb;
        if(length<1)return null;
        double best=36;Integer ink=null;
        for(int color:colors) {
            double r=(color>>>16&255)-br,g=(color>>>8&255)-bg,b=(color&255)-bb,contrast=r*r+g*g+b*b;
            double projection=(r*hr+g*hg+b*hb)/length;
            double error=Math.pow(r-projection*hr,2)+Math.pow(g-projection*hg,2)+Math.pow(b-projection*hb,2);
            // Both directions allow an older native Canvas color after a CSS theme change.
            if(contrast>best&&error<=Math.max(9,contrast*.025)){best=contrast;ink=color;}
        }
        return ink;
    }
    private static int[] transmission(int color,int background,int ink) {
        int[] channels={color>>>16&255,color>>>8&255,color&255,0,0,0};
        double dot=0,length=0;
        for(int i=0;i<3;i++) {
            int shift=16-i*8,b=background>>>shift&255,f=ink>>>shift&255,c=channels[i];
            if(c<Math.min(b,f)-2||c>Math.max(b,f)+2)return null;
            dot+=(c-b)*(double)(f-b);length+=(f-b)*(double)(f-b);
        }
        boolean translucent=false;
        for(int i=0;i<3;i++) {
            int shift=16-i*8,b=background>>>shift&255,f=ink>>>shift&255;
            double value=Math.abs(b-f)>=8?(channels[i]-f)/(double)(b-f):1-dot/length;
            channels[i+3]=(int)Math.round(Math.max(0,Math.min(1,value))*256);
            translucent|=channels[i+3]>0;
        }
        return translucent?channels:null;
    }
    private static int blendEdge(int nativeChannel,int paperChannel,int nativeBackground,int transmission) {
        return Math.max(0,Math.min(255,nativeChannel+(int)Math.round((paperChannel-nativeBackground)*transmission/256.0)));
    }
    @Override public void close() {
        if(closed)return;closed=true;
        if(!control.isDisposed())syncModels(false);
        for(RulerModel model:cleanModels.values())model.close();cleanModels.clear();
        for(IAnnotationModel model:models)model.removeAnnotationModelListener(modelChanges);models.clear();sources.clear();
        if(preferenceStore!=null)preferenceStore.removePropertyChangeListener(preferences);
        paper=null;if(nativeImage!=null)nativeImage.dispose();if(frame!=null)frame.dispose();if(!overlay.isDisposed())overlay.dispose();
        if(!control.isDisposed()){
            control.removeListener(SWT.Paint,refresh);control.removeListener(SWT.Resize,refresh);
            control.removeListener(SWT.Dispose,disposed);
            control.setData("dev.eclipseline.rulerPaper",null);control.setData("dev.eclipseline.rulerOverlay",null);control.redraw();
        }
    }
}

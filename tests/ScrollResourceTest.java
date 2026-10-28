package dev.eclipseline;

import java.lang.reflect.Field;
import java.util.Map;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

/** Integer-row scrolling must retain identical paper rasters rather than regenerate them. */
public final class ScrollResourceTest {
    public static void main(String[] args)throws Exception {
        DeviceData tracking=new DeviceData();tracking.tracking=true;Display display=new Display(tracking);
        Shell shell=new Shell(display);shell.setText("Paper scroll resource regression");shell.setLayout(new FillLayout());
        CompositeRuler ruler=new CompositeRuler();LineNumberRulerColumn column=new LineNumberRulerColumn();ruler.addDecorator(0,column);
        SourceViewer viewer=new SourceViewer(shell,ruler,SWT.V_SCROLL);viewer.setDocument(new Document("012  resource scroll row\n".repeat(350)));
        StyledText text=viewer.getTextWidget();Font font=new Font(display,"Consolas",22,SWT.NORMAL);text.setFont(font);column.setFont(font);
        NotebookSettings settings=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,14,65,new RGB(112,148,173),new RGB(247,244,233),new RGB(137,159,171));
        NotebookSurface surface=new NotebookSurface(text,()->settings,false);surface.attachRuler(column,viewer);
        shell.setSize(850,650);shell.open();if(text.getCaret()!=null)text.getCaret().setVisible(false);pump(display,250);
        try {
            int row=text.getLineHeight()+text.getLineSpacing();text.setTopPixel(row*20);pump(display,150);
            Object[] initial=images(surface);int warmImages=liveImages(display);
            for(int i=0;i<12;i++) {
                text.setTopPixel(row*(20+(i%4)*3));pump(display,60);
                Object[] current=images(surface);
                for(int j=0;j<initial.length;j++)if(initial[j]!=current[j])throw new AssertionError(names[j]+" regenerated on integer-row scroll: step="+i+", top="+text.getTopPixel());
                if(liveImages(display)>warmImages+2)throw new AssertionError("Live native images grew while scrolling");
            }
            Object background=field(surface,"image"),rules=field(surface,"rulesImage");
            text.setTopPixel(row*23+row/2);pump(display,120);
            if(field(surface,"image")!=background)throw new AssertionError("Static paper background regenerated for a partial-row scroll");
            if(field(surface,"rulesImage")==rules)throw new AssertionError("Rule image was not invalidated when its visible phase changed");
            int[] paints={0};Listener counter=event->paints[0]++;text.addListener(SWT.Paint,counter);column.getControl().addListener(SWT.Paint,counter);
            Control overlay=(Control)column.getControl().getData("dev.eclipseline.rulerOverlay");overlay.addListener(SWT.Paint,counter);pump(display,180);
            if(paints[0]!=0)throw new AssertionError("Idle paper scheduled "+paints[0]+" repaint events");
            Image retained=text.getBackgroundImage();text.setBackgroundImage(null);surface.refresh();pump(display,100);
            if(text.getBackgroundImage()!=retained||retained.isDisposed())throw new AssertionError("Theme-cleared background was not reattached from the retained image");
            System.out.println("PASS: 12 integer-row scrolls reuse source background, rules, padding and gutter paper images; partial-row scroll reuses background and updates rule phase; idle paints=0; liveImages="+liveImages(display));
            System.out.println("PASS: theme-cleared source background reattaches the same retained image without raster regeneration");
        } finally {surface.close();shell.dispose();font.dispose();display.dispose();}
    }
    private static final String[] names={"source background","source rules","padding background","gutter paper"};
    private static Object[] images(NotebookSurface surface)throws Exception {
        Object padding=field(surface,"padding");Map<?,?> rulers=(Map<?,?>)field(surface,"rulers");Object ruler=rulers.values().iterator().next();
        return new Object[]{field(surface,"image"),field(surface,"rulesImage"),padding==null?null:field(padding,"image"),field(ruler,"paper")};
    }
    private static Object field(Object target,String name)throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static int liveImages(Display display) {int n=0;for(Object object:display.getDeviceData().objects)if(object instanceof Image&&!((Image)object).isDisposed())n++;return n;}
    private static void pump(Display display,int millis)throws Exception {long until=System.currentTimeMillis()+millis;while(System.currentTimeMillis()<until){if(!display.readAndDispatch())Thread.sleep(3);}}
}

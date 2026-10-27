package dev.eclipseline;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

/** Detects image churn, idle paint loops, restore damage, and native ruler label loss. */
public final class StabilityAuditTest {
    public static void main(String[] args)throws Exception {
        DeviceData tracking=new DeviceData();tracking.tracking=true;Display display=new Display(tracking);
        Shell shell=new Shell(display);shell.setLayout(new FillLayout());
        CompositeRuler ruler=new CompositeRuler();LineNumberRulerColumn numbers=new LineNumberRulerColumn();ruler.addDecorator(0,numbers);
        SourceViewer viewer=new SourceViewer(shell,ruler,SWT.V_SCROLL|SWT.H_SCROLL);viewer.setDocument(new Document("1\n2\n3\n"+"restore row\n".repeat(100)));
        StyledText text=viewer.getTextWidget();numbers.setFont(text.getFont());numbers.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
        NotebookSettings config=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,17,80,new RGB(112,148,173),new RGB(222,205,162),new RGB(137,159,171));
        NotebookSurface surface=new NotebookSurface(text,()->config,true);surface.attachRuler(numbers,viewer);
        Composite host=(Composite)viewer.getControl();shell.setSize(640,420);shell.open();pump(display,350);
        try {
            Image body=text.getBackgroundImage(),frame=host.getBackgroundImage();Rectangle original=text.getBounds();
            for(int i=0;i<100;i++)surface.prepare();
            if(body!=text.getBackgroundImage()||frame!=host.getBackgroundImage())throw new AssertionError("Unchanged prepare creates new images");
            System.out.println("PASS: 100 unchanged prepares retain body and frame image identities");
            int[] paints={0};host.addListener(SWT.Paint,e->paints[0]++);text.addListener(SWT.Paint,e->paints[0]++);
            pump(display,250);int settled=paints[0];pump(display,300);
            if(paints[0]-settled>2)throw new AssertionError("Idle self-repaint loop: "+(paints[0]-settled));
            System.out.println("PASS: idle viewer settles without a self-repaint loop");
            int images=countImages(display);
            for(int cycle=0;cycle<8;cycle++) {
                shell.setMinimized(true);pump(display,40);shell.setMinimized(false);pump(display,90);surface.restored();pump(display,90);
                if(!text.getBounds().equals(original))throw new AssertionError("Restore changes editor bounds: "+text.getBounds()+" expected "+original);
                assertNumbers(display,numbers,surface,config,text);
            }
            if(countImages(display)>images+4)throw new AssertionError("Native images accumulate across restore cycles");
            System.out.println("PASS: 8 minimize/restore cycles preserve bounds, labels 1/2/3 and native image count");
        } finally {surface.close();shell.dispose();display.dispose();}
    }
    private static int countImages(Display display){int count=0;DeviceData data=display.getDeviceData();for(Object object:data.objects)if(object instanceof Image&&!((Image)object).isDisposed())count++;return count;}
    private static void assertNumbers(Display display,LineNumberRulerColumn column,NotebookSurface surface,NotebookSettings settings,StyledText text) {
        Control overlay=(Control)column.getControl().getData("dev.eclipseline.rulerOverlay");Point size=overlay.getSize();Image image=new Image(display,size.x,size.y);GC gc=new GC(image);
        try {
            overlay.print(gc);ImageData actual=image.getImageData(100),paper=PaperRenderer.render(settings,surface.rulerLayout(column.getControl()),settings.paperColor,100);
            for(int line=0;line<3;line++) {
                int ink=0,top=text.getLinePixel(line);
                for(int y=Math.max(0,top);y<Math.min(actual.height,top+text.getLineHeight());y++)for(int x=0;x<actual.width;x++) {
                    RGB a=actual.palette.getRGB(actual.getPixel(x,y)),b=paper.palette.getRGB(paper.getPixel(x,y));
                    if(Math.abs(a.red-b.red)+Math.abs(a.green-b.green)+Math.abs(a.blue-b.blue)>80)ink++;
                }
                if(ink<3)throw new AssertionError("Native number "+(line+1)+" lost after restore");
            }
        } finally {gc.dispose();image.dispose();}
    }
    private static void pump(Display display,int millis)throws Exception {long until=System.currentTimeMillis()+millis;while(System.currentTimeMillis()<until){if(!display.readAndDispatch())Thread.sleep(3);}}
}

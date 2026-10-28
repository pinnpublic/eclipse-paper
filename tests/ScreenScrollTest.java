package dev.eclipseline;

import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.POINT;
import org.eclipse.swt.internal.win32.RECT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

/** Samples displayed pixels without Control.print, WM_PRINT, redraw, or foreground activation. */
public final class ScreenScrollTest {
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args.length==0?".build/screen-scroll":args[0]);Files.createDirectories(output);
        Display display=new Display();Shell shell=new Shell(display);shell.setText("Paper displayed-pixel wheel regression");shell.setLayout(new FillLayout());
        CompositeRuler ruler=new CompositeRuler();LineNumberRulerColumn numbers=new LineNumberRulerColumn();ruler.addDecorator(0,numbers);
        SourceViewer viewer=new SourceViewer(shell,ruler,SWT.V_SCROLL|SWT.H_SCROLL);
        StringBuilder document=new StringBuilder();for(int i=1;i<=350;i++)document.append(String.format("%03d  screen source row %03d%n",i,i));
        viewer.setDocument(new Document(document.toString()));StyledText text=viewer.getTextWidget();
        Font font=new Font(display,"Consolas",22,SWT.NORMAL);text.setFont(font);numbers.setFont(font);
        numbers.setForeground(display.getSystemColor(SWT.COLOR_BLACK));text.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
        NotebookSettings settings=new NotebookSettings(true,true,false,0,0,4,15,30,100,100,22,0,0,new RGB(0,180,255),new RGB(247,244,233),new RGB(137,159,171));
        NotebookSurface surface=new NotebookSurface(text,()->settings,false);surface.attachRuler(numbers,viewer);
        shell.setSize(850,650);shell.open();pump(display,350);
        Control overlay=(Control)numbers.getControl().getData("dev.eclipseline.rulerOverlay");
        int[] paints={0};Listener counter=event->paints[0]++;text.addListener(SWT.Paint,counter);numbers.getControl().addListener(SWT.Paint,counter);overlay.addListener(SWT.Paint,counter);
        Caret caret=text.getCaret();if(caret!=null)caret.setVisible(false);
        try {
            int row=text.getLineHeight()+text.getLineSpacing(),steps=0;
            for(int start:new int[]{row*20,row*20+1,row*20+row/2+3}) {
                text.setTopPixel(start);pump(display,120);
                for(int delta:new int[]{-120,-120,-240,120,-360,240,120}) {
                    Point location=text.toDisplay(100,80);long coordinates=(location.x&0xffffL)|((location.y&0xffffL)<<16);
                    OS.SendMessage(text.handle,OS.WM_MOUSEWHEEL,(long)delta<<16,coordinates);pump(display,120);
                    int before=paints[0];ImageData source=capture(text,shell),gutter=capture(overlay,shell);
                    if(paints[0]!=before)throw new AssertionError("Screen capture triggered paint events");
                    Point shift=display.map(text,overlay,0,0);
                    try {assertFrame(source,gutter,text,shift,settings);}catch(AssertionError error) {
                        save(source,output.resolve("failure-source.png"));save(gutter,output.resolve("failure-gutter.png"));throw error;
                    }
                    System.out.println("Screen step="+steps+++", top="+text.getTopPixel()+", mapY="+shift.y+", paints="+paints[0]+", capturePaints=0");
                }
            }
            System.out.println("PASS: "+steps+" native wheel frames align displayed gutter/source rules and glyphs without repaint capture");
        } finally {surface.close();shell.dispose();font.dispose();display.dispose();}
    }
    private static ImageData capture(Control control,Shell shell) {
        Rectangle area=((Scrollable)control).getClientArea();Point origin=control.toDisplay(area.x,area.y);assertUnoccluded(shell,origin,area.width,area.height);
        Image image=new Image(control.getDisplay(),area.width,area.height);GC screen=new GC(control.getDisplay());
        try {
            screen.copyArea(image,origin.x,origin.y);assertUnoccluded(shell,origin,area.width,area.height);
            int zoom=control.getMonitor().getZoom();
            try {java.lang.reflect.Method method=GC.class.getDeclaredMethod("getZoom");method.setAccessible(true);zoom=(Integer)method.invoke(screen);}catch(ReflectiveOperationException ignored) { }
            ImageData pixels=image.getImageData(zoom);
            if(pixels.width!=area.width||pixels.height!=area.height)pixels=pixels.scaledTo(area.width,area.height);
            boolean colored=false;for(int y=0;y<pixels.height&&!colored;y+=7)for(int x=0;x<pixels.width;x+=7) {
                RGB c=pixels.palette.getRGB(pixels.getPixel(x,y));if(c.red<240||c.green<240||c.blue<230){colored=true;break;}
            }
            if(!colored) {
                // Try an independent GC-owned target if copyArea yielded no visible content.
                // This remains a native pixel copy and never asks a widget to repaint.
                GC target=new GC(image);
                try {
                    RECT bounds=new RECT();OS.GetWindowRect(shell.handle,bounds);Rectangle logical=shell.getBounds();
                    int nativeX=bounds.left+(int)Math.round((origin.x-logical.x)*(bounds.right-bounds.left)/(double)logical.width);
                    int nativeY=bounds.top+(int)Math.round((origin.y-logical.y)*(bounds.bottom-bounds.top)/(double)logical.height);
                    if(!OS.BitBlt(target.handle,0,0,(int)Math.round(area.width*zoom/100.0),(int)Math.round(area.height*zoom/100.0),screen.handle,nativeX,nativeY,OS.SRCCOPY|0x40000000))throw new AssertionError("Native screen pixel copy failed");
                } finally {target.dispose();}
                pixels=image.getImageData(zoom);if(pixels.width!=area.width||pixels.height!=area.height)pixels=pixels.scaledTo(area.width,area.height);
                colored=false;for(int y=0;y<pixels.height&&!colored;y+=7)for(int x=0;x<pixels.width;x+=7) {
                    RGB c=pixels.palette.getRGB(pixels.getPixel(x,y));if(c.red<240||c.green<240||c.blue<230){colored=true;break;}
                }
                if(!colored)throw new AssertionError("Screen capture returned a blank surface; display pixels are unavailable (zoom="+zoom+")");
            }
            return pixels;
        }
        finally {screen.dispose();image.dispose();}
    }
    private static void assertUnoccluded(Shell shell,Point origin,int width,int height) {
        RECT nativeBounds=new RECT();if(!OS.GetWindowRect(shell.handle,nativeBounds))throw new AssertionError("Cannot inspect shell screen bounds");
        Rectangle logical=shell.getBounds();double sx=(nativeBounds.right-nativeBounds.left)/(double)logical.width,sy=(nativeBounds.bottom-nativeBounds.top)/(double)logical.height;
        for(int y=2;y<height;y+=35)for(int x=2;x<width;x+=35) {
            POINT point=new POINT();point.x=nativeBounds.left+(int)Math.round((origin.x+x-logical.x)*sx);point.y=nativeBounds.top+(int)Math.round((origin.y+y-logical.y)*sy);
            long window=OS.WindowFromPoint(point),root=OS.GetAncestor(window,OS.GA_ROOT);
            if(root!=shell.handle)throw new AssertionError("Displayed-pixel test is occluded at "+point.x+","+point.y+"; no foreground activation was requested");
        }
    }
    private static void assertFrame(ImageData source,ImageData gutter,StyledText text,Point shift,NotebookSettings settings) {
        PaperRenderer.Layout layout=NotebookSurface.layout(text,text.getLineHeight(),settings);
        int sourceX=Math.max(1,source.width-15),gutterX=0;
        for(int y=2;y<source.height-2;y++) {
            int gy=y+shift.y;if(gy<2||gy>=gutter.height-2)continue;
            if(isRule(source,sourceX,y)!=isRule(gutter,gutterX,gy))throw new AssertionError("Displayed rule phase differs at top="+text.getTopPixel()+", y="+y+", mapY="+shift.y);
        }
        for(double rule:layout.rules) {
            int y=(int)Math.round(rule);if(y<3||y>=source.height-3)continue;
            boolean found=false;for(int dy=-2;dy<=2;dy++)found|=isRule(source,sourceX,y+dy);
            if(!found)throw new AssertionError("Displayed source rule is stale at top="+text.getTopPixel()+", expectedY="+rule);
        }
        int first=text.getLineIndex(0);if(text.getLinePixel(first)<0)first++;
        for(int line=first;line<Math.min(text.getLineCount(),first+4);line++) {
            int top=text.getLocationAtOffset(text.getOffsetAtLine(line)).y,height=text.getLineHeight();
            int a=inkBottom(source,top,height,Math.min(80,source.width)),b=inkBottom(gutter,top+shift.y,height,gutter.width)-shift.y;
            if(a<0||b<0||Math.abs(a-b)>1)throw new AssertionError("Displayed glyph baselines differ: line="+(line+1)+", top="+text.getTopPixel()+", source="+a+", numbers="+b);
        }
    }
    private static boolean isRule(ImageData image,int x,int y) {RGB c=image.palette.getRGB(image.getPixel(x,y));return c.blue-c.red>80&&c.green-c.red>40;}
    private static int inkBottom(ImageData image,int top,int height,int width) {
        int bottom=-1;for(int y=Math.max(0,top);y<Math.min(image.height,top+height);y++)for(int x=0;x<width;x++) {
            RGB c=image.palette.getRGB(image.getPixel(x,y));if(c.red<100&&c.green<100&&c.blue<100)bottom=y;
        }return bottom;
    }
    private static void save(ImageData data,Path path) {ImageLoader loader=new ImageLoader();loader.data=new ImageData[]{data};loader.save(path.toString(),SWT.IMAGE_PNG);}
    private static void pump(Display display,int millis)throws Exception {long until=System.currentTimeMillis()+millis;while(System.currentTimeMillis()<until){if(!display.readAndDispatch())Thread.sleep(3);}}
}

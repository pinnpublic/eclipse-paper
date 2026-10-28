package dev.eclipseline;

import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

/** Drives the native wheel path instead of manually refreshing the paper after setTopIndex. */
public final class ScrollAlignmentTest {
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[0]);Files.createDirectories(output);
        Display display=new Display();Shell shell=new Shell(display);shell.setText("Paper native wheel regression");shell.setLayout(new FillLayout());
        CompositeRuler ruler=new CompositeRuler();LineNumberRulerColumn numbers=new LineNumberRulerColumn();ruler.addDecorator(0,numbers);
        SourceViewer viewer=new SourceViewer(shell,ruler,SWT.V_SCROLL|SWT.H_SCROLL);
        StringBuilder content=new StringBuilder();for(int i=1;i<=350;i++)content.append(String.format("%03d  visible source row %03d%n",i,i));
        viewer.setDocument(new Document(content.toString()));StyledText text=viewer.getTextWidget();
        Font font=new Font(display,"Consolas",22,SWT.NORMAL);text.setFont(font);numbers.setFont(font);numbers.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
        NotebookSettings settings=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,14,65,new RGB(112,148,173),new RGB(247,244,233),new RGB(137,159,171));
        NotebookSurface surface=new NotebookSurface(text,()->settings,true);surface.attachRuler(numbers,viewer);
        shell.setSize(850,650);shell.open();pump(display,300);
        Control overlay=(Control)numbers.getControl().getData("dev.eclipseline.rulerOverlay");
        try {
            assertBaselines(overlay,text,surface,settings,output,"initial");
            int row=text.getLineHeight()+text.getLineSpacing(),pass=0;
            for(int start:new int[]{0,1,row/2+3,row*20+row/2,row*110+3}) {
                text.setTopPixel(start);pump(display,80);
                for(int delta:new int[]{-120,-120,-240,120,-360,240,120}) {
                    int before=text.getTopPixel();
                    Point point=text.toDisplay(100,80);long coordinates=(point.x&0xffffL)|((point.y&0xffffL)<<16);
                    OS.SendMessage(text.handle,OS.WM_MOUSEWHEEL,(long)delta<<16,coordinates);pump(display,80);
                    System.out.println("Wheel "+pass+": top="+before+" -> "+text.getTopPixel()+", first="+text.getLineIndex(0)+", firstY="+text.getLinePixel(text.getLineIndex(0)));
                    assertBaselines(overlay,text,surface,settings,output,"wheel-"+pass++);
                }
            }
            System.out.println("PASS: native wheel maintains ruler/source baselines at whole and partial top rows; "+pass+" wheel steps");
        } finally {surface.close();shell.dispose();font.dispose();display.dispose();}
    }
    private static void assertBaselines(Control overlay,StyledText text,NotebookSurface surface,NotebookSettings settings,Path output,String label) {
        Image numbers=new Image(text.getDisplay(),overlay.getSize().x,overlay.getSize().y),source=new Image(text.getDisplay(),text.getSize().x,text.getSize().y);
        GC numberGC=new GC(numbers),sourceGC=new GC(source);Caret caret=text.getCaret();boolean visible=caret!=null&&caret.getVisible();
        try {
            if(caret!=null)caret.setVisible(false);
            overlay.print(numberGC);text.print(sourceGC);
            ImageData actualNumbers=numbers.getImageData(100),actualSource=source.getImageData(100);
            ImageData numberPaper=PaperRenderer.render(settings,surface.rulerLayout(overlay.getParent()),settings.paperColor,100);
            ImageData sourcePaper=PaperRenderer.render(settings,NotebookSurface.layout(text,text.getLineHeight(),settings),settings.paperColor,100);
            int start=text.getLineIndex(0);if(text.getLinePixel(start)<0)start++;
            for(int line=start;line<Math.min(text.getLineCount(),start+6);line++) {
                int top=text.getLocationAtOffset(text.getOffsetAtLine(line)).y,height=text.getLineHeight();
                int numberBottom=inkBottom(actualNumbers,numberPaper,top,height,actualNumbers.width);
                int sourceBottom=inkBottom(actualSource,sourcePaper,top,height,Math.min(50,actualSource.width));
                if(numberBottom<0||sourceBottom<0||Math.abs(numberBottom-sourceBottom)>1) {
                    save(actualNumbers,output.resolve(label+"-numbers.png"));save(actualSource,output.resolve(label+"-source.png"));
                    throw new AssertionError(label+": line="+(line+1)+", topPixel="+text.getTopPixel()+", number bottom="+numberBottom+", source bottom="+sourceBottom);
                }
            }
        } finally {if(caret!=null&&!caret.isDisposed())caret.setVisible(visible);numberGC.dispose();sourceGC.dispose();numbers.dispose();source.dispose();}
    }
    private static int inkBottom(ImageData actual,ImageData paper,int top,int height,int width) {
        int bottom=-1;for(int y=Math.max(0,top);y<Math.min(actual.height,top+height);y++)for(int x=0;x<width;x++) {
            RGB a=actual.palette.getRGB(actual.getPixel(x,y)),b=paper.palette.getRGB(paper.getPixel(x,y));
            if(Math.abs(a.red-b.red)+Math.abs(a.green-b.green)+Math.abs(a.blue-b.blue)>100)bottom=y;
        }return bottom;
    }
    private static void save(ImageData data,Path path){ImageLoader loader=new ImageLoader();loader.data=new ImageData[]{data};loader.save(path.toString(),SWT.IMAGE_PNG);}
    private static void pump(Display display,int millis)throws Exception {long until=System.currentTimeMillis()+millis;while(System.currentTimeMillis()<until){if(!display.readAndDispatch())Thread.sleep(3);}}
}

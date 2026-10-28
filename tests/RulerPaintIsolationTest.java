package dev.eclipseline;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

/** A gutter that paints first must keep the same complete scene as the unpainted source. */
public final class RulerPaintIsolationTest {
    public static void main(String[] args)throws Exception {
        Display display=new Display();Shell shell=new Shell(display);shell.setLayout(new FillLayout());
        CompositeRuler ruler=new CompositeRuler();LineNumberRulerColumn column=new LineNumberRulerColumn();ruler.addDecorator(0,column);
        SourceViewer viewer=new SourceViewer(shell,ruler,SWT.V_SCROLL);viewer.setDocument(new Document("012  scroll row\n".repeat(350)));
        StyledText text=viewer.getTextWidget();
        NotebookSettings settings=new NotebookSettings(true,true,false,0,0,4,15,30,100,100,22,0,0,new RGB(0,180,255),new RGB(247,244,233),new RGB(137,159,171));
        NotebookSurface surface=new NotebookSurface(text,()->settings,true);surface.attachRuler(column,viewer);
        shell.setSize(700,500);shell.open();long until=System.currentTimeMillis()+300;while(System.currentTimeMillis()<until){if(!display.readAndDispatch())Thread.sleep(3);}
        try {
            Control overlay=(Control)column.getControl().getData("dev.eclipseline.rulerOverlay");
            Image previous=new Image(display,overlay.getSize().x,overlay.getSize().y);GC previousGC=new GC(previous);
            ImageData previousPixels;
            try{overlay.print(previousGC);previousPixels=previous.getImageData(100);}finally{previousGC.dispose();previous.dispose();}
            text.setRedraw(false);
            Image before=text.getBackgroundImage();int spacing=text.getLineSpacing();Rectangle bounds=text.getBounds();
            text.setTopPixel(137);
            if(text.getTopPixel()!=137)throw new AssertionError("Scroll did not advance while source paint was pending");
            Image output=new Image(display,overlay.getSize().x,overlay.getSize().y);GC gc=new GC(output);
            try {
                overlay.print(gc);
                ImageData actual=output.getImageData(100);
                if(!java.util.Arrays.equals(previousPixels.data,actual.data))
                    throw new AssertionError("Gutter digits/paper advanced while the source still displays its previous frame; top="+text.getTopPixel());
            }finally{gc.dispose();output.dispose();}
            if(text.getBackgroundImage()!=before||before.isDisposed()||text.getLineSpacing()!=spacing||!text.getBounds().equals(bounds))
                throw new AssertionError("Gutter paint replaced or mutated the pending source frame");
            System.out.println("PASS: gutter-first paint retains the complete previous scene, including digits; pending source image, spacing and bounds remain intact");
        } finally {text.setRedraw(true);surface.close();shell.dispose();display.dispose();}
    }
}

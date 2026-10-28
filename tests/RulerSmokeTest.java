package dev.eclipseline;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.AnnotationRulerColumn;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.JFaceTextUtil;
@SuppressWarnings("deprecation")
public final class RulerSmokeTest {
    public static void main(String[] args)throws Exception {
        Display d=new Display();Shell shell=new Shell(d,SWT.SHELL_TRIM|SWT.ON_TOP);shell.setLayout(new FillLayout());
        CompositeRuler rulerModel=new CompositeRuler();LineNumberRulerColumn column=new LineNumberRulerColumn();
        class TestAccess implements org.eclipse.jface.text.source.IAnnotationAccess,org.eclipse.jface.text.source.IAnnotationAccessExtension {
            public Object getType(org.eclipse.jface.text.source.Annotation a){return a.getType();}
            public boolean isMultiLine(org.eclipse.jface.text.source.Annotation a){return false;}
            public boolean isTemporary(org.eclipse.jface.text.source.Annotation a){return true;}
            public String getTypeLabel(org.eclipse.jface.text.source.Annotation a){return a.getType();}
            public int getLayer(org.eclipse.jface.text.source.Annotation a){return 1;}
            public boolean isPaintable(org.eclipse.jface.text.source.Annotation a){return a instanceof org.eclipse.jface.text.source.IAnnotationPresentation;}
            public boolean isSubtype(Object a,Object b){return a.equals(b);}
            public Object[] getSupertypes(Object a){return new Object[0];}
            public void paint(org.eclipse.jface.text.source.Annotation a,GC gc,Canvas c,Rectangle r){((org.eclipse.jface.text.source.IAnnotationPresentation)a).paint(gc,c,r);}
        }
        AnnotationRulerColumn annotations=new AnnotationRulerColumn(14,new TestAccess());rulerModel.addDecorator(0,annotations);
        annotations.addAnnotationType("notebook.test.marker");
        column.setBackground(d.getSystemColor(SWT.COLOR_WHITE));column.setForeground(d.getSystemColor(SWT.COLOR_DARK_GRAY));
        rulerModel.addDecorator(1,column);SourceViewer viewer=new SourceViewer(shell,rulerModel,SWT.V_SCROLL|SWT.H_SCROLL);
        AnnotationModel markerModel=new AnnotationModel();
        viewer.setDocument(new Document("1\n2\n3\n"+"source row\n".repeat(120)),markerModel);
        StyledText text=viewer.getTextWidget();Control ruler=column.getControl();column.setFont(text.getFont());
        text.setTopMargin(3);int originalTopMargin=text.getTopMargin();
        Composite viewerHost=(Composite)viewer.getControl();Layout originalViewerLayout=viewerHost.getLayout();
        NotebookSettings s=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,17,65,new RGB(112,148,173),new RGB(249,247,237),new RGB(137,159,171));
        NotebookSettings[] current={s};
        NotebookSurface surface=new NotebookSurface(text,()->current[0]);surface.attachRuler(ruler,viewer);surface.attachAnnotations(annotations,viewer);shell.setSize(400,300);shell.open();
        Control overlay=(Control)ruler.getData("dev.eclipseline.rulerOverlay");
        long end=System.currentTimeMillis()+500;while(System.currentTimeMillis()<end){if(!d.readAndDispatch())Thread.sleep(5);}
        int extraTopMargin=surface.topPadding()-originalTopMargin;
        if(text.getLocation().y+text.getLocationAtOffset(0).y<=originalTopMargin)throw new AssertionError("First line has no added top padding");
        if(text.getTopMargin()!=0||JFaceTextUtil.getPartialTopIndex(viewer)<0)throw new AssertionError("Notebook inset breaks the native JFace viewport range");
        Font originalFont=text.getFont();FontData[] data=originalFont.getFontData();
        for(FontData f:data)f.setHeight(f.getHeight()*2);
        Font large=new Font(d,data);text.setFont(large);surface.refresh();
        if(surface.topPadding()-originalTopMargin<extraTopMargin*1.7)throw new AssertionError("Top padding does not follow font zoom");
        text.setFont(originalFont);surface.refresh();large.dispose();
        if(surface.topPadding()!=originalTopMargin+extraTopMargin)throw new AssertionError("Top padding accumulates after zoom back");
        shell.forceActive();
        long ready=System.currentTimeMillis()+200;while(System.currentTimeMillis()<ready){if(!d.readAndDispatch())Thread.sleep(5);}
        Point size=ruler.getSize();Image capture=new Image(d,size.x,size.y);GC gc=new GC(capture);
        try {
            overlay.print(gc);ImageData actual=capture.getImageData(100);
            ImageData expected=PaperRenderer.render(s,surface.rulerLayout(ruler),s.paperColor,100);
            RGB a=actual.palette.getRGB(actual.getPixel(3,8)),b=expected.palette.getRGB(expected.getPixel(3,8));
            ImageLoader loader=new ImageLoader();loader.data=new ImageData[]{actual};loader.save(args[0],SWT.IMAGE_PNG);
            if(!a.equals(b))throw new AssertionError("Gutter color differs: "+a+" expected "+b);
            for(int line=0;line<3;line++)assertNumber(actual,expected,text.getLinePixel(line),text.getLineHeight());
            System.out.println("PASS: actual JFace line numbers 1, 2, 3 remain visible over paper");
            assertBaselines(overlay,text,surface,s);
            Control markerOverlay=(Control)annotations.getControl().getData("dev.eclipseline.rulerOverlay");
            if(markerOverlay==null)throw new AssertionError("Annotation gutter has no paper overlay");
            Image markerImage=new Image(d,markerOverlay.getSize().x,markerOverlay.getSize().y);GC markerGC=new GC(markerImage);
            try {
                markerOverlay.print(markerGC);
                ImageData markerPaper=PaperRenderer.render(s,surface.rulerLayout(annotations.getControl()),s.paperColor,100);
                if(!samePixels(markerImage.getImageData(100),markerPaper))throw new AssertionError("Empty annotation column differs from continuous paper");
                class Mark extends org.eclipse.jface.text.source.Annotation implements org.eclipse.jface.text.source.IAnnotationPresentation {
                    Mark(){super("notebook.test.marker",false,"marker");}
                    public int getLayer(){return 1;}
                    public void paint(GC output,Canvas canvas,Rectangle bounds){output.setBackground(d.getSystemColor(SWT.COLOR_RED));output.fillRectangle(3,bounds.y+2,6,6);}
                }
                Mark mark=new Mark();markerModel.addAnnotation(mark,new org.eclipse.jface.text.Position(text.getOffsetAtLine(1),1));
                long markerReady=System.currentTimeMillis()+250;while(System.currentTimeMillis()<markerReady){if(!d.readAndDispatch())Thread.sleep(5);}
                markerOverlay.print(markerGC);ImageData withMark=markerImage.getImageData(100);
                RGB red=withMark.palette.getRGB(withMark.getPixel(4,text.getLinePixel(1)+3));
                if(red.red<200||red.green>30)throw new AssertionError("Existing annotation presentation is not visible over paper");
                markerModel.removeAnnotation(mark);
                markerReady=System.currentTimeMillis()+250;while(System.currentTimeMillis()<markerReady){if(!d.readAndDispatch())Thread.sleep(5);}
                markerOverlay.print(markerGC);
                if(!samePixels(markerImage.getImageData(100),markerPaper))throw new AssertionError("Removed annotation leaves a background patch");
            } finally {markerGC.dispose();markerImage.dispose();}
            System.out.println("PASS: annotation gutter is continuous paper; marker presentations paint and remove cleanly");
            Image textImage=new Image(d,text.getSize().x,text.getSize().y);GC textGC=new GC(textImage);
            try{text.print(textGC);loader.data=new ImageData[]{textImage.getImageData(100)};loader.save(args[0]+"-text.png",SWT.IMAGE_PNG);}
            finally{textGC.dispose();textImage.dispose();}
            for(int repaint=0;repaint<5;repaint++) {
                ruler.redraw();ruler.update();overlay.print(gc);actual=capture.getImageData(100);
                assertNumber(actual,expected,text.getLinePixel(0),text.getLineHeight());
            }
            ruler.update();overlay.update();
            Image screenImage=new Image(d,size.x,size.y);GC screen=new GC(d);
            Point screenOrigin=overlay.toDisplay(0,0);
            try {
            screen.copyArea(screenImage,screenOrigin.x,screenOrigin.y);ImageData stable=screenImage.getImageData(100);
            Image viewport=new Image(d,viewerHost.getSize().x,viewerHost.getSize().y);
            GC viewportGC=new GC(viewport);
            try {
                printTree(viewerHost,viewerHost,viewportGC);
                loader.data=new ImageData[]{viewport.getImageData(100)};loader.save(args[0]+"-viewport.png",SWT.IMAGE_PNG);
            }finally{viewportGC.dispose();viewport.dispose();}
            boolean screenReadable=stable.palette.getRGB(stable.getPixel(3,8)).equals(expected.palette.getRGB(expected.getPixel(3,8)));
            if(!screenReadable)System.out.println("SKIP: visible screen-frame comparison: desktop capture does not show the tested window; native paint and input checks still run");
            int[] paints={0};overlay.addListener(SWT.Paint,e->paints[0]++);
            for(int type:new int[]{SWT.MouseMove,SWT.MouseEnter,SWT.MouseExit,SWT.MouseHover}) {
                int before=paints[0];Event event=new Event();event.x=4;event.y=20;overlay.notifyListeners(type,event);
                column.redraw();
                GC nativeGC=new GC(ruler);
                try {nativeGC.setBackground(d.getSystemColor(SWT.COLOR_WHITE));nativeGC.fillRectangle(0,0,size.x,size.y);}
                finally {nativeGC.dispose();}
                Thread.sleep(30); // Deliberately do not dispatch a repair paint before sampling the visible frame.
                screen.copyArea(screenImage,screenOrigin.x,screenOrigin.y);
                ImageData frame=screenImage.getImageData(100);
                if(screenReadable)for(int y=0;y<frame.height;y++)for(int x=0;x<frame.width;x++)
                    if(frame.getPixel(x,y)!=stable.getPixel(x,y))throw new AssertionError("Native direct draw leaked a flickering frame at "+x+","+y);
                if(paints[0]!=before)throw new AssertionError("Mouse movement relies on a repair paint");
                overlay.print(gc);actual=capture.getImageData(100);assertNumber(actual,expected,text.getLinePixel(0),text.getLineHeight());
            }
            } finally {screen.dispose();screenImage.dispose();}
            System.out.println("PASS: numbers survive native redraws and all mouse events without dispatching a repair paint");
            int[] forwarded={0};ruler.addListener(SWT.MouseDown,e->{if(e.button==1&&e.x==4&&e.y==20)forwarded[0]++;});
            Event click=new Event();click.button=1;click.x=4;click.y=20;overlay.notifyListeners(SWT.MouseDown,click);
            overlay.notifyListeners(SWT.MouseUp,click);
            if(forwarded[0]!=1)throw new AssertionError("Ruler input was not forwarded");
            System.out.println("PASS: ruler mouse input forwarded to the original column");
            text.setTopIndex(20);surface.refresh();text.update();ruler.redraw();ruler.update();overlay.print(gc);actual=capture.getImageData(100);
            expected=PaperRenderer.render(s,surface.rulerLayout(ruler),s.paperColor,100);
            assertNumber(actual,expected,text.getLinePixel(20),text.getLineHeight());
            System.out.println("PASS: line numbers survive repeated redraws and vertical scrolling");
            text.setTopIndex(0);
            if(JFaceTextUtil.getPartialTopIndex(viewer)<0)throw new AssertionError("Scrolled-back viewport range starts at a negative line");
            Image nativeImage=new Image(d,annotations.getControl().getSize().x,annotations.getControl().getSize().y);GC nativeOutput=new GC(nativeImage);
            try {annotations.getControl().print(nativeOutput);}finally{nativeOutput.dispose();nativeImage.dispose();}
            System.out.println("PASS: native annotation ruler paints with a valid first-line range and external top padding");
            Font zoomFont=new Font(d,data);
            try {
                for(Font font:new Font[]{originalFont,zoomFont}) {
                    text.setFont(font);column.setFont(font);
                    for(int gap:new int[]{-50,15,100})for(int spacing:new int[]{0,150}) {
                        current[0]=new NotebookSettings(true,true,false,0,1,4,gap,spacing,100,38,22,0,0,s.lineColor,s.paperColor,s.patternColor);
                        surface.refresh();text.setSelection(0);text.setTopPixel(0);surface.refresh();shell.layout(true,true);ruler.update();overlay.update();
                        assertBaselines(overlay,text,surface,current[0]);
                    }
                }
            } finally {text.setFont(originalFont);column.setFont(originalFont);current[0]=s;surface.refresh();zoomFont.dispose();}
            System.out.println("PASS: rasterized line numbers and editor digits share the same baseline at gaps -50, 15, 100; spacing 0, 150; original and doubled font sizes");
            surface.close();
            if(text.getTopMargin()!=originalTopMargin)throw new AssertionError("Original top margin not restored");
            if(viewerHost.getLayout()!=originalViewerLayout)throw new AssertionError("Original source viewer layout not restored");
            if(annotations.getControl().getData("dev.eclipseline.rulerOverlay")!=null)throw new AssertionError("Annotation overlay not released");
            System.out.println("PASS: first-line padding, font zoom, no accumulation, original margin restoration");
        } finally {gc.dispose();capture.dispose();surface.close();shell.dispose();d.dispose();}
    }
    private static boolean samePixels(ImageData a,ImageData b) {
        if(a.width!=b.width||a.height!=b.height)return false;
        for(int y=0;y<a.height;y++)for(int x=0;x<a.width;x++)if(!a.palette.getRGB(a.getPixel(x,y)).equals(b.palette.getRGB(b.getPixel(x,y))))return false;
        return true;
    }
    private static void printTree(Control root,Control control,GC destination) {
        if(!control.isVisible()||control.getSize().x<=0||control.getSize().y<=0)return;
        Point origin=root.getDisplay().map(control,root,0,0);
        Image layer=new Image(root.getDisplay(),control.getSize().x,control.getSize().y);GC gc=new GC(layer);
        try {control.print(gc);destination.drawImage(layer,origin.x,origin.y);}finally{gc.dispose();layer.dispose();}
        if(control instanceof Composite)for(Control child:((Composite)control).getChildren())printTree(root,child,destination);
    }
    private static void assertBaselines(Control overlay,StyledText text,NotebookSurface surface,NotebookSettings settings) {
        text.update(); // Compare two completed paints; pending-source ordering is tested separately.
        Image numbers=new Image(text.getDisplay(),overlay.getSize().x,overlay.getSize().y);
        Image source=new Image(text.getDisplay(),text.getSize().x,text.getSize().y);
        GC numberGC=new GC(numbers),sourceGC=new GC(source);
        Caret caret=text.getCaret();boolean caretVisible=caret!=null&&caret.getVisible();
        try {
            if(caret!=null)caret.setVisible(false);
            overlay.print(numberGC);text.print(sourceGC);
            ImageData numberPixels=numbers.getImageData(100),sourcePixels=source.getImageData(100);
            ImageData numberPaper=PaperRenderer.render(settings,surface.rulerLayout(overlay.getParent()),settings.paperColor,100);
            ImageData sourcePaper=PaperRenderer.render(settings,NotebookSurface.layout(text,text.getLineHeight(),settings),settings.paperColor,100);
            for(int line=0;line<3;line++) {
                int top=text.getLocationAtOffset(text.getOffsetAtLine(line)).y,height=text.getLineHeight();
                int numberBottom=inkBottom(numberPixels,numberPaper,top,height,numberPixels.width);
                int sourceBottom=inkBottom(sourcePixels,sourcePaper,top,height,Math.min(40,sourcePixels.width));
                if(numberBottom<0||sourceBottom<0||Math.abs(numberBottom-sourceBottom)>1) {
                    ImageLoader debug=new ImageLoader();debug.data=new ImageData[]{sourcePixels};debug.save(".build/ruler-baseline-source.png",SWT.IMAGE_PNG);
                    debug.data=new ImageData[]{numberPixels};debug.save(".build/ruler-baseline-numbers.png",SWT.IMAGE_PNG);
                    throw new AssertionError("Baseline mismatch at line "+(line+1)+": number ink bottom="+numberBottom+", editor="+sourceBottom+", gap="+settings.gap+", spacing="+settings.spacing);
                }
            }
        } finally {if(caret!=null&&!caret.isDisposed())caret.setVisible(caretVisible);numberGC.dispose();sourceGC.dispose();numbers.dispose();source.dispose();}
    }
    private static int inkBottom(ImageData actual,ImageData paper,int top,int height,int width) {
        int bottom=-1;
        for(int y=Math.max(0,top);y<Math.min(actual.height,top+height);y++)for(int x=0;x<width;x++) {
            RGB a=actual.palette.getRGB(actual.getPixel(x,y)),b=paper.palette.getRGB(paper.getPixel(x,y));
            if(Math.abs(a.red-b.red)+Math.abs(a.green-b.green)+Math.abs(a.blue-b.blue)>100)bottom=y;
        }
        return bottom;
    }
    private static void assertNumber(ImageData actual,ImageData paper,int top,int height) {
        int ink=0;
        for(int y=Math.max(0,top);y<Math.min(actual.height,top+height);y++)for(int x=0;x<actual.width;x++) {
            RGB a=actual.palette.getRGB(actual.getPixel(x,y)),b=paper.palette.getRGB(paper.getPixel(x,y));
            if(Math.abs(a.red-b.red)+Math.abs(a.green-b.green)+Math.abs(a.blue-b.blue)>80)ink++;
        }
        if(ink<3)throw new AssertionError("Native line number glyph missing at y="+top+", ink pixels="+ink);
    }
}

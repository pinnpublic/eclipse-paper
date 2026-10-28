package dev.eclipseline;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

/** No print/capture: gutters must already match when the source's paint callback finishes. */
public final class SynchronousScrollTest {
    public static void main(String[] args)throws Exception {
        Display display=new Display();Shell shell=new Shell(display);shell.setLayout(new FillLayout());
        ISharedTextColors colors=new ISharedTextColors() {
            final java.util.Map<RGB,Color> owned=new java.util.HashMap<>();
            public Color getColor(RGB rgb){return owned.computeIfAbsent(rgb,value->new Color(display,value));}
            public void dispose(){for(Color color:owned.values())color.dispose();}
        };
        CompositeRuler ruler=new CompositeRuler();LineNumberChangeRulerColumn column=new LineNumberChangeRulerColumn(colors);ruler.addDecorator(0,column);
        SourceViewer viewer=new SourceViewer(shell,ruler,SWT.V_SCROLL|SWT.H_SCROLL);
        viewer.setDocument(new Document(("// 한글 주석과 긴 줄 "+"wrapped source ".repeat(16)+"\n\n").repeat(80)));
        StyledText text=viewer.getTextWidget();text.setWordWrap(true);
        Font font=new Font(display,"Consolas",32,SWT.NORMAL);text.setFont(font);column.setFont(font);
        NotebookSettings settings=new NotebookSettings(true,true,false,0,1,4,5,30,100,38,22,14,40,new RGB(112,148,173),new RGB(247,244,233),new RGB(137,159,171));
        NotebookSurface surface=new NotebookSurface(text,()->settings,true);surface.attachRuler(column,viewer);
        shell.setSize(850,650);shell.open();pump(display,250);
        boolean[] armed={false};String[] failure={null};int[] paints={0};
        text.addListener(SWT.Paint,event->{
            if(!armed[0])return;paints[0]++;
            try {
                Map<?,?> gutters=(Map<?,?>)field(surface,"rulers");
                for(Map.Entry<?,?> entry:gutters.entrySet()) {
                    PaperRenderer.Layout expected=surface.rulerLayout((Control)entry.getKey());
                    PaperRenderer.Layout prepared=(PaperRenderer.Layout)field(surface,"lastLayout");
                    Point shift=display.map(text,(Control)entry.getKey(),0,0);
                    double[] sourceRules=prepared.rules.clone();for(int i=0;i<sourceRules.length;i++)sourceRules[i]+=shift.y;
                    PaperRenderer.Layout current=NotebookSurface.layout(text,text.getLineHeight(),settings)
                        .forRuler(expected.width,expected.height,shift.x,shift.y);
                    if(!Arrays.equals(sourceRules,current.rules))failure[0]="source paint used geometry older than the final viewport";
                    PaperRenderer.Layout actual=(PaperRenderer.Layout)field(entry.getValue(),"lastLayout");
                    if(actual==null||!Arrays.equals(expected.rules,actual.rules))failure[0]="source paint ended at top="+text.getTopPixel()+" while gutter retained another rule phase; expected="+Arrays.toString(expected.rules)+"; actual="+(actual==null?"null":Arrays.toString(actual.rules));
                }
            }catch(Exception error){failure[0]=error.toString();}
        });
        try {
            armed[0]=true;
            for(int top:new int[]{641,659,689,777,1025,1001,917,801,641}) {
                text.setTopPixel(top);text.redraw();text.update();
                if(failure[0]!=null)throw new AssertionError(failure[0]);
                pump(display,40);
            }
            if(paints[0]<9)throw new AssertionError("Source paint path was not exercised");
            System.out.println("PASS: "+paints[0]+" source paint completions already have synchronized gutter rules; wrapped Korean source, 32pt, partial pixel scrolling; no queued repair or print capture");
        }finally{surface.close();shell.dispose();font.dispose();colors.dispose();display.dispose();}
    }
    private static Object field(Object target,String name)throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void pump(Display display,int millis)throws Exception {long end=System.currentTimeMillis()+millis;while(System.currentTimeMillis()<end){if(!display.readAndDispatch())Thread.sleep(2);}}
}

package dev.eclipseline;

import java.lang.reflect.Method;
import java.util.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/** Ownership and pixel-equivalence regressions; no new notebook features. */
public final class ReviewRegressionTest {
    public static void main(String[] args)throws Exception {
        Method composite=RulerPaper.class.getDeclaredMethod("composite",ImageData.class,ImageData.class);composite.setAccessible(true);
        for(boolean indexed:new boolean[]{false,true}) {
            RGB[] colors={new RGB(240,240,240),new RGB(20,30,40),new RGB(200,50,60),new RGB(240,240,240)};
            PaletteData palette=indexed?new PaletteData(colors):new PaletteData(0xff0000,0xff00,0xff);
            ImageData nativeData=new ImageData(37,73,indexed?8:32,palette);
            ImageData paper=new ImageData(37,73,32,new PaletteData(0xff0000,0xff00,0xff));
            Random random=new Random(15);
            for(int y=0;y<nativeData.height;y++)for(int x=0;x<nativeData.width;x++) {
                int n=(x+y)%7==0?1:((x+y)%13==0?2:((x+y)%5==0?3:0));
                nativeData.setPixel(x,y,indexed?n:palette.getPixel(colors[n]));
                paper.setPixel(x,y,random.nextInt(0xffffff));
            }
            ImageData expected=(ImageData)paper.clone();reference(nativeData,expected);composite.invoke(null,nativeData,paper);
            if(!Arrays.equals(expected.data,paper.data))throw new AssertionError("Compositing changes native foreground/paper pixels; indexed="+indexed);
        }
        System.out.println("PASS: compositing preserves exact pixels for direct/indexed palettes and duplicate palette colors");
        Display display=new Display();Shell shell=new Shell(display);Composite host=new Composite(shell,SWT.NONE);host.setBounds(0,0,100,100);StyledText text=new StyledText(host,SWT.NONE);text.setBounds(10,0,80,80);
        Canvas side=new Canvas(host,SWT.NONE);side.setBounds(90,0,10,80);
        Color originalSide=display.getSystemColor(SWT.COLOR_RED);side.setBackground(originalSide);
        Color darkChrome=new Color(display,47,47,47);
        Image original=new Image(display,1,1);host.setBackgroundImage(original);NotebookPadding padding=new NotebookPadding(host,text);
        try {
            NotebookSettings settings=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,0,40,new RGB(112,148,173),new RGB(249,247,237),new RGB(137,159,171));
            padding.paper(settings,new PaperRenderer.Layout(80,80,16,20,0,19,new double[]{19,39,59,79}),settings.paperColor);
            if(!side.getBackground().equals(shell.getBackground())||side.getBackgroundImage()!=null)
                throw new AssertionError("Overview inherits paper instead of solid theme chrome");
            shell.setBackground(darkChrome);
            padding.paper(settings,new PaperRenderer.Layout(80,80,16,20,0,19,new double[]{19,39,59,79}),settings.paperColor);
            if(!side.getBackground().equals(darkChrome))throw new AssertionError("Overview does not follow theme changes");
            padding.clear();if(host.getBackgroundImage()!=original)throw new AssertionError("Original background not restored");
            if(!side.getBackground().equals(originalSide))throw new AssertionError("Original overview background not restored");
            side.setBackground(display.getSystemColor(SWT.COLOR_BLUE));
            host.setBackgroundImage(null);padding.clear();
            if(host.getBackgroundImage()!=null)throw new AssertionError("Repeated clear overwrites a background changed after decoration stopped");
            if(!side.getBackground().equals(display.getSystemColor(SWT.COLOR_BLUE)))throw new AssertionError("Repeated cleanup overwrites an externally changed overview");
            System.out.println("PASS: overview uses solid theme chrome, follows theme changes and restores its original background");
            System.out.println("PASS: repeated cleanup leaves externally cleared backgrounds unchanged");
        }finally{padding.close();shell.dispose();original.dispose();darkChrome.dispose();display.dispose();}
        ImageData nativeData=new ImageData(32,600,32,new PaletteData(0xff0000,0xff00,0xff)),paper=new ImageData(32,600,32,new PaletteData(0xff0000,0xff00,0xff));
        for(int y=0;y<600;y++)for(int x=0;x<32;x++)nativeData.setPixel(x,y,x==10&&y%20<8?0x112233:0xeeeeee);
        com.sun.management.ThreadMXBean allocations=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        if(!allocations.isThreadAllocatedMemorySupported()){System.out.println("SKIP: JVM allocation tracking unavailable");return;}
        allocations.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        for(int i=0;i<30;i++){reference(nativeData,paper);composite.invoke(null,nativeData,paper);}
        long before=allocations.getThreadAllocatedBytes(thread);
        for(int i=0;i<10;i++)reference(nativeData,paper);
        long originalBytes=allocations.getThreadAllocatedBytes(thread)-before;
        before=allocations.getThreadAllocatedBytes(thread);
        for(int i=0;i<10;i++)composite.invoke(null,nativeData,paper);
        long optimizedBytes=allocations.getThreadAllocatedBytes(thread)-before;
        if(optimizedBytes>originalBytes/3)throw new AssertionError("Excessive gutter allocation: "+optimizedBytes+" vs original "+originalBytes);
        System.out.println("PASS: 10 gutter composites allocate "+optimizedBytes+" bytes vs original "+originalBytes+" bytes; identical pixels");
    }
    private static void reference(ImageData nativeData,ImageData paper) {
        Map<Integer,Integer> histogram=new HashMap<>();
        for(int y=0;y<nativeData.height;y++)for(int x=0;x<nativeData.width;x++){RGB c=nativeData.palette.getRGB(nativeData.getPixel(x,y));histogram.merge(c.red<<16|c.green<<8|c.blue,1,Integer::sum);}
        int background=histogram.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        for(int y=0;y<nativeData.height;y++)for(int x=0;x<nativeData.width;x++){RGB c=nativeData.palette.getRGB(nativeData.getPixel(x,y));if((c.red<<16|c.green<<8|c.blue)!=background)paper.setPixel(x,y,paper.palette.getPixel(c));}
    }
}

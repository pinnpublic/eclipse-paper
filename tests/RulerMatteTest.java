package dev.eclipseline;

import java.lang.reflect.Method;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;

/** Remattes native antialias pixels without changing the glyph or the annotation renderer. */
public final class RulerMatteTest {
    public static void main(String[] args)throws Exception {
        Method composite=RulerPaper.class.getDeclaredMethod("composite",ImageData.class,ImageData.class,RGB.class);composite.setAccessible(true);
        for(boolean dark:new boolean[]{false,true})for(int zoom:new int[]{100,150,200}) {
            RGB base=dark?new RGB(32,32,32):new RGB(255,255,255),ink=dark?new RGB(180,180,180):new RGB(112,112,112);
            int width=12*zoom/100;ImageData nativeData=data(width,20),paper=data(width,20);
            for(int y=0;y<20;y++)for(int x=0;x<width;x++) {
                double alpha=y<4?0:y==4?1:(y-5)/20.0;
                RGB c=mix(ink,base,alpha);nativeData.setPixel(x,y,nativeData.palette.getPixel(c));
                RGB under=dark?new RGB(47+x%3,49+y%5,57+x%4):new RGB(219+x%3,201+y%5,159+x%4);
                paper.setPixel(x,y,paper.palette.getPixel(under));
            }
            ImageData original=(ImageData)paper.clone();composite.invoke(null,nativeData,paper,ink);
            for(int y=0;y<20;y++)for(int x=0;x<width;x++) {
                double alpha=y<4?0:y==4?1:(y-5)/20.0;
                RGB under=original.palette.getRGB(original.getPixel(x,y));
                RGB expected=mix(ink,under,alpha),actual=paper.palette.getRGB(paper.getPixel(x,y));
                if(Math.abs(expected.red-actual.red)>2||Math.abs(expected.green-actual.green)>2||Math.abs(expected.blue-actual.blue)>2)
                    throw new AssertionError("Matte fringe: dark="+dark+", zoom="+zoom+", y="+y+", actual="+actual+", expected="+expected);
            }
        }
        System.out.println("PASS: antialias edges blend with light/dark textured paper at 100/150/200 percent; opaque ink and empty paper preserved");
        Display display=new Display();
        try {
            for(boolean dark:new boolean[]{false,true}) {
                Image nativeImage=new Image(display,100,430);GC gc=new GC(nativeImage);Font font=new Font(display,"Consolas",22,SWT.NORMAL);
                Color ink=new Color(display,dark?new RGB(180,180,180):new RGB(112,112,112));
                try {
                    gc.setBackground(display.getSystemColor(dark?SWT.COLOR_BLACK:SWT.COLOR_WHITE));gc.fillRectangle(0,0,100,430);gc.setForeground(ink);gc.setFont(font);gc.setTextAntialias(SWT.ON);
                    for(int i=1;i<=12;i++)gc.drawString(String.valueOf(i),20,(i-1)*35,true);
                    RGB bg=dark?new RGB(45,49,57):new RGB(224,207,161);
                    NotebookSettings settings=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,4,65,new RGB(112,148,173),bg,new RGB(137,159,171));
                    ImageData paper=PaperRenderer.render(settings,new PaperRenderer.Layout(100,430,24,35,0,34,new double[]{34,69,104,139,174,209,244,279,314,349,384,419}),bg,100);
                    ImageData old=(ImageData)paper.clone(),next=(ImageData)paper.clone();ImageData nativePixels=nativeImage.getImageData(100);
                    composite.invoke(null,nativePixels,old,null);composite.invoke(null,nativePixels,next,ink.getRGB());
                    int changed=0;for(int y=0;y<430;y++)for(int x=0;x<100;x++)if(old.getPixel(x,y)!=next.getPixel(x,y))changed++;
                    if(changed<20)throw new AssertionError("Native glyph fringe was not updated; dark="+dark);
                    ImageData comparison=data(200,430);int[] row=new int[100];
                    for(int y=0;y<430;y++){old.getPixels(0,y,100,row,0);comparison.setPixels(0,y,100,row,0);next.getPixels(0,y,100,row,0);comparison.setPixels(100,y,100,row,0);}
                    ImageLoader loader=new ImageLoader();loader.data=new ImageData[]{comparison};loader.save(args[0]+(dark?"-dark.png":"-light.png"),SWT.IMAGE_PNG);
                    System.out.println("PASS: native "+(dark?"dark":"light")+" digits retain geometry; "+changed+" fringe pixels rematted; comparison left=old/right=new");
                }finally{gc.dispose();nativeImage.dispose();font.dispose();ink.dispose();}
            }
        }finally{display.dispose();}
    }
    private static ImageData data(int width,int height){return new ImageData(width,height,32,new PaletteData(0xff0000,0xff00,0xff));}
    private static RGB mix(RGB a,RGB b,double alpha){return new RGB((int)Math.round(a.red*alpha+b.red*(1-alpha)),(int)Math.round(a.green*alpha+b.green*(1-alpha)),(int)Math.round(a.blue*alpha+b.blue*(1-alpha)));}
}

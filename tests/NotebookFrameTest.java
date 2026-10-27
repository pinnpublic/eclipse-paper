package dev.eclipseline;

import org.eclipse.swt.graphics.*;

/** Empty scrollbar surroundings must be solid chrome; paper must never tile into them. */
public final class NotebookFrameTest {
    public static void main(String[] args) {
        int checks=0;
        for(boolean dark:new boolean[]{false,true})for(int zoom:new int[]{100,150,200}) {
            RGB paper=dark?new RGB(36,40,46):new RGB(224,207,161),chrome=dark?new RGB(48,48,48):new RGB(240,240,240);
            NotebookSettings settings=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,17,90,
                new RGB(112,148,173),paper,new RGB(137,159,171));
            PaperRenderer.Layout layout=new PaperRenderer.Layout(220,160,16,24,0,36,new double[]{36,60,84,108,132,156});
            Rectangle page=new Rectangle(0,0,184,136);
            ImageData actual=NotebookPadding.backdrop(settings,layout,paper,chrome,page,zoom),expected=PaperRenderer.render(settings,layout,paper,zoom);
            int right=(int)Math.round(page.width*zoom/100.0),bottom=(int)Math.round(page.height*zoom/100.0);
            for(int y=0;y<actual.height;y++)for(int x=0;x<actual.width;x++) {
                RGB value=actual.palette.getRGB(actual.getPixel(x,y));
                RGB target=x<right&&y<bottom?expected.palette.getRGB(expected.getPixel(x,y)):chrome;
                if(!value.equals(target))throw new AssertionError("Frame/paper seam at "+x+","+y+" dark="+dark+" zoom="+zoom);
            }
            checks++;
        }
        System.out.println("PASS: ALL "+checks+" complete light/dark frame images at native 100/150/200 percent");
    }
}

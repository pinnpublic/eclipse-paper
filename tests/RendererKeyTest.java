package dev.eclipseline;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;

/** Canonical cache keys must describe exactly the same visible raster as the original layout. */
public final class RendererKeyTest {
    public static void main(String[] args) {
        RGB background=new RGB(247,244,233),ink=new RGB(112,148,173),pattern=new RGB(137,159,171);
        int checks=0;
        for(int paper=0;paper<12;paper++)for(int style=0;style<10;style++)for(int zoom:new int[]{100,125,150,200})for(int mode=0;mode<3;mode++) {
            NotebookSettings settings=new NotebookSettings(mode!=2,mode!=1,false,style,paper,4,15,30,100,38,22,mode==0?0:14,40,ink,background,pattern);
            for(int highlight=0;highlight<3;highlight++) {
                PaperRenderer.Highlight h=highlight==0?null:new PaperRenderer.Highlight(highlight==1?41:-401,highlight==1?83:-359,new RGB(93,127,157));
                PaperRenderer.Layout layout=new PaperRenderer.Layout(97,89,19,25,13,-473,new double[]{-12.3,12.7,37.7,62.7,87.7,112.7})
                    .withGeometry(53.3,101.6,h).forRuler(97,89,-11,7);
                equal(PaperRenderer.renderBackground(settings,layout,background,zoom),PaperRenderer.renderBackground(settings,layout.backgroundKey(settings),background,zoom),paper,style,zoom,"background");
                equal(PaperRenderer.renderRules(settings,layout,background,zoom),PaperRenderer.renderRules(settings,layout.rulesKey(settings),background,zoom),paper,style,zoom,"rules");
                equal(PaperRenderer.render(settings,layout,background,zoom),PaperRenderer.render(settings,layout.fullKey(settings),background,zoom),paper,style,zoom,"full");
                checks+=3;
            }
        }
        NotebookSettings settings=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,0,40,ink,background,pattern);
        PaperRenderer.Layout a=new PaperRenderer.Layout(97,89,19,25,13,-473,new double[]{12.7,37.7,62.7,87.7});
        PaperRenderer.Layout b=new PaperRenderer.Layout(97,89,19,25,13,-498,new double[]{12.7,37.7,62.7,87.7});
        if(!a.backgroundKey(settings).equals(b.backgroundKey(settings))||!a.rulesKey(settings).equals(b.rulesKey(settings))||!a.fullKey(settings).equals(b.fullKey(settings)))
            throw new AssertionError("Whole-row scrolling invalidates unchanged notebook rasters");
        System.out.println("PASS: "+checks+" original/canonical raster comparisons; whole-row scrolling retains all notebook layer keys");
    }
    private static void equal(ImageData actual,ImageData expected,int paper,int style,int zoom,String layer) {
        if(actual.width!=expected.width||actual.height!=expected.height)throw new AssertionError("Raster size changed");
        for(int y=0;y<actual.height;y++)for(int x=0;x<actual.width;x++) {
            if(actual.getPixel(x,y)!=expected.getPixel(x,y)||actual.getAlpha(x,y)!=expected.getAlpha(x,y))
                throw new AssertionError("Canonical "+layer+" differs at "+x+","+y+" paper="+paper+" style="+style+" zoom="+zoom);
        }
    }
}

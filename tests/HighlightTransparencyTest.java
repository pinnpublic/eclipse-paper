import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import dev.eclipseline.NotebookSettings;
import dev.eclipseline.PaperRenderer;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;

/** Focused regression checks for translucency, patterns, texture and unchanged rule geometry. */
public final class HighlightTransparencyTest {
    private static int checks;
    public static void main(String[] args) {
        RGB paper = new RGB(245,237,219), blue = new RGB(215,233,251);
        for (int zoom : new int[] {100,125,150,175,200,300}) {
            for (int texture : new int[] {0,6,8,12,17,21,31,35}) {
                NotebookSettings s = new NotebookSettings(true,true,false,0,1,4,15,30,100,38,70,
                    texture,65,new RGB(112,148,173),paper,new RGB(137,159,171));
                PaperRenderer.Layout base = new PaperRenderer.Layout(200,100,18,24,0,23,new double[] {23,47,71,95})
                    .withGeometry(40,80,null);
                PaperRenderer.Layout highlighted = base.withGeometry(40,80,new PaperRenderer.Highlight(23,47,blue));
                ImageData before = PaperRenderer.renderBackground(s,base,paper,zoom);
                ImageData after = PaperRenderer.renderBackground(s,highlighted,paper,zoom);
                int x = 120*zoom/100, y = 35*zoom/100;
                RGB old = rgb(before,x,y), mixed = rgb(after,x,y);
                check(!mixed.equals(blue) && !mixed.equals(old), "highlight is translucent: " + zoom + "% texture " + texture);
                check(close(mixed.red,mix(old.red,blue.red)) && close(mixed.green,mix(old.green,blue.green))
                    && close(mixed.blue,mix(old.blue,blue.blue)), "50 percent alpha composition: " + zoom + "% texture " + texture);
                check(after.getPixel(x,0)==before.getPixel(x,0) && after.getPixel(x,80*zoom/100)==before.getPixel(x,80*zoom/100),
                    "pixels outside highlight remain unchanged");
                int marginX=35*zoom/100;
                int beside=after.getPixel(marginX+5*zoom/100,y);
                boolean marginVisible=false;
                for(int xx=marginX-1;xx<=marginX+1;xx++) marginVisible|=after.getPixel(xx,y)!=beside;
                check(marginVisible, "notebook vertical margin remains visible through highlight");
                ImageData rulesBefore=PaperRenderer.renderRules(s,base,paper,zoom);
                ImageData rulesAfter=PaperRenderer.renderRules(s,highlighted,paper,zoom);
                check(Arrays.equals(rulesBefore.data,rulesAfter.data) && Arrays.equals(rulesBefore.alphaData,rulesAfter.alphaData),
                    "rule color, sharpness and positions unchanged");
                if (texture!=0) {
                    Set<Integer> shades=new HashSet<>();
                    for (int yy=29*zoom/100;yy<42*zoom/100;yy++)
                        for (int xx=80*zoom/100;xx<160*zoom/100;xx++) shades.add(after.getPixel(xx,yy));
                    check(shades.size()>2,"paper material remains visible through highlight");
                }
                int end=-1,ruleEnd=-1;
                for (int yy=42*zoom/100;yy<53*zoom/100;yy++) {
                    if(after.getPixel(x,yy)!=before.getPixel(x,yy))end=yy;
                    if(rulesAfter.getAlpha(x,yy)==255)ruleEnd=yy;
                }
                check(end==ruleEnd && end>=0,"highlight and rule still end at the same pixel edge");
            }
        }
        for(int opacity:new int[]{0,25,50,75,100}) {
            NotebookSettings s=new NotebookSettings(true,false,false,0,0,4,15,30,100,38,22,0,0,
                new RGB(112,148,173),paper,new RGB(137,159,171),opacity);
            PaperRenderer.Layout l=new PaperRenderer.Layout(160,80,18,24,0,23,new double[0])
                .withGeometry(40,80,new PaperRenderer.Highlight(23,47,blue));
            RGB mixed=rgb(PaperRenderer.renderBackground(s,l,paper,100),80,35);
            int a=(int)Math.round(opacity*255/100.0);
            check(close(mixed.red,(paper.red*(255-a)+blue.red*a+127)/255)
                && close(mixed.green,(paper.green*(255-a)+blue.green*a+127)/255)
                && close(mixed.blue,(paper.blue*(255-a)+blue.blue*a+127)/255),
                "configured opacity "+opacity+" applies even with notebook rules hidden");
            org.eclipse.jface.preference.PreferenceStore preferences=new org.eclipse.jface.preference.PreferenceStore();
            NotebookSettings.defaults(preferences);s.write(preferences);
            check(NotebookSettings.read(preferences).highlightOpacity==opacity,"configured opacity saves and reloads: "+opacity);
        }
        System.out.println("PASS: ALL " + checks + " focused transparency checks");
    }
    private static RGB rgb(ImageData data,int x,int y){return data.palette.getRGB(data.getPixel(x,y));}
    private static int mix(int paper,int ink){return (int)Math.round((paper*127+ink*128)/255.0);}
    private static boolean close(int a,int b){return Math.abs(a-b)<=1;}
    private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
}

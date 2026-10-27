import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import dev.eclipseline.NotebookSettings;
import dev.eclipseline.PaperRenderer;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;

public final class TextureCatalogTest {
    private static int checks;
    private static final RGB SAME_PAPER=new RGB(237,229,211);
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[0]);Files.createDirectories(output);
        check(NotebookSettings.TEXTURE_NAMES.length==36,"35 paper materials plus none");
        check(NotebookSettings.TEXTURE_COLORS.length==36,"all materials have a color preset");
        String[] legacy={"없음","매끈한 백상지","고운 미색지","거친 종이","재생지","크라프트지","한지","수채화지","린넨지","양피지","코튼지","신문지"};
        check(Arrays.equals(legacy,Arrays.copyOf(NotebookSettings.TEXTURE_NAMES,12)),"all existing saved material IDs preserved");
        PreferenceStore store=new PreferenceStore();NotebookSettings.defaults(store);
        store.setValue(NotebookSettings.PREFIX+"texture",11);
        check(NotebookSettings.read(store).texture==11,"previously saved newsprint selection stays newsprint");
        settings(35,65,SAME_PAPER).write(store);
        check(NotebookSettings.read(store).texture==35,"new final material saves and reloads");
        Set<Integer> hashes=new HashSet<>();int crumpledHash=0;
        for(int type=0;type<36;type++) {
            NotebookSettings s=settings(type,65,SAME_PAPER);
            PaperRenderer.Layout small=layout(520,420,12),large=layout(520,420,48);
            ImageData data=PaperRenderer.renderBackground(s,small,SAME_PAPER,100);
            hashes.add(Arrays.hashCode(data.data));if(type==12)crumpledHash=Arrays.hashCode(data.data);
            check(Arrays.equals(data.data,PaperRenderer.renderBackground(s,large,SAME_PAPER,100).data),
                "font zoom does not scale material "+type);
            ImageData flat=PaperRenderer.renderBackground(settings(type,0,SAME_PAPER),small,SAME_PAPER,100);
            int plain=flat.getPixel(0,0);boolean uniform=true;
            for(int y=0;y<flat.height;y+=7)for(int x=0;x<flat.width;x+=7)uniform&=flat.getPixel(x,y)==plain;
            check(uniform,"zero strength removes material "+type);
            boolean repeat=true;
            for(int y=0;y<384;y+=3)repeat&=data.getPixel(5,y)==data.getPixel(389,y);
            for(int x=0;x<384;x+=3)repeat&=data.getPixel(x,7)==data.getPixel(x,391);
            check(repeat,"material "+type+" repeats consistently in both directions");
            RGB preset=NotebookSettings.TEXTURE_COLORS[type];
            ImageData preview=PaperRenderer.renderBackground(settings(type,65,preset),layout(520,300,18),preset,100);
            ImageIO.write(toImage(preview),"png",output.resolve(String.format("paper-%02d.png",type)).toFile());
        }
        check(hashes.size()==36,"all 36 choices are distinct on the same paper color");
        ImageData repeat=PaperRenderer.renderBackground(settings(12,65,SAME_PAPER),layout(520,420,12),SAME_PAPER,100);
        check(Arrays.hashCode(repeat.data)==crumpledHash,"regenerated crumple remains deterministic after cache eviction");
        gallery(output,12,36,"new-paper-catalog.png");
        gallery(output,0,36,"all-paper-catalog.png");
        System.out.println("PASS: ALL "+checks+" paper catalog checks");
    }
    private static NotebookSettings settings(int type,int strength,RGB color) {
        return new NotebookSettings(true,false,false,0,0,4,15,30,100,38,22,type,strength,
            new RGB(112,148,173),color,new RGB(137,159,171));
    }
    private static PaperRenderer.Layout layout(int w,int h,int font){return new PaperRenderer.Layout(w,h,font,font+6,0,0,new double[0]);}
    private static BufferedImage toImage(ImageData data) {
        BufferedImage image=new BufferedImage(data.width,data.height,BufferedImage.TYPE_INT_RGB);
        int[] row=new int[data.width];
        for(int y=0;y<data.height;y++){data.getPixels(0,y,data.width,row,0);image.setRGB(0,y,data.width,1,row,0,data.width);}
        return image;
    }
    private static void gallery(Path output,int first,int end,String filename)throws Exception {
        int cols=4,cw=280,ch=190,rows=(end-first+cols-1)/cols;
        BufferedImage sheet=new BufferedImage(cols*cw,rows*ch,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=sheet.createGraphics();
        try {
            g.setColor(new Color(237,239,240));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            g.setFont(new Font("Malgun Gothic",Font.PLAIN,15));
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for(int type=first;type<end;type++) {
                int x=((type-first)%cols)*cw,y=((type-first)/cols)*ch;
                RGB color=NotebookSettings.TEXTURE_COLORS[type];
                ImageData sample=PaperRenderer.renderBackground(settings(type,65,color),layout(cw-12,145,18),color,100);
                g.drawImage(toImage(sample),x+6,y+6,null);
                g.setColor(new Color(32,36,39));g.drawString(String.format("%02d  %s",type,NotebookSettings.TEXTURE_NAMES[type]),x+8,y+175);
            }
        } finally {g.dispose();}
        ImageIO.write(sheet,"png",output.resolve(filename).toFile());
    }
    private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
}

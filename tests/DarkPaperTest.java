import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import dev.eclipseline.NotebookSettings;
import dev.eclipseline.PaperRenderer;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.SWT;

/** Profile migration, independent persistence, unchanged geometry, and all dark paper materials. */
public final class DarkPaperTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        PreferenceStore preferences=new PreferenceStore();NotebookSettings.defaults(preferences);
        RGB oldLight=new RGB(220,235,245);
        PreferenceConverter.setValue(preferences,NotebookSettings.PREFIX+"paperColor",oldLight);
        NotebookSettings migrated=NotebookSettings.read(preferences);
        check(migrated.paperColor.equals(oldLight),"legacy light color preserved");
        check(migrated.darkPaperColor.equals(NotebookSettings.darkPaper(oldLight)),"legacy color gets matching dark counterpart");
        check(preferences.getString(NotebookSettings.PREFIX+"darkPaperColor").isEmpty(),"reading does not overwrite old preferences");
        RGB customDark=new RGB(29,34,43);
        NotebookSettings custom=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,12,65,
            migrated.lineColor,oldLight,migrated.patternColor,50,customDark);
        custom.write(preferences);NotebookSettings restored=NotebookSettings.read(preferences);
        check(restored.paperColor.equals(oldLight)&&restored.darkPaperColor.equals(customDark),"light/dark choices persist independently");
        check(restored.paperColor(false).equals(oldLight)&&restored.paperColor(true).equals(customDark),"profile resolution chooses intended background");
        check(NotebookSettings.read(preferences,true).darkPaperColor.equals(NotebookSettings.darkPaper(new RGB(250,247,237))),"defaults restore dark counterpart");
        Path output=Path.of(args[0]);Files.createDirectories(output);
        for(int texture=0;texture<NotebookSettings.TEXTURE_NAMES.length;texture++) {
            RGB light=NotebookSettings.TEXTURE_COLORS[texture],dark=NotebookSettings.darkPaper(light);
            NotebookSettings settings=new NotebookSettings(true,true,false,0,1,4,15,30,100,38,22,texture,65,
                migrated.lineColor,light,migrated.patternColor);
            PaperRenderer.Layout layout=new PaperRenderer.Layout(192,128,16,24,0,20,new double[]{20,44,68,92,116});
            ImageData pixels=PaperRenderer.render(settings,layout,dark,100);
            long sum=0;
            for(int y=0;y<pixels.height;y++)for(int x=0;x<pixels.width;x++) {
                RGB color=pixels.palette.getRGB(pixels.getPixel(x,y));sum+=color.red+color.green+color.blue;
            }
            check(sum/(pixels.width*pixels.height*3.0)<100,"dark texture remains dark: "+texture);
            ImageLoader loader=new ImageLoader();loader.data=new ImageData[]{pixels};loader.save(output.resolve("dark-texture-"+texture+".png").toString(),SWT.IMAGE_PNG);
            if(texture==12||texture==17||texture==35) {
                loader.data=new ImageData[]{PaperRenderer.render(settings,layout,light,100)};
                loader.save(output.resolve("light-texture-"+texture+".png").toString(),SWT.IMAGE_PNG);
            }
        }
        for(int style=0;style<NotebookSettings.LINE_NAMES.length;style++)for(int pattern=0;pattern<NotebookSettings.PAPER_NAMES.length;pattern++) {
            NotebookSettings settings=new NotebookSettings(true,true,false,style,pattern,4,15,30,100,38,22,0,0,
                migrated.lineColor,oldLight,migrated.patternColor);
            PaperRenderer.Layout layout=new PaperRenderer.Layout(128,80,16,24,0,20,new double[]{20,44,68});
            ImageData lightRules=PaperRenderer.renderRules(settings,layout,oldLight,100),darkRules=PaperRenderer.renderRules(settings,layout,settings.darkPaperColor,100);
            check(Arrays.equals(lightRules.alphaData,darkRules.alphaData),"light/dark keep rule geometry: "+style+"/"+pattern);
            check(settings.lineColor.equals(migrated.lineColor)&&settings.patternColor.equals(migrated.patternColor),"profile leaves line/pattern colors unchanged");
        }
        System.out.println("PASS: ALL "+checks+" dark paper profile checks");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}

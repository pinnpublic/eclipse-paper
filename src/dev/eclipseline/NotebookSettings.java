package dev.eclipseline;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.swt.graphics.RGB;

/** Immutable, validated settings. Dimensions are percentages of the actual font height. */
public final class NotebookSettings {
    public static final String PREFIX = "notebook.";
    public static final String[] LINE_NAMES = {
        "실선", "가는 점선", "둥근 점선", "짧은 파선", "긴 파선",
        "일점쇄선", "이점쇄선", "이중선", "삼중선", "물결선"
    };
    public static final String[] PAPER_NAMES = {
        "단색", "공책", "모눈", "촘촘한 모눈", "공학용 모눈", "점 격자",
        "십자 격자", "세로줄", "등각 격자", "마름모 격자", "원고지", "코넬 노트"
    };
    public static final String[] TEXTURE_NAMES = {
        "없음", "매끈한 백상지", "고운 미색지", "거친 종이", "재생지", "크라프트지",
        "한지", "수채화지", "린넨지", "양피지", "코튼지", "신문지",
        "가볍게 구긴 종이", "거칠게 구긴 종이", "펼친 구김 종이", "사등분 접은 종이", "아코디언 접지",
        "화선지", "생화선지", "숙화선지", "닥종이", "운용지", "대나무 종이", "볏짚 종이",
        "바나나 섬유지", "로크타 수제지", "골판지", "엠보싱 종이", "레이드지", "트레이싱지", "감열지",
        "얼룩진 빈티지 종이", "대리석 마블지", "청색 도화지", "회색 스케치지", "유산지"
    };
    public static final RGB[] TEXTURE_COLORS = {
        new RGB(250,247,237), new RGB(250,248,242), new RGB(248,246,237), new RGB(244,240,227),
        new RGB(230,224,204), new RGB(199,166,119), new RGB(247,243,224), new RGB(250,249,243),
        new RGB(246,242,228), new RGB(235,219,180), new RGB(250,248,240), new RGB(224,220,204),
        new RGB(248,246,238), new RGB(241,238,227), new RGB(247,244,233), new RGB(249,246,235), new RGB(242,236,220),
        new RGB(249,247,237), new RGB(246,243,226), new RGB(251,249,242), new RGB(241,234,210), new RGB(245,242,228),
        new RGB(237,232,211), new RGB(234,221,183), new RGB(231,216,187), new RGB(237,227,200),
        new RGB(204,179,141), new RGB(247,243,230), new RGB(245,242,232), new RGB(233,237,235), new RGB(248,247,239),
        new RGB(235,216,177), new RGB(237,243,242), new RGB(202,221,233), new RGB(219,219,214), new RGB(241,242,230)
    };
    public final boolean enabled, lines, theme;
    public final int lineStyle, paper, width, gap, spacing, scale, lineOpacity, patternOpacity, texture, textureOpacity;
    public final RGB lineColor, paperColor, darkPaperColor, patternColor;
    public final int highlightOpacity;

    public NotebookSettings(boolean enabled, boolean lines, boolean theme, int lineStyle, int paper,
            int width, int gap, int scale, int lineOpacity, int patternOpacity,
            RGB lineColor, RGB paperColor, RGB patternColor) {
        this(enabled, lines, theme, lineStyle, paper, width, gap, 30, scale, lineOpacity,
            patternOpacity, 0, 40, lineColor, paperColor, patternColor);
    }

    public NotebookSettings(boolean enabled, boolean lines, boolean theme, int lineStyle, int paper,
            int width, int gap, int spacing, int scale, int lineOpacity, int patternOpacity,
            int texture, int textureOpacity, RGB lineColor, RGB paperColor, RGB patternColor) {
        this(enabled,lines,theme,lineStyle,paper,width,gap,spacing,scale,lineOpacity,patternOpacity,
            texture,textureOpacity,lineColor,paperColor,patternColor,50);
    }
    public NotebookSettings(boolean enabled, boolean lines, boolean theme, int lineStyle, int paper,
            int width, int gap, int spacing, int scale, int lineOpacity, int patternOpacity,
            int texture, int textureOpacity, RGB lineColor, RGB paperColor, RGB patternColor, int highlightOpacity) {
        this(enabled,lines,theme,lineStyle,paper,width,gap,spacing,scale,lineOpacity,patternOpacity,
            texture,textureOpacity,lineColor,paperColor,patternColor,highlightOpacity,darkPaper(paperColor));
    }
    public NotebookSettings(boolean enabled, boolean lines, boolean theme, int lineStyle, int paper,
            int width, int gap, int spacing, int scale, int lineOpacity, int patternOpacity,
            int texture, int textureOpacity, RGB lineColor, RGB paperColor, RGB patternColor, int highlightOpacity, RGB darkPaperColor) {
        this.enabled = enabled; this.lines = lines; this.theme = theme;
        this.lineStyle = clamp(lineStyle, 0, LINE_NAMES.length - 1);
        this.paper = clamp(paper, 0, PAPER_NAMES.length - 1);
        this.width = clamp(width, 1, 25); this.gap = clamp(gap, -50, 100);
        this.spacing = clamp(spacing, 0, 200);
        this.texture = clamp(texture, 0, TEXTURE_NAMES.length - 1);
        this.textureOpacity = clamp(textureOpacity, 0, 100);
        this.highlightOpacity = clamp(highlightOpacity,0,100);
        this.scale = clamp(scale, 50, 300);
        this.lineOpacity = clamp(lineOpacity, 1, 100);
        this.patternOpacity = clamp(patternOpacity, 1, 100);
        this.lineColor = lineColor; this.paperColor = paperColor; this.darkPaperColor=darkPaperColor;this.patternColor = patternColor;
    }

    public static RGB darkPaper(RGB light) {
        return new RGB((int)Math.round(light.red*.17),(int)Math.round(light.green*.17),(int)Math.round(light.blue*.17));
    }
    public RGB paperColor(boolean dark){return dark?darkPaperColor:paperColor;}
    @Override public boolean equals(Object other) {
        if(!(other instanceof NotebookSettings))return false;NotebookSettings s=(NotebookSettings)other;
        return enabled==s.enabled&&lines==s.lines&&theme==s.theme&&lineStyle==s.lineStyle&&paper==s.paper
            &&width==s.width&&gap==s.gap&&spacing==s.spacing&&scale==s.scale&&lineOpacity==s.lineOpacity
            &&patternOpacity==s.patternOpacity&&texture==s.texture&&textureOpacity==s.textureOpacity&&highlightOpacity==s.highlightOpacity
            &&lineColor.equals(s.lineColor)&&paperColor.equals(s.paperColor)&&darkPaperColor.equals(s.darkPaperColor)&&patternColor.equals(s.patternColor);
    }
    @Override public int hashCode(){return java.util.Objects.hash(enabled,lines,theme,lineStyle,paper,width,gap,spacing,scale,lineOpacity,patternOpacity,texture,textureOpacity,highlightOpacity,lineColor,paperColor,darkPaperColor,patternColor);}

    private static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }

    public static void defaults(IPreferenceStore s) {
        s.setDefault(PREFIX + "enabled", true); s.setDefault(PREFIX + "lines", true);
        s.setDefault(PREFIX + "theme", true);
        s.setDefault(PREFIX + "lineStyle", 0); s.setDefault(PREFIX + "paper", 1);
        s.setDefault(PREFIX + "width", 4); s.setDefault(PREFIX + "gap", 15);
        s.setDefault(PREFIX + "spacing", 30);
        s.setDefault(PREFIX + "texture", 0); s.setDefault(PREFIX + "textureOpacity", 40);
        s.setDefault(PREFIX + "highlightOpacity",50);
        s.setDefault(PREFIX + "scale", 100);
        s.setDefault(PREFIX + "lineOpacity", 38); s.setDefault(PREFIX + "patternOpacity", 22);
        PreferenceConverter.setDefault(s, PREFIX + "lineColor", new RGB(112, 148, 173));
        PreferenceConverter.setDefault(s, PREFIX + "paperColor", new RGB(250, 247, 237));
        // Empty means an older installation: derive its dark counterpart without changing saved light colors.
        s.setDefault(PREFIX+"darkPaperColor", "");
        PreferenceConverter.setDefault(s, PREFIX + "patternColor", new RGB(137, 159, 171));
    }

    public static NotebookSettings read(IPreferenceStore s) { return read(s, false); }
    public static NotebookSettings read(IPreferenceStore s, boolean defaults) {
        RGB light=color(s,"paperColor",defaults);
        String darkValue=defaults?s.getDefaultString(PREFIX+"darkPaperColor"):s.getString(PREFIX+"darkPaperColor");
        RGB dark=darkValue.isEmpty()?darkPaper(light):color(s,"darkPaperColor",defaults);
        return new NotebookSettings(bool(s, "enabled", defaults), bool(s, "lines", defaults),
            bool(s, "theme", defaults), number(s, "lineStyle", defaults), number(s, "paper", defaults),
            number(s, "width", defaults), number(s, "gap", defaults), number(s, "spacing", defaults), number(s, "scale", defaults),
            number(s, "lineOpacity", defaults), number(s, "patternOpacity", defaults),
            number(s, "texture", defaults), number(s, "textureOpacity", defaults),
            color(s, "lineColor", defaults), color(s, "paperColor", defaults), color(s, "patternColor", defaults),
            number(s,"highlightOpacity",defaults),dark);
    }
    private static boolean bool(IPreferenceStore s, String k, boolean d) {
        return d ? s.getDefaultBoolean(PREFIX + k) : s.getBoolean(PREFIX + k);
    }
    private static int number(IPreferenceStore s, String k, boolean d) {
        return d ? s.getDefaultInt(PREFIX + k) : s.getInt(PREFIX + k);
    }
    private static RGB color(IPreferenceStore s, String k, boolean d) {
        return d ? PreferenceConverter.getDefaultColor(s, PREFIX + k) : PreferenceConverter.getColor(s, PREFIX + k);
    }
    public void write(IPreferenceStore s) {
        s.setValue(PREFIX + "enabled", enabled); s.setValue(PREFIX + "lines", lines);
        s.setValue(PREFIX + "theme", theme); s.setValue(PREFIX + "lineStyle", lineStyle);
        s.setValue(PREFIX + "paper", paper); s.setValue(PREFIX + "width", width);
        s.setValue(PREFIX + "gap", gap); s.setValue(PREFIX + "scale", scale);
        s.setValue(PREFIX + "spacing", spacing);
        s.setValue(PREFIX + "texture", texture); s.setValue(PREFIX + "textureOpacity", textureOpacity);
        s.setValue(PREFIX + "highlightOpacity",highlightOpacity);
        s.setValue(PREFIX + "lineOpacity", lineOpacity); s.setValue(PREFIX + "patternOpacity", patternOpacity);
        PreferenceConverter.setValue(s, PREFIX + "lineColor", lineColor);
        PreferenceConverter.setValue(s, PREFIX + "paperColor", paperColor);
        PreferenceConverter.setValue(s, PREFIX + "darkPaperColor", darkPaperColor);
        PreferenceConverter.setValue(s, PREFIX + "patternColor", patternColor);
    }
}

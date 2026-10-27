package dev.eclipseline;

import org.eclipse.jface.preference.ColorSelector;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public final class NotebookPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
    public static final String ID = "dev.eclipseline.preferences";
    private Button enabled, lines, theme;
    private Combo lineStyle, paper, texture, zoom;
    private Spinner width, gap, spacing, scale, lineOpacity, patternOpacity, textureOpacity, highlightOpacity;
    private ColorSelector lineColor, paperColor, darkPaperColor, patternColor;
    private StyledText preview;
    private NotebookSurface surface;
    private NotebookSettings draft;
    private Font previewFont;
    private org.eclipse.swt.graphics.Color previewHighlight;
    private boolean loading;
    private AutoCloseable themeChanges;

    public NotebookPreferencePage() {
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("편집기의 글꼴과 화면 배율에 맞춰 노트 선과 배경을 그립니다.");
    }
    @Override public void init(IWorkbench workbench) { }

    @Override protected Control createContents(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(1, false));
        enabled = check(root, "소스 편집기에 노트 표시");
        TabFolder tabs = new TabFolder(root, SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Composite lineTab = tab(tabs, "선");
        lines = check(lineTab, "모든 글줄 아래에 선 표시");
        ((GridData) lines.getLayoutData()).horizontalSpan = 2;
        lineStyle = combo(lineTab, "선 종류", NotebookSettings.LINE_NAMES);
        lineColor = color(lineTab, "선 색상");
        width = spinner(lineTab, "두께 (글자 높이의 %)", 1, 25);
        gap = spinner(lineTab, "텍스트와 선 간격 (글자 높이의 %)", -50, 100);
        gap.setToolTipText("선 위치만 이동합니다. 음수는 선을 글자 쪽으로 올립니다. 줄 간격은 별도로 조절합니다.");
        spacing = spinner(lineTab, "추가 줄 간격 (글자 높이의 %)", 0, 200);
        spacing.setToolTipText("기존 글줄 사이에 더할 여백입니다. 0은 편집기 기본 간격이며 선 표시 여부와 독립적입니다.");
        lineOpacity = spinner(lineTab, "선 진하기 (%)", 1, 100);
        highlightOpacity = spinner(lineTab,"현재 줄 강조 불투명도 (%)",0,100);
        highlightOpacity.setToolTipText("0은 완전 투명, 100은 불투명입니다. 기본값은 50입니다.");
        Composite paperTab = tab(tabs, "배경");
        paper = combo(paperTab, "배경 종류", NotebookSettings.PAPER_NAMES);
        theme = check(paperTab, "편집기 테마의 배경색 사용");
        ((GridData) theme.getLayoutData()).horizontalSpan = 2;
        paperColor = color(paperTab, "종이 배경색 (라이트 테마)");
        darkPaperColor = color(paperTab, "종이 배경색 (다크 테마)");
        darkPaperColor.getButton().setToolTipText("다크 테마에서 자동으로 적용합니다. 무늬·질감·선 설정은 라이트 테마와 같습니다.");
        patternColor = color(paperTab, "무늬 색상");
        scale = spinner(paperTab, "무늬 크기 (줄 높이의 %)", 50, 300);
        patternOpacity = spinner(paperTab, "무늬 진하기 (%)", 1, 100);
        texture = combo(paperTab, "종이 질감", NotebookSettings.TEXTURE_NAMES);
        texture.setToolTipText("종이 질감은 무늬와 조합할 수 있으며 글꼴 확대/축소와 무관한 크기로 표시합니다.");
        texture.addListener(SWT.Selection, event -> {
            if (loading || texture.getSelectionIndex() <= 0) return;
            theme.setSelection(false);
            paperColor.setColorValue(NotebookSettings.TEXTURE_COLORS[texture.getSelectionIndex()]);
            darkPaperColor.setColorValue(NotebookSettings.darkPaper(NotebookSettings.TEXTURE_COLORS[texture.getSelectionIndex()]));
            changed();
        });
        textureOpacity = spinner(paperTab, "질감 진하기 (%)", 0, 100);

        Composite previewHeader = new Composite(root, SWT.NONE);
        previewHeader.setLayout(new GridLayout(3, false));
        previewHeader.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Label title = new Label(previewHeader, SWT.NONE); title.setText("미리보기");
        title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(previewHeader, SWT.NONE).setText("글꼴 확대/축소");
        zoom = new Combo(previewHeader, SWT.DROP_DOWN | SWT.READ_ONLY);
        zoom.setItems(new String[] {"50%", "75%", "100%", "125%", "150%", "175%", "200%"});
        zoom.select(2);
        zoom.addListener(SWT.Selection, event -> updatePreviewFont());
        preview = new StyledText(root, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        GridData pd = new GridData(SWT.FILL, SWT.FILL, true, true);
        pd.heightHint = 210; pd.widthHint = 510; preview.setLayoutData(pd);
        preview.setText("// Paper · 공책 미리보기\n"
            + "public class Notebook {\n"
            + "    String note = \"글자 크기에 맞춘 선과 모눈\";\n"
            + "    // Preferences → Apply로 편집기에 적용\n"
            + "    int pages = 10;\n"
            + "}\n");
        StyleRange comment = new StyleRange(0, preview.getLine(0).length(),
            parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN), null);
        preview.setStyleRange(comment);
        Label hint = new Label(root, SWT.WRAP);
        hint.setText("이미지를 확대하지 않고 배율별로 다시 그립니다. 적용 전 미리보기에서 확인하세요.");
        hint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        load(NotebookSettings.read(getPreferenceStore()));
        updatePreviewFont();
        previewHighlight = new org.eclipse.swt.graphics.Color(preview.getDisplay(),
            org.eclipse.jface.preference.PreferenceConverter.getColor(org.eclipse.ui.editors.text.EditorsUI.getPreferenceStore(),
                org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants.EDITOR_CURRENT_LINE_COLOR));
        preview.addLineBackgroundListener(event -> {
            if (event.lineOffset == preview.getOffsetAtLine(preview.getLineAtOffset(preview.getCaretOffset())))
                event.lineBackground = previewHighlight;
        });
        surface = new NotebookSurface(preview, () -> draft, true);
        themeChanges=NotebookTheme.watch(preview.getDisplay(),()->{
            if(!preview.isDisposed()) {
                org.eclipse.swt.graphics.RGB color=org.eclipse.jface.preference.PreferenceConverter.getColor(org.eclipse.ui.editors.text.EditorsUI.getPreferenceStore(),
                    org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants.EDITOR_CURRENT_LINE_COLOR);
                org.eclipse.swt.graphics.Color old=previewHighlight;
                previewHighlight=new org.eclipse.swt.graphics.Color(preview.getDisplay(),color);
                surface.refresh();if(old!=null)old.dispose();
            }
        });
        return root;
    }

    private Composite tab(TabFolder folder, String name) {
        TabItem item = new TabItem(folder, SWT.NONE); item.setText(name);
        Composite body = new Composite(folder, SWT.NONE);
        body.setLayout(new GridLayout(2, false)); item.setControl(body); return body;
    }
    private Button check(Composite parent, String label) {
        Button control = new Button(parent, SWT.CHECK); control.setText(label);
        control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        control.addListener(SWT.Selection, e -> changed()); return control;
    }
    private Combo combo(Composite parent, String label, String[] items) {
        new Label(parent, SWT.NONE).setText(label);
        Combo control = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY); control.setItems(items);
        control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        control.addListener(SWT.Selection, e -> changed()); return control;
    }
    private Spinner spinner(Composite parent, String label, int min, int max) {
        new Label(parent, SWT.NONE).setText(label);
        Spinner control = new Spinner(parent, SWT.BORDER); control.setMinimum(min); control.setMaximum(max);
        control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        control.addListener(SWT.Selection, e -> changed());
        control.addListener(SWT.Modify, e -> changed()); return control;
    }
    private ColorSelector color(Composite parent, String label) {
        new Label(parent, SWT.NONE).setText(label);
        ColorSelector selector = new ColorSelector(parent);
        selector.addListener(e -> changed()); return selector;
    }
    private void load(NotebookSettings s) {
        loading = true;
        enabled.setSelection(s.enabled); lines.setSelection(s.lines); theme.setSelection(s.theme);
        lineStyle.select(s.lineStyle); paper.select(s.paper); width.setSelection(s.width); gap.setSelection(s.gap);
        spacing.setSelection(s.spacing); texture.select(s.texture); textureOpacity.setSelection(s.textureOpacity);
        highlightOpacity.setSelection(s.highlightOpacity);
        scale.setSelection(s.scale); lineOpacity.setSelection(s.lineOpacity); patternOpacity.setSelection(s.patternOpacity);
        lineColor.setColorValue(s.lineColor); paperColor.setColorValue(s.paperColor); patternColor.setColorValue(s.patternColor);
        darkPaperColor.setColorValue(s.darkPaperColor);
        loading = false; changed();
    }
    private void changed() {
        if (loading || preview == null) return;
        NotebookSettings next = new NotebookSettings(enabled.getSelection(), lines.getSelection(), theme.getSelection(),
            lineStyle.getSelectionIndex(), paper.getSelectionIndex(), width.getSelection(), gap.getSelection(), spacing.getSelection(),
            scale.getSelection(), lineOpacity.getSelection(), patternOpacity.getSelection(),
            texture.getSelectionIndex(), textureOpacity.getSelection(),
            lineColor.getColorValue(), paperColor.getColorValue(), patternColor.getColorValue(),highlightOpacity.getSelection(),darkPaperColor.getColorValue());
        boolean modified=!next.equals(draft);
        if(modified)draft=next;
        boolean on = draft.enabled, ruled = on && draft.lines;
        lines.setEnabled(on); lineStyle.setEnabled(ruled); lineColor.setEnabled(ruled);
        width.setEnabled(ruled); gap.setEnabled(ruled); lineOpacity.setEnabled(ruled);
        spacing.setEnabled(on); texture.setEnabled(on); textureOpacity.setEnabled(on && draft.texture != 0);
        highlightOpacity.setEnabled(on);
        paper.setEnabled(on); theme.setEnabled(on); paperColor.setEnabled(on && !draft.theme);
        darkPaperColor.setEnabled(on&&!draft.theme);
        patternColor.setEnabled(on && draft.paper != 0); scale.setEnabled(on && draft.paper != 0);
        patternOpacity.setEnabled(on && draft.paper != 0);
        if (surface != null && modified) surface.refresh();
    }
    private void updatePreviewFont() {
        if (preview == null || preview.isDisposed()) return;
        int[] values = {50, 75, 100, 125, 150, 175, 200};
        FontData[] data = JFaceResources.getTextFont().getFontData();
        for (FontData fd : data) fd.setHeight(Math.max(4, (int) Math.round(fd.getHeight() * values[zoom.getSelectionIndex()] / 100.0)));
        Font old = previewFont; previewFont = new Font(preview.getDisplay(), data); preview.setFont(previewFont);
        if (old != null) old.dispose();
        if (surface != null) surface.refresh();
    }
    @Override public boolean performOk() {
        changed(); draft.write(getPreferenceStore());
        try { org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE.getNode(Activator.ID).flush(); }
        catch (org.osgi.service.prefs.BackingStoreException error) {
            Activator.log("노트 설정 저장에 실패했습니다.", error);
            setErrorMessage("설정을 저장하지 못했습니다: " + error.getMessage()); return false;
        }
        EditorDecorator decorator=Activator.getDefault().decorator();
        if(decorator!=null)decorator.refreshSettings();
        return true;
    }
    @Override protected void performApply() { performOk(); }
    @Override protected void performDefaults() {
        load(NotebookSettings.read(getPreferenceStore(), true)); super.performDefaults();
    }
    @Override public void dispose() {
        if(themeChanges!=null)try{themeChanges.close();}catch(Exception error){Activator.log("미리보기 테마 알림 해제에 실패했습니다.",error);}
        if (surface != null) surface.close();
        if (previewFont != null) previewFont.dispose(); super.dispose();
        if (previewHighlight != null) previewHighlight.dispose();
    }
}

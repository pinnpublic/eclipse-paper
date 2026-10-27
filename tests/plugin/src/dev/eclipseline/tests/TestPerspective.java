package dev.eclipseline.tests;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
public final class TestPerspective implements IPerspectiveFactory {
    @Override public void createInitialLayout(IPageLayout layout) {
        layout.addView("org.eclipse.jdt.ui.PackageExplorer", IPageLayout.LEFT, 0.2f, layout.getEditorArea());
    }
}

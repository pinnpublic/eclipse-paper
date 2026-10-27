package dev.eclipseline;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public final class Activator extends AbstractUIPlugin {
    public static final String ID = "dev.eclipseline";
    private static Activator instance;
    private EditorDecorator decorator;
    public static Activator getDefault() { return instance; }
    @Override public void start(BundleContext context) throws Exception {
        super.start(context); instance = this;
        NotebookSettings.defaults(getPreferenceStore());
    }
    public void initialize() {
        if (decorator == null && PlatformUI.isWorkbenchRunning()) {
            decorator = new EditorDecorator(PlatformUI.getWorkbench(), getPreferenceStore());
            decorator.start();
        }
    }
    public EditorDecorator decorator() { return decorator; }
    @Override public void stop(BundleContext context) throws Exception {
        if (decorator != null) {
            org.eclipse.swt.widgets.Display display = PlatformUI.getWorkbench().getDisplay();
            if (!display.isDisposed()) display.syncExec(() -> decorator.close());
            decorator = null;
        }
        instance = null; super.stop(context);
    }
    public static void log(String message, Throwable error) {
        if (instance != null) instance.getLog().log(new Status(IStatus.ERROR, ID, message, error));
    }
}

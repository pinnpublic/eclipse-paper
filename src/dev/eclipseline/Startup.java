package dev.eclipseline;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
public final class Startup implements IStartup {
    @Override public void earlyStartup() {
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            Activator plugin = Activator.getDefault();
            if (plugin != null) plugin.initialize();
        });
    }
}

package dev.eclipseline;

import java.util.Locale;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.css.swt.theme.ITheme;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.osgi.service.event.EventHandler;

/** Chooses a background profile independently of the paper color already applied to the widget. */
public final class NotebookTheme {
    private NotebookTheme(){}
    public static boolean isDark(StyledText text) {
        if(PlatformUI.isWorkbenchRunning()) {
            IThemeEngine engine=PlatformUI.getWorkbench().getService(IThemeEngine.class);
            ITheme theme=engine==null?null:engine.getActiveTheme();
            if(theme!=null) {
                String id=theme.getId().toLowerCase(Locale.ROOT);
                if(id.contains("dark"))return true;
                if(id.equals("org.eclipse.e4.ui.css.theme.e4_default")||id.contains("light")||id.contains("classic"))return false;
            }
        }
        RGB foreground=text.getForeground().getRGB();
        return (foreground.red*299+foreground.green*587+foreground.blue*114)>150000;
    }
    public static AutoCloseable watch(Display display,Runnable refresh) {
        if(!PlatformUI.isWorkbenchRunning())return ()->{};
        IEventBroker broker=PlatformUI.getWorkbench().getService(IEventBroker.class);
        if(broker==null)return ()->{};
        final class Watch implements AutoCloseable {
            volatile boolean closed,pending;
            final EventHandler handler=event->{
                if(closed||pending||display.isDisposed())return;pending=true;
                display.asyncExec(()->{pending=false;if(!closed&&!display.isDisposed())refresh.run();});
            };
            @Override public void close(){closed=true;broker.unsubscribe(handler);}
        }
        Watch watch=new Watch();broker.subscribe(IThemeEngine.Events.THEME_CHANGED,watch.handler);return watch;
    }
}

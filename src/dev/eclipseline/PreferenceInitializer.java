package dev.eclipseline;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
public final class PreferenceInitializer extends AbstractPreferenceInitializer {
    @Override public void initializeDefaultPreferences() {
        NotebookSettings.defaults(Activator.getDefault().getPreferenceStore());
    }
}

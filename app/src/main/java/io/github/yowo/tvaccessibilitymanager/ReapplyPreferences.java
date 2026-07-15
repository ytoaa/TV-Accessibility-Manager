package io.github.yowo.tvaccessibilitymanager;

import android.content.Context;
import android.content.SharedPreferences;

final class ReapplyPreferences {
    private static final String PREFS = "accessibility_manager_state";
    private static final String AUTO_REAPPLY = "auto_reapply";
    private static final String DESIRED_PRESENT = "desired_present";
    private static final String DESIRED_SERVICES = "desired_services";
    private static final String DESIRED_ENABLED = "desired_enabled";

    private ReapplyPreferences() {
    }

    static boolean isAutoReapplyEnabled(Context context) {
        return preferences(context).getBoolean(AUTO_REAPPLY, false);
    }

    static void setAutoReapplyEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(AUTO_REAPPLY, enabled).apply();
    }

    static void saveDesired(Context context, AccessibilitySettingsStore.Snapshot snapshot) {
        preferences(context)
                .edit()
                .putString(DESIRED_SERVICES, snapshot.services)
                .putInt(DESIRED_ENABLED, snapshot.enabled)
                .putBoolean(DESIRED_PRESENT, true)
                .apply();
    }

    static AccessibilitySettingsStore.Snapshot readDesired(Context context) {
        SharedPreferences preferences = preferences(context);
        if (!preferences.getBoolean(DESIRED_PRESENT, false)) {
            return null;
        }
        return new AccessibilitySettingsStore.Snapshot(
                preferences.getString(DESIRED_SERVICES, ""),
                preferences.getInt(DESIRED_ENABLED, 0)
        );
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

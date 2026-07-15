package io.github.yowo.tvaccessibilitymanager;

import android.content.ContentResolver;
import android.provider.Settings;

final class AccessibilitySettingsStore {
    private AccessibilitySettingsStore() {
    }

    static Snapshot read(ContentResolver resolver) {
        try {
            String services = Settings.Secure.getString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            int enabled = Settings.Secure.getInt(
                    resolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
            );
            return new Snapshot(services == null ? "" : services, enabled);
        } catch (RuntimeException ignored) {
            // Some vendor builds restrict even reads before WRITE_SECURE_SETTINGS is granted.
            return new Snapshot("", 0);
        }
    }

    static boolean write(ContentResolver resolver, Snapshot desired) {
        Snapshot original = read(resolver);
        try {
            boolean servicesWritten = Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    desired.services
            );
            boolean enabledWritten = Settings.Secure.putInt(
                    resolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    desired.enabled
            );
            if (!servicesWritten || !enabledWritten) {
                rollback(resolver, original);
                return false;
            }
            return true;
        } catch (RuntimeException error) {
            rollback(resolver, original);
            return false;
        }
    }

    private static void rollback(ContentResolver resolver, Snapshot original) {
        try {
            Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    original.services
            );
            Settings.Secure.putInt(
                    resolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    original.enabled
            );
        } catch (RuntimeException ignored) {
            // Best effort only. The caller reports the failure to the user.
        }
    }

    static final class Snapshot {
        final String services;
        final int enabled;

        Snapshot(String services, int enabled) {
            this.services = services == null ? "" : services;
            this.enabled = enabled;
        }
    }
}

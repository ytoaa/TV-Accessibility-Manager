package io.github.yowo.tvaccessibilitymanager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BootReceiver extends BroadcastReceiver {
    private static final long SECOND_APPLY_DELAY_MS = 3000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (!ReapplyPreferences.isAutoReapplyEnabled(appContext)) {
            return;
        }
        if (appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        AccessibilitySettingsStore.Snapshot desired =
                ReapplyPreferences.readDesired(appContext);
        if (desired == null) {
            return;
        }

        PendingResult pendingResult = goAsync();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                // Apply once immediately and once more after a short delay. Some TV firmware
                // initializes accessibility settings late during boot and may overwrite the
                // first write. This remains best-effort and is intentionally opt-in.
                AccessibilitySettingsStore.write(appContext.getContentResolver(), desired);
                try {
                    Thread.sleep(SECOND_APPLY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                AccessibilitySettingsStore.write(appContext.getContentResolver(), desired);
            } finally {
                pendingResult.finish();
                executor.shutdown();
            }
        });
    }
}

package io.github.yowo.tvaccessibilitymanager;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final String PREFS = "accessibility_manager_state";
    private static final String BACKUP_SERVICES = "backup_services";
    private static final String BACKUP_ENABLED = "backup_enabled";
    private static final String BACKUP_PRESENT = "backup_present";

    private TextView permissionStatus;
    private TextView grantCommand;
    private TextView summary;
    private LinearLayout servicesContainer;
    private Button saveButton;
    private Button restoreButton;
    private CheckBox autoReapplyCheckBox;

    private final List<ServiceRow> rows = new ArrayList<>();
    private final LinkedHashSet<ComponentName> preservedUnknownComponents = new LinkedHashSet<>();
    private final List<String> preservedInvalidTokens = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionStatus = findViewById(R.id.permissionStatus);
        grantCommand = findViewById(R.id.grantCommand);
        summary = findViewById(R.id.summary);
        servicesContainer = findViewById(R.id.servicesContainer);
        saveButton = findViewById(R.id.saveButton);
        restoreButton = findViewById(R.id.restoreButton);
        autoReapplyCheckBox = findViewById(R.id.autoReapplyCheckBox);
        Button refreshButton = findViewById(R.id.refreshButton);

        refreshButton.setOnClickListener(view -> reload());
        saveButton.setOnClickListener(view -> saveSelection());
        restoreButton.setOnClickListener(view -> confirmRestore());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        boolean permissionGranted = hasSecureSettingsPermission();
        updatePermissionUi(permissionGranted);
        loadServices();
        saveButton.setEnabled(permissionGranted);
        restoreButton.setEnabled(permissionGranted && hasBackup());
        autoReapplyCheckBox.setOnCheckedChangeListener(null);
        autoReapplyCheckBox.setChecked(ReapplyPreferences.isAutoReapplyEnabled(this));
        autoReapplyCheckBox.setEnabled(permissionGranted);
        autoReapplyCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ReapplyPreferences.setAutoReapplyEnabled(this, isChecked);
            if (isChecked) {
                ReapplyPreferences.saveDesired(
                        this,
                        AccessibilitySettingsStore.read(getContentResolver())
                );
                Toast.makeText(this, R.string.auto_reapply_enabled_notice, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean hasSecureSettingsPermission() {
        return checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updatePermissionUi(boolean granted) {
        permissionStatus.setText(granted
                ? R.string.permission_granted
                : R.string.permission_missing);

        String command = "adb shell pm grant " + getPackageName()
                + " android.permission.WRITE_SECURE_SETTINGS";
        grantCommand.setText(getString(R.string.grant_help) + "\n" + command);
        grantCommand.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    private void loadServices() {
        rows.clear();
        preservedUnknownComponents.clear();
        preservedInvalidTokens.clear();
        servicesContainer.removeAllViews();

        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> installed = new ArrayList<>();
        if (manager != null) {
            try {
                List<AccessibilityServiceInfo> reported =
                        manager.getInstalledAccessibilityServiceList();
                if (reported != null) {
                    installed.addAll(reported);
                }
            } catch (RuntimeException ignored) {
                // Continue with the PackageManager fallback below.
            }
        }

        AccessibilitySettingsStore.Snapshot current =
                AccessibilitySettingsStore.read(getContentResolver());
        ComponentListCodec.ParsedComponents parsed =
                ComponentListCodec.parse(current.services);

        Set<ComponentName> installedComponents = new HashSet<>();
        LinkedHashMap<ComponentName, ServiceEntry> entryMap = new LinkedHashMap<>();
        PackageManager packageManager = getPackageManager();

        for (AccessibilityServiceInfo info : installed) {
            if (info == null || info.getResolveInfo() == null
                    || info.getResolveInfo().serviceInfo == null) {
                continue;
            }
            addServiceEntry(entryMap, packageManager, info.getResolveInfo());
        }

        // Some TV firmware hides third-party services from its settings UI. Querying the
        // declared AccessibilityService intent as a fallback helps surface sideloaded apps.
        Intent serviceIntent = new Intent(AccessibilityService.SERVICE_INTERFACE);
        List<ResolveInfo> resolvedServices = new ArrayList<>();
        try {
            List<ResolveInfo> queried = packageManager.queryIntentServices(
                    serviceIntent,
                    PackageManager.GET_META_DATA
            );
            if (queried != null) {
                resolvedServices.addAll(queried);
            }
        } catch (RuntimeException ignored) {
            // The AccessibilityManager result may still be sufficient.
        }
        for (ResolveInfo resolveInfo : resolvedServices) {
            if (resolveInfo == null || resolveInfo.serviceInfo == null) {
                continue;
            }
            if (!Manifest.permission.BIND_ACCESSIBILITY_SERVICE.equals(
                    resolveInfo.serviceInfo.permission)) {
                continue;
            }
            addServiceEntry(entryMap, packageManager, resolveInfo);
        }

        List<ServiceEntry> entries = new ArrayList<>(entryMap.values());
        for (ServiceEntry entry : entries) {
            installedComponents.add(entry.component);
        }

        Collator collator = Collator.getInstance(Locale.getDefault());
        entries.sort((left, right) -> collator.compare(left.label, right.label));

        for (ComponentName component : parsed.components) {
            if (!installedComponents.contains(component)) {
                preservedUnknownComponents.add(component);
            }
        }
        preservedInvalidTokens.addAll(parsed.invalidTokens);

        for (ServiceEntry entry : entries) {
            CheckBox checkBox = buildServiceCheckBox(entry, parsed.components.contains(entry.component));
            servicesContainer.addView(checkBox);
            rows.add(new ServiceRow(entry.component, checkBox));
        }

        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_services);
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setTextSize(18f);
            empty.setPadding(12, 18, 12, 18);
            servicesContainer.addView(empty);
        }

        int preservedCount = preservedUnknownComponents.size() + preservedInvalidTokens.size();
        int enabledInstalled = 0;
        for (ServiceRow row : rows) {
            if (row.checkBox.isChecked()) {
                enabledInstalled++;
            }
        }
        String summaryText = getString(
                R.string.summary_format,
                rows.size(),
                enabledInstalled,
                preservedCount
        );
        if (preservedCount > 0) {
            summaryText += "\n" + getString(R.string.unknown_preserved);
        }
        summary.setText(summaryText);
    }

    private void addServiceEntry(
            LinkedHashMap<ComponentName, ServiceEntry> entries,
            PackageManager packageManager,
            ResolveInfo resolveInfo
    ) {
        ServiceInfo serviceInfo = resolveInfo.serviceInfo;
        if (serviceInfo.packageName == null || serviceInfo.name == null) {
            return;
        }
        ComponentName component = new ComponentName(
                serviceInfo.packageName,
                serviceInfo.name
        );
        CharSequence labelSequence = resolveInfo.loadLabel(packageManager);
        String label = labelSequence == null
                ? component.getPackageName()
                : labelSequence.toString();
        entries.put(component, new ServiceEntry(label, component));
    }

    private CheckBox buildServiceCheckBox(ServiceEntry entry, boolean checked) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(checked);
        checkBox.setButtonTintList(getColorStateList(R.color.checkbox_tint));
        checkBox.setText(getString(
                R.string.service_component,
                entry.label,
                entry.component.flattenToString()
        ));
        checkBox.setTextColor(getColor(R.color.text_primary));
        checkBox.setTextSize(17f);
        checkBox.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        checkBox.setGravity(Gravity.CENTER_VERTICAL);
        checkBox.setFocusable(true);
        checkBox.setClickable(true);
        checkBox.setBackgroundResource(R.drawable.service_item_background);
        checkBox.setPadding(dp(16), dp(8), dp(16), dp(8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        checkBox.setLayoutParams(params);
        return checkBox;
    }

    private void saveSelection() {
        if (!hasSecureSettingsPermission()) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
            reload();
            return;
        }

        AccessibilitySettingsStore.Snapshot before =
                AccessibilitySettingsStore.read(getContentResolver());
        saveBackup(before);

        LinkedHashSet<ComponentName> selected = new LinkedHashSet<>();
        for (ServiceRow row : rows) {
            if (row.checkBox.isChecked()) {
                selected.add(row.component);
            }
        }
        selected.addAll(preservedUnknownComponents);

        String serialized = ComponentListCodec.serialize(selected, preservedInvalidTokens);
        AccessibilitySettingsStore.Snapshot desired =
                new AccessibilitySettingsStore.Snapshot(
                        serialized,
                        TextUtils.isEmpty(serialized) ? 0 : 1
                );

        boolean written = AccessibilitySettingsStore.write(getContentResolver(), desired);
        if (!written) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            reload();
            return;
        }

        ReapplyPreferences.saveDesired(this, desired);
        Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(() -> verifyAndReload(desired), 600L);
    }

    private void verifyAndReload(AccessibilitySettingsStore.Snapshot expected) {
        AccessibilitySettingsStore.Snapshot actual =
                AccessibilitySettingsStore.read(getContentResolver());
        ComponentListCodec.ParsedComponents expectedParsed =
                ComponentListCodec.parse(expected.services);
        ComponentListCodec.ParsedComponents actualParsed =
                ComponentListCodec.parse(actual.services);

        boolean servicesMatch = expectedParsed.components.equals(actualParsed.components)
                && expectedParsed.invalidTokens.equals(actualParsed.invalidTokens);
        boolean enabledMatches = expected.enabled == actual.enabled;
        if (!servicesMatch || !enabledMatches) {
            Toast.makeText(this, R.string.verify_failed, Toast.LENGTH_LONG).show();
        }
        reload();
    }

    private void confirmRestore() {
        if (!hasBackup()) {
            Toast.makeText(this, R.string.restore_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_restore_title)
                .setMessage(R.string.confirm_restore_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> restoreBackup())
                .show();
    }

    private void restoreBackup() {
        if (!hasSecureSettingsPermission()) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!preferences.getBoolean(BACKUP_PRESENT, false)) {
            Toast.makeText(this, R.string.restore_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        AccessibilitySettingsStore.Snapshot backup =
                new AccessibilitySettingsStore.Snapshot(
                        preferences.getString(BACKUP_SERVICES, ""),
                        preferences.getInt(BACKUP_ENABLED, 0)
                );
        boolean restored = AccessibilitySettingsStore.write(getContentResolver(), backup);
        if (restored) {
            preferences.edit().putBoolean(BACKUP_PRESENT, false).apply();
            ReapplyPreferences.saveDesired(this, backup);
            Toast.makeText(this, R.string.restore_success, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
        }
        reload();
    }

    private void saveBackup(AccessibilitySettingsStore.Snapshot snapshot) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(BACKUP_SERVICES, snapshot.services)
                .putInt(BACKUP_ENABLED, snapshot.enabled)
                .putBoolean(BACKUP_PRESENT, true)
                .apply();
    }

    private boolean hasBackup() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(BACKUP_PRESENT, false);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ServiceEntry {
        final String label;
        final ComponentName component;

        ServiceEntry(String label, ComponentName component) {
            this.label = label;
            this.component = component;
        }
    }

    private static final class ServiceRow {
        final ComponentName component;
        final CheckBox checkBox;

        ServiceRow(ComponentName component, CheckBox checkBox) {
            this.component = component;
            this.checkBox = checkBox;
        }
    }
}

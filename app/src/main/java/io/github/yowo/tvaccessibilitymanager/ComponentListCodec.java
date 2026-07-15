package io.github.yowo.tvaccessibilitymanager;

import android.content.ComponentName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ComponentListCodec {
    private ComponentListCodec() {
    }

    static ParsedComponents parse(String raw) {
        LinkedHashSet<ComponentName> components = new LinkedHashSet<>();
        ArrayList<String> invalidTokens = new ArrayList<>();

        if (raw == null || raw.trim().isEmpty()) {
            return new ParsedComponents(components, invalidTokens);
        }

        for (String token : raw.split(":")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            ComponentName component = ComponentName.unflattenFromString(trimmed);
            if (component == null) {
                invalidTokens.add(trimmed);
            } else {
                components.add(component);
            }
        }
        return new ParsedComponents(components, invalidTokens);
    }

    static String serialize(Collection<ComponentName> components, Collection<String> preservedRawTokens) {
        List<String> output = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (ComponentName component : components) {
            String token = component.flattenToString();
            if (seen.add(token)) {
                output.add(token);
            }
        }

        for (String rawToken : preservedRawTokens) {
            String trimmed = rawToken == null ? "" : rawToken.trim();
            if (!trimmed.isEmpty() && seen.add(trimmed)) {
                output.add(trimmed);
            }
        }
        StringBuilder joined = new StringBuilder();
        for (String token : output) {
            if (joined.length() > 0) {
                joined.append(':');
            }
            joined.append(token);
        }
        return joined.toString();
    }

    static final class ParsedComponents {
        final LinkedHashSet<ComponentName> components;
        final ArrayList<String> invalidTokens;

        ParsedComponents(LinkedHashSet<ComponentName> components, ArrayList<String> invalidTokens) {
            this.components = components;
            this.invalidTokens = invalidTokens;
        }
    }
}

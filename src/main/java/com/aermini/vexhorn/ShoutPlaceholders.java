package com.aermini.vexhorn;

import java.lang.reflect.Method;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Resolve optional VexView placeholders once on receipt, before escaping HUD fields. */
final class ShoutPlaceholders {
    private final Method expand;

    ShoutPlaceholders(Plugin papi) throws ReflectiveOperationException {
        // Use the same optional Player overload that VexView 2.7.3 itself calls.
        Class<?> api = papi.getClass().getClassLoader().loadClass("me.clip.placeholderapi.PlaceholderAPI");
        expand = api.getMethod("setPlaceholders", Player.class, String.class);
    }

    String resolve(Player viewer, String text) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        String result = text.replace("%player%", viewer.getDisplayName());
        try {
            String expanded = (String) expand.invoke(null, viewer, result);
            if (expanded != null) {
                result = expanded;
            }
        } catch (ReflectiveOperationException ignored) {
            // Keep the shout readable if a third-party expansion fails.
        }
        return literalPercents(result);
    }

    static String literalPercents(String text) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        // A zero-width formatting reset after each literal % prevents the HUD API
        // from expanding the same placeholder again on every animation frame.
        StringBuilder out = new StringBuilder(text.length());
        String style = "";
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            out.append(ch);
            if (ch == '\u00a7' && i + 1 < text.length()) {
                char code = text.charAt(++i);
                out.append(code);
                style = ShoutLane.nextFormat(style, code);
            } else if (ch == '%') {
                out.append("\u00a7r").append(style);
            }
        }
        return out.toString();
    }
}

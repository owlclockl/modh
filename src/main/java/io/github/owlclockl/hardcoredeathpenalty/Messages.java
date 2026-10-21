package io.github.owlclockl.hardcoredeathpenalty;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.network.chat.Component;

/**
 * Small text helper: replaces {@code {placeholder}} tokens with values.
 */
public final class Messages {
    private Messages() {
    }

    public static Component component(String template, Map<String, String> placeholders) {
        return Component.literal(format(template, placeholders));
    }

    public static String format(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public static Map<String, String> map(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Key/value pairs expected");
        }
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }
}

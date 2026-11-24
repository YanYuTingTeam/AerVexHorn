package com.aermini.vexhorn;

import java.util.ArrayList;
import java.util.List;

/** Layout calculations for VexView's HUD coordinate and font conventions. */
final class FlowLayout {
    private static final int LINE_HEIGHT = 10;
    private static final int MIN_BACKGROUND_HEIGHT = 18;

    private FlowLayout() {
    }

    /** Prevent user text from being interpreted as VexView HUD protocol fields. */
    static String safeText(String text) {
        return text.replace("<S>", "＜S>")
                .replace("<#>", "＜#>")
                .replace("<##>", "＜##>")
                .replace("<#&>", "＜#&>")
                .replace("<&>", "＜&>");
    }

    static int characterWidth(char character) {
        if (character == ' ') return 4;
        if ("ilI!.,:;'|".indexOf(character) >= 0) return 3;
        return character >= 0x2E80 ? 8 : 7;
    }

    static int backgroundHeight(String text, int screenHeight) {
        int visibleLines = Math.min(text.split("\\r?\\n", -1).length, maxLines(screenHeight));
        return Math.min(screenHeight, Math.max(MIN_BACKGROUND_HEIGHT, visibleLines * LINE_HEIGHT));
    }

    static int textY(int screenHeight, int heightPercent, int backgroundHeight) {
        int desired = (int) Math.round(screenHeight * heightPercent / 100.0);
        return Math.max(0, Math.min(desired, screenHeight - backgroundHeight + 2));
    }

    static List<String> visibleLines(String text, int x, int screenHeight) {
        String[] lines = text.split("\\r?\\n", -1);
        int count = Math.min(lines.length, maxLines(screenHeight));
        List<String> result = new ArrayList<>(count);
        long hidden = x < 0 ? -(long) x : 0;
        for (int i = 0; i < count; i++) {
            result.add(hidden == 0 ? lines[i] : trimLeft(lines[i], hidden));
        }
        return result;
    }

    private static int maxLines(int screenHeight) {
        return Math.max(1, screenHeight / LINE_HEIGHT);
    }

    /* Negative HUD coordinates mean right-anchored/centered in VexView. Keep
       x at zero and remove fully hidden glyphs, retaining formatting codes. */
    private static String trimLeft(String line, long hidden) {
        StringBuilder prefix = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            char character = line.charAt(i);
            if (character == '\u00a7' && i + 1 < line.length()) {
                prefix.append(character).append(line.charAt(i + 1));
                i += 2;
                continue;
            }
            int charCount = Character.isHighSurrogate(character) && i + 1 < line.length()
                    && Character.isLowSurrogate(line.charAt(i + 1)) ? 2 : 1;
            int width = characterWidth(character);
            if (charCount == 2) width += characterWidth(line.charAt(i + 1));
            if (hidden < width) break;
            hidden -= width;
            i += charCount;
        }
        return prefix.append(line.substring(i)).toString();
    }
}

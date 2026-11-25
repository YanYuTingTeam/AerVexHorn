package com.aermini.vexhorn;

import org.bukkit.map.MapFont.CharacterSprite;
import org.bukkit.map.MinecraftFont;

/** A single scrolling line. VexView positions x < -1 relative to the right edge. */
final class ShoutLane {
    private static final String GAP = "\u00a7r   ";
    static final int MAX_BACKLOG = 65536;
    private static final int MAX_FRAME_CHARS = 4096;
    private static final int LEFT_MARGIN = 24;
    private final StringBuilder text = new StringBuilder();
    private int first;
    private int x = -2;
    private String formatting = "";

    static String prepare(String text) {
        // These strings delimit fields, rows and positioning in a VexView 2.7 HUD.
        String line = text.replace("<S>", "＜S>").replace("<&>", "＜&>")
                .replace("<#>", "＜#>").replace("<##>", "＜##>").replace("<#&>", "＜#&>")
                .replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == '\u00a7') {
            end--;
        }
        return line.substring(0, end);
    }

    void append(String message) {
        int pending = text.length() - first;
        int room = MAX_BACKLOG - pending - (pending > 0 ? GAP.length() : 0);
        if (message.isEmpty() || message.length() > room) {
            return;
        }
        if (!isEmpty()) {
            text.append(GAP);
        }
        text.append(message);
    }

    boolean isEmpty() {
        return first == text.length();
    }

    int hudX() {
        return x;
    }

    String visibleText(int screenWidth) {
        // Include spare characters past the right edge for narrow or formatted text.
        // Cap the UTF-16 length so that even three-byte glyphs fit in one HUD packet.
        int budget = Math.min(MAX_FRAME_CHARS, 2 * Math.min(-x, screenWidth + LEFT_MARGIN) + 64);
        int end = Math.min(text.length(), first + budget);
        if (end < text.length() && (Character.isHighSurrogate(text.charAt(end - 1))
                || text.charAt(end - 1) == '\u00a7')) {
            end--;
        }
        return formatting + text.substring(first, end);
    }

    void advance(int pixels, int screenWidth) {
        x -= pixels;
        // Keep the client drawing the whole visible string. Only discard a glyph after
        // it has passed the left edge, retaining the Minecraft formatting that follows it.
        while (!isEmpty() && x + screenWidth < -LEFT_MARGIN) {
            int cp = text.codePointAt(first);
            if (cp == '\u00a7' && first + 1 < text.length()) {
                formatting = nextFormat(formatting, text.charAt(first + 1));
                first += 2;
                continue;
            }
            int width = glyphWidth(cp) + (formatting.contains("\u00a7l") ? 1 : 0);
            if (x + screenWidth + width > -LEFT_MARGIN) {
                break;
            }
            x += width;
            first += Character.charCount(cp);
        }
        if (isEmpty()) {
            x = -2;
            formatting = "";
        }
        if (isEmpty() || first >= MAX_FRAME_CHARS) {
            text.delete(0, first);
            first = 0;
        }
    }

    static String nextFormat(String formatting, char rawCode) {
        char code = Character.toLowerCase(rawCode);
        if (code == 'r') {
            return "";
        }
        if ("klmno".indexOf(code) >= 0) {
            String style = "\u00a7" + code;
            return formatting.contains(style) ? formatting : formatting + style;
        }
        return "\u00a7" + code;
    }

    private static int glyphWidth(int cp) {
        if (cp == ' ') {
            return 4;
        }
        CharacterSprite glyph = cp < 128 ? MinecraftFont.Font.getChar((char) cp) : null;
        // The client measures all text that is on screen. These estimates only control
        // when to retire off-screen characters and rebase the remaining string.
        return glyph == null ? (cp < 256 ? 6 : 9) : glyph.getWidth() + 1;
    }
}

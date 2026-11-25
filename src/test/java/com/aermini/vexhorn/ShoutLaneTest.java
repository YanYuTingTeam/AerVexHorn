package com.aermini.vexhorn;

import org.junit.Test;

import static org.junit.Assert.*;

public class ShoutLaneTest {
    @Test
    public void aBurstDoesNotGrowTheHudPacketWithTheWholeBacklog() {
        ShoutLane lane = new ShoutLane();
        for (int i = 0; i < 200; i++) {
            lane.append("用户发布的全服喊话 " + i);
        }
        String firstFrame = lane.visibleText(320);
        assertTrue(firstFrame.contains("全服喊话 0"));
        assertFalse(firstFrame.contains("全服喊话 199"));
        for (int i = 0; i < 300 && !lane.visibleText(320).contains("全服喊话 199"); i++) {
            lane.advance(100, 320);
        }
        assertTrue(lane.visibleText(320).contains("全服喊话 199"));
    }

    @Test
    public void literalPercentsKeepTheirColorAndCannotBeExpandedAgain() {
        String raw = "§c§l100% / %player%";
        String literal = ShoutPlaceholders.literalPercents(raw);
        assertFalse(literal.contains("%player%"));
        assertTrue(literal.contains("100%§r§c§l /"));
        assertEquals(org.bukkit.ChatColor.stripColor(raw), org.bukkit.ChatColor.stripColor(literal));
    }

    @Test
    public void firstShoutStartsAtTheRightEdgeWithoutWaitingForAnother() {
        ShoutLane lane = new ShoutLane();
        lane.append("喇叭");

        assertFalse(lane.isEmpty());
        assertEquals(-2, lane.hudX());
        assertTrue(lane.visibleText(320).contains("喇"));
        int start = lane.hudX();
        lane.advance(4, 320);
        assertEquals(start - 4, lane.hudX());
    }

    @Test
    public void arrivalsJoinTheMovingLineInOrderWithoutRewindingIt() {
        ShoutLane lane = new ShoutLane();
        lane.append("§c第一条");
        lane.advance(120, 320);
        int movingX = lane.hudX();

        lane.append("第二条");
        lane.append("第三条");
        assertEquals(movingX, lane.hudX());
        String frame = lane.visibleText(320);
        int first = frame.indexOf("§c第一条");
        int second = frame.indexOf("第二条");
        int third = frame.indexOf("第三条");
        assertTrue(frame, first >= 0 && second > first && third > second);
        assertTrue(frame, frame.substring(first, second).contains("§r"));
    }

    @Test
    public void aResizedViewportUsesItsCurrentLeftEdgeToRetireText() {
        ShoutLane lane = new ShoutLane();
        lane.append("仍在屏幕上");
        lane.advance(120, 320);
        lane.advance(240, 640);
        assertFalse(lane.isEmpty());

        lane.advance(400, 160);
        assertTrue(lane.isEmpty());

        ShoutLane next = new ShoutLane();
        next.append("新");
        assertTrue(next.visibleText(160).contains("新"));
        assertFalse(next.visibleText(160).contains("仍"));
        assertEquals(-2, next.hudX());
    }

    @Test
    public void colorSurvivesClippingAndUnicodeGlyphsStayWhole() {
        ShoutLane lane = new ShoutLane();
        StringBuilder message = new StringBuilder("§aA");
        for (int i = 0; i < 80; i++) {
            message.append("长😀");
        }
        lane.append(message.toString());

        boolean sawClippedHead = false;
        for (int i = 0; i < 1000 && !lane.isEmpty(); i++) {
            String frame = lane.visibleText(100);
            if (!frame.contains("A") && frame.contains("长")) {
                assertTrue(frame, frame.startsWith("§a"));
                sawClippedHead = true;
            }
            for (int j = 0; j < frame.length(); j++) {
                if (Character.isHighSurrogate(frame.charAt(j))) {
                    j++;
                    assertTrue("Incomplete Unicode character in frame",
                            j < frame.length() && Character.isLowSurrogate(frame.charAt(j)));
                } else {
                    assertFalse(Character.isLowSurrogate(frame.charAt(j)));
                }
            }
            lane.advance(4, 100);
        }
        assertTrue("The first glyph should have scrolled off before the rest", sawClippedHead);
        assertTrue(lane.isEmpty());
    }

    @Test
    public void danglingColorMarkerCannotConsumeTheNextShoutsReset() {
        String prepared = ShoutLane.prepare("前缀§");
        assertTrue(prepared.startsWith("前缀"));
        assertFalse(prepared.endsWith("§"));

        ShoutLane lane = new ShoutLane();
        lane.append(prepared);
        lane.append("新消息");
        lane.advance(100, 320);
        String frame = lane.visibleText(320);
        assertTrue(frame, frame.indexOf("§r") > frame.indexOf("前缀"));
        assertTrue(frame, frame.indexOf("新消息") > frame.indexOf("§r"));
    }

    @Test
    public void shoutsStayOnOneLineAndCannotIntroduceHudFields() {
        String raw = "§a甲\r\n乙\n丙\r丁<S><#><##><#&><&>尾";
        String prepared = ShoutLane.prepare(raw);
        for (String token : new String[] {"\r", "\n", "<S>", "<#>", "<##>", "<#&>", "<&>"}) {
            assertFalse(token, prepared.contains(token));
        }
        assertTrue(prepared.startsWith("§a甲"));
        assertTrue(prepared.contains("乙"));
        assertTrue(prepared.contains("丙"));
        assertTrue(prepared.contains("丁"));
        assertTrue(prepared.endsWith("尾"));
    }
}

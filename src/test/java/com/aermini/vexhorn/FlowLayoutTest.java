package com.aermini.vexhorn;

import java.util.Arrays;

public final class FlowLayoutTest {
    @org.junit.Test
    public void checks() {
        equal("hello ＜S> ＜#> ＜##> ＜#&> ＜&> <ordinary>",
                FlowLayout.safeText("hello <S> <#> <##> <#&> <&> <ordinary>"));
        equal(Arrays.asList("ABC"), FlowLayout.visibleLines("ABC", 0, 480));
        equal(Arrays.asList("ABC"), FlowLayout.visibleLines("ABC", -6, 480));
        equal(Arrays.asList("BC"), FlowLayout.visibleLines("ABC", -7, 480));
        equal(Arrays.asList("\u00a7a\u00a7lBC"), FlowLayout.visibleLines("\u00a7a\u00a7lABC", -7, 480));
        equal(Arrays.asList("\u00a7a\u00a7l\u00a7rC"), FlowLayout.visibleLines("\u00a7a\u00a7lA\u00a7rBC", -14, 480));
        equal(Arrays.asList("", "BC"), FlowLayout.visibleLines("A\nABC", -7, 480));
        equal(Arrays.asList("B"), FlowLayout.visibleLines("\ud83d\ude00B", -16, 480));
        equal(Arrays.asList("\ud83d\ude00B"), FlowLayout.visibleLines("\ud83d\ude00B", -15, 480));
        equal(Arrays.asList("a", "b"), FlowLayout.visibleLines("a\nb\nc", 0, 20));
        equal(18, FlowLayout.backgroundHeight("one line", 480));
        equal(50, FlowLayout.backgroundHeight("1\n2\n3\n4\n5", 480));
        equal(20, FlowLayout.backgroundHeight("1\n2\n3", 20));
        equal(72, FlowLayout.textY(480, 15, 18));
        equal(432, FlowLayout.textY(480, 100, 50));
        System.out.println("FlowLayout tests passed");
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }
}

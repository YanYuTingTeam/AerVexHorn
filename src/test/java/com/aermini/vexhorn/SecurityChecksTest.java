package com.aermini.vexhorn;

import java.util.UUID;

public final class SecurityChecksTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
    @org.junit.Test
    public void checks() {
        String key = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
        String signed = "04010000018bcfe5680001020304050607081112131415161718000d53484f55545f4348414e4e454c000568656c6c6f2a023dca2629c5020722dc77a588a7d42e30bf123bcb825b7848c3c4c0f0c215";
        byte[] packet = hex(signed);
        long now = 1700000000000L, hi = 0x0102030405060708L, lo = 0x1112131415161718L;
        PacketAuthenticator auth = new PacketAuthenticator(key);
        check(auth.verify(packet, now, hi, lo, now), "known Python HMAC vector rejected");
        check(!auth.verify(packet, now, hi, lo, now), "replay accepted");
        packet[packet.length - 1] ^= 1;
        check(!new PacketAuthenticator(key).verify(packet, now, hi, lo, now), "tampered MAC accepted");
        packet[packet.length - 1] ^= 1;
        check(!new PacketAuthenticator(key).verify(packet, now, hi, lo, now + 60001), "stale packet accepted");
        check(!new PacketAuthenticator(key).verify(packet, now, hi, lo, now - 60001), "future packet accepted");
        try { new PacketAuthenticator("broken"); throw new AssertionError("bad key accepted"); } catch (IllegalArgumentException expected) { }
        MessageGuard limiter = new MessageGuard(512, 8, 20, 100);
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 20; i++) check(limiter.allowPacket(player, 1), "valid packet dropped");
        check(!limiter.allowPacket(player, 1), "per-source limit bypassed");
        check(limiter.allowPacket(player, 1_000_000_001L), "rate did not reset");
        MessageGuard fanout = new MessageGuard(512, 8, 2048, 4096);
        for (int i = 0; i < 1000; i++) check(fanout.allowPacket(player, 1), "normal large fan-out was limited");
        MessageGuard global = new MessageGuard(512, 8, 20, 100);
        for (int i = 0; i < 100; i++) check(global.allowPacket(UUID.randomUUID(), 1), "global limit too low");
        check(!global.allowPacket(UUID.randomUUID(), 1), "global limit bypassed");
        check(limiter.isTextAllowed("hello\nworld"), "valid text rejected");
        check(!limiter.isTextAllowed(new String(new char[513]).replace('\0', 'x')), "oversized text accepted");
        check(limiter.isTextAllowed("a\nb\nc\nd\ne"), "default invite rejected");
        check(!limiter.isTextAllowed("a\nb\nc\nd\ne\nf\ng\nh\ni"), "excessive lines accepted");
        System.out.println("SecurityChecks passed");
    }
}

package com.aermini.vexhorn;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC v4 messages are accepted once during their validity window. */
final class PacketAuthenticator {
    private static final long CLOCK_SKEW_MS = 60_000L;
    // Capacity covers one minute at the default global limit, while remaining bounded.
    private static final int MAX_NONCES = 262_144;
    private final byte[] key;
    private final Map<UUID, Long> nonces = new HashMap<>();
    private final PriorityQueue<Expiry> expiries = new PriorityQueue<>((a, b) -> Long.compare(a.time, b.time));

    PacketAuthenticator(String hex) {
        if (hex == null || hex.isEmpty()) { key = null; return; }
        if (!hex.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("Invalid key");
        key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
    }

    boolean isEnabled() { return key != null; }

    synchronized boolean verify(byte[] packet, long timestamp, long high, long low, long now) {
        if (key == null || packet.length < 32 || timestamp < now - CLOCK_SKEW_MS || timestamp > now + CLOCK_SKEW_MS) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(packet, 0, packet.length - 32);
            byte[] actual = new byte[32];
            System.arraycopy(packet, packet.length - 32, actual, 0, 32);
            if (!MessageDigest.isEqual(mac.doFinal(), actual)) return false;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
        while (!expiries.isEmpty() && expiries.peek().time < now) {
            nonces.remove(expiries.remove().nonce);
        }
        UUID nonce = new UUID(high, low);
        if (nonces.containsKey(nonce) || nonces.size() >= MAX_NONCES) return false;
        long expires = timestamp + CLOCK_SKEW_MS;
        nonces.put(nonce, expires);
        expiries.add(new Expiry(nonce, expires));
        return true;
    }

    private static final class Expiry {
        final UUID nonce;
        final long time;
        Expiry(UUID nonce, long time) { this.nonce = nonce; this.time = time; }
    }
}

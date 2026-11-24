package com.aermini.vexhorn;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import lk.vexview.gui.components.expand.VexBase64Image;
import lk.vexview.api.VexViewAPI;
import lk.vexview.hud.VexBase64ImageShow;
import lk.vexview.hud.VexTextShow;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class Main extends JavaPlugin implements PluginMessageListener, Listener {
    private static final String CHANNEL = "servershout:cast";
    private static final int BROADCAST_TYPE = 1;
    private static final int DEFAULT_WINDOW_WIDTH = 854;
    private static final int DEFAULT_WINDOW_HEIGHT = 480;
    private static final int FLOW_SPEED = 2;
    private static final int FLOW_GAP = 10;
    private static final int FLOW_Z = 1;
    private static final int BACKGROUND_Z = 0;
    private Set<String> channels = Collections.emptySet();
    private final Map<UUID, FlowState> flows = new HashMap<>();
    private final Map<String, byte[]> backgroundImages = new HashMap<>();
    private BukkitRunnable flowTask;
    private MessageGuard messageGuard;
    private PacketAuthenticator authenticator;
    private int flowHeight;
    private double backgroundTransparency;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.channels = new HashSet<>(this.getConfig().getStringList("servershout.channels"));
        try {
            this.authenticator = new PacketAuthenticator(this.getConfig().getString("servershout.hmac-key", ""));
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid servershout.hmac-key: expected 64 hexadecimal characters; disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.messageGuard = new MessageGuard();
        if (!this.authenticator.isEnabled()) {
            getLogger().warning("Unauthenticated ServerShout mode: block client cast messages at the proxy and prevent direct access to this backend.");
        }
        this.flowHeight = this.limit(this.getConfig().getInt("flowview.height", 15), 0, 100);
        this.backgroundTransparency = this.limit(this.getConfig().getDouble("flowview.background-transparency", 0.35), 0, 1);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        this.flowTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickFlows();
            }
        };
        this.flowTask.runTaskTimer(this, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (this.flowTask != null) {
            this.flowTask.cancel();
            this.flowTask = null;
        }
        for (Map.Entry<UUID, FlowState> entry : new ArrayList<>(this.flows.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                this.clearFlow(player, entry.getValue());
            }
        }
        this.flows.clear();
        this.backgroundImages.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!this.isEnabled() || !CHANNEL.equals(channel) || data.length > Messenger.MAX_MESSAGE_SIZE) {
            return;
        }
        if (!this.messageGuard.allowPacket(player.getUniqueId(), System.nanoTime())) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = input.readUnsignedByte();
            String playerId = null;
            long timestamp = 0;
            long nonceHigh = 0;
            long nonceLow = 0;
            if (version == 4 && this.authenticator.isEnabled()) {
                int type = input.readUnsignedByte();
                if (type != 1 && type != 2) return;
                timestamp = input.readLong();
                nonceHigh = input.readLong();
                nonceLow = input.readLong();
                if (type == 2) playerId = input.readUTF();
            } else if (this.authenticator.isEnabled()) {
                return;
            } else if (version == 2) {
                playerId = input.readUTF();
            } else if (version == 3 && input.readUnsignedByte() != BROADCAST_TYPE) {
                return;
            } else if (version != 3) {
                return;
            }
            String channelName = input.readUTF();
            String text = input.readUTF();
            UUID uuid = playerId == null ? null : UUID.fromString(playerId);
            if ((playerId != null && !uuid.toString().equals(playerId)) || channelName.isEmpty()
                    || !this.channels.contains(channelName)
                    || !MessageGuard.isTextAllowed(text)) {
                return;
            }
            if (version == 4) {
                if (input.available() != 32 || !this.authenticator.verify(data, timestamp, nonceHigh, nonceLow, System.currentTimeMillis())) return;
            } else if (input.available() != 0) {
                return;
            }
            Runnable task = uuid == null ? () -> this.broadcast(text) : () -> this.send(uuid, text);
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(this, task);
            }
        } catch (IOException | IllegalArgumentException ignored) {
        }
    }

    private void send(UUID uuid, String text) {
        if (!this.isEnabled()) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            this.addFlow(player, text);
        }
    }

    private void broadcast(String text) {
        if (!this.isEnabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline()) {
                this.addFlow(player, text);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.messageGuard.removePlayer(event.getPlayer().getUniqueId());
        FlowState state = this.flows.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            this.clearFlow(event.getPlayer(), state);
        }
    }

    private void addFlow(Player player, String text) {
        FlowState state = this.flows.get(player.getUniqueId());
        if (state == null) {
            state = new FlowState(player.getUniqueId());
            this.flows.put(player.getUniqueId(), state);
        }
        if (state.width <= 0) {
            state.width = this.getWindowWidth(player);
            state.height = this.getWindowHeight(player);
        }

        int requiredHeight = FlowLayout.backgroundHeight(text, state.height);
        if (!state.backgroundSent || requiredHeight > state.backgroundHeight) {
            state.backgroundHeight = Math.max(state.backgroundHeight, requiredHeight);
            state.y = FlowLayout.textY(state.height, this.flowHeight, state.backgroundHeight);
            this.sendBackground(player, state);
        }

        int x = state.width;
        for (FlowMessage message : state.messages) {
            x = Math.max(x, message.x + message.width + FLOW_GAP);
        }
        if (state.messages.size() >= MessageGuard.MAX_QUEUED_PER_PLAYER) {
            return;
        }
        FlowMessage message = new FlowMessage("aervexhorn-flow-" + UUID.randomUUID(), text,
                x, this.getTextWidth(text));
        state.messages.add(message);
        this.sendText(player, state, message);
    }

    private void tickFlows() {
        Iterator<Map.Entry<UUID, FlowState>> states = this.flows.entrySet().iterator();
        while (states.hasNext()) {
            Map.Entry<UUID, FlowState> entry = states.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                states.remove();
                continue;
            }

            FlowState state = entry.getValue();
            Iterator<FlowMessage> messages = state.messages.iterator();
            while (messages.hasNext()) {
                FlowMessage message = messages.next();
                message.x -= FLOW_SPEED;
                if (message.x + message.width < 0) {
                    VexViewAPI.removeHUD(player, message.id);
                    messages.remove();
                } else {
                    this.sendText(player, state, message);
                }
            }
            if (state.messages.isEmpty()) {
                this.clearBackground(player, state);
                states.remove();
            }
        }
    }

    private void sendText(Player player, FlowState state, FlowMessage message) {
        int time = this.getFlowTime(state.width, message.width);
        VexViewAPI.sendHUD(player, new VexTextShow(message.id, Math.max(0, message.x), state.y, FLOW_Z,
                FlowLayout.visibleLines(message.text, message.x, state.height), time));
    }

    private void sendBackground(Player player, FlowState state) {
        byte[] image = this.getBackgroundImage(state.width, state.backgroundHeight);
        VexBase64Image background = new VexBase64Image(new ByteArrayInputStream(image), state.backgroundId,
                0, Math.max(0, state.y - 2), state.width, state.backgroundHeight);
        VexViewAPI.sendHUD(player, new VexBase64ImageShow(BACKGROUND_Z, background));
        state.backgroundSent = true;
    }

    private void clearFlow(Player player, FlowState state) {
        for (FlowMessage message : state.messages) {
            VexViewAPI.removeHUD(player, message.id);
        }
        this.clearBackground(player, state);
    }

    private void clearBackground(Player player, FlowState state) {
        if (state.backgroundSent) {
            VexViewAPI.removeHUD(player, state.backgroundId);
            state.backgroundSent = false;
        }
    }

    private byte[] getBackgroundImage(int width, int height) {
        String key = width + ":" + height;
        byte[] image = this.backgroundImages.get(key);
        if (image != null) {
            return image;
        }
        BufferedImage background = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = background.createGraphics();
        int alpha = (int) Math.round((1 - this.backgroundTransparency) * 255);
        graphics.setColor(new Color(0, 0, 0, alpha));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(background, "png", output);
            image = output.toByteArray();
        } catch (IOException exception) {
            image = new byte[0];
        }
        this.backgroundImages.put(key, image);
        return image;
    }

    private int getWindowWidth(Player player) {
        try {
            int width = VexViewAPI.getPlayerClientWindowWidth(player);
            return width > 0 ? Math.min(width, 4096) : DEFAULT_WINDOW_WIDTH;
        } catch (RuntimeException ignored) {
            return DEFAULT_WINDOW_WIDTH;
        }
    }

    private int getWindowHeight(Player player) {
        try {
            int height = VexViewAPI.getPlayerClientWindowHeight(player);
            return height > 0 ? Math.min(height, 2160) : DEFAULT_WINDOW_HEIGHT;
        } catch (RuntimeException ignored) {
            return DEFAULT_WINDOW_HEIGHT;
        }
    }

    private int getTextWidth(String text) {
        int current = 0;
        int max = 0;
        boolean format = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\r') {
                continue;
            }
            if (character == '\n') {
                max = Math.max(max, current);
                current = 0;
                continue;
            }
            if (format) {
                format = false;
                continue;
            }
            if (character == '\u00a7') {
                format = true;
                continue;
            }
            current += this.getCharacterWidth(character);
        }
        return Math.max(1, Math.max(max, current) + 2);
    }

    private int getCharacterWidth(char character) {
        return FlowLayout.characterWidth(character);
    }

    private int getFlowTime(int width, int textWidth) {
        long time = ((long) width + textWidth) / FLOW_SPEED + 40;
        return time > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) time;
    }

    private int limit(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double limit(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class FlowState {
        private final String backgroundId;
        private final List<FlowMessage> messages = new ArrayList<>();
        private int width;
        private int height;
        private int y;
        private int backgroundHeight;
        private boolean backgroundSent;

        private FlowState(UUID uuid) {
            this.backgroundId = "aervexhorn-flow-background-" + uuid;
        }
    }

    private static class FlowMessage {
        private final String id;
        private final String text;
        private final int width;
        private int x;

        private FlowMessage(String id, String text, int x, int width) {
            this.id = id;
            this.text = text;
            this.x = x;
            this.width = width;
        }
    }
}

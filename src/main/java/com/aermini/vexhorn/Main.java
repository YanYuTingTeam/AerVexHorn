package com.aermini.vexhorn;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lk.vexview.api.VexViewAPI;
import lk.vexview.hud.VexTextShow;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

public class Main extends JavaPlugin implements PluginMessageListener, Listener {
    private static final String CHANNEL = "servershout:cast";
    private static final int BROADCAST_TYPE = 1;
    private static final String HUD_ID = "aervexhorn:shouts";
    private final Map<UUID, Display> displays = new HashMap<>();
    private BukkitTask animation;
    private ShoutPlaceholders placeholders;
    private Set<String> channels = Collections.emptySet();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.channels = new HashSet<>(this.getConfig().getStringList("servershout.channels"));
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi != null && papi.isEnabled()) {
            try {
                placeholders = new ShoutPlaceholders(papi);
            } catch (ReflectiveOperationException incompatible) {
                getLogger().warning("PlaceholderAPI player expansion is unavailable");
            }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
    }

    @Override
    public void onDisable() {
        stopAnimation();
        for (UUID id : displays.keySet()) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) {
                clear(viewer);
            }
        }
        displays.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!this.isEnabled() || !CHANNEL.equals(channel) || data.length > Messenger.MAX_MESSAGE_SIZE) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = input.readUnsignedByte();
            String playerId = null;
            if (version == 2) {
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
                    || input.available() != 0) {
                return;
            }
            if (text.isEmpty()) {
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
            append(player, text);
        }
    }

    private void broadcast(String text) {
        if (!this.isEnabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline()) {
                append(player, text);
            }
        }
    }

    private void append(Player player, String text) {
        String line = ShoutLane.prepare(placeholders == null ? text : placeholders.resolve(player, text));
        if (line.isEmpty()) {
            return;
        }
        if (line.length() > ShoutLane.MAX_BACKLOG) {
            return;
        }
        displays.computeIfAbsent(player.getUniqueId(), id -> new Display()).lane.append(line);
        if (animation == null) {
            animation = Bukkit.getScheduler().runTaskTimer(this, this::render, 1L, 1L);
        }
    }

    private void render() {
        Iterator<Map.Entry<UUID, Display>> entries = displays.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Display> entry = entries.next();
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null || !viewer.isOnline()) {
                entries.remove();
                continue;
            }
            Display display = entry.getValue();
            display.updateWindow(viewer);
            ShoutLane lane = display.lane;
            VexViewAPI.sendHUD(viewer, new VexTextShow(HUD_ID, lane.hudX(), display.top + 2, 1,
                    Collections.singletonList(lane.visibleText(display.width)), 0));
            lane.advance(2, display.width);
            if (lane.isEmpty()) {
                clear(viewer);
                entries.remove();
            }
        }
        if (displays.isEmpty()) {
            stopAnimation();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (displays.remove(player.getUniqueId()) != null) {
            clear(player);
        }
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
    }

    private void clear(Player player) {
        VexViewAPI.removeHUD(player, HUD_ID);
        ShoutBackdrop.remove(player);
    }

    private static final class Display {
        final ShoutLane lane = new ShoutLane();
        int width;
        int top;
        int ticksUntilWindowCheck;

        void updateWindow(Player viewer) {
            if (ticksUntilWindowCheck-- > 0) {
                return;
            }
            ticksUntilWindowCheck = 9;
            int newWidth = width == 0 ? 320 : width;
            int newTop = width == 0 ? 36 : top;
            try {
                int w = VexViewAPI.getPlayerClientWindowWidth(viewer);
                int h = VexViewAPI.getPlayerClientWindowHeight(viewer);
                if (w > 1 && h > 0) {
                    newWidth = Math.min(w, 16384);
                    newTop = Math.min(h, 16384) * 15 / 100;
                }
            } catch (NullPointerException notReportedYet) {
                // VexView 2.7.3 unboxes a missing window report during login.
            }
            if (width != newWidth || top != newTop) {
                ShoutBackdrop.show(viewer, newWidth, newTop);
                width = newWidth;
                top = newTop;
            }
        }
    }
}

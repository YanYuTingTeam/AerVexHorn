package com.aermini.vexhorn;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lk.vexview.api.VexViewAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class Main extends JavaPlugin implements PluginMessageListener {
    private static final String CHANNEL = "servershout:cast";
    private static final int BROADCAST_TYPE = 1;
    private Set<String> channels = Collections.emptySet();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.channels = new HashSet<>(this.getConfig().getStringList("servershout.channels"));
        Bukkit.getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
    }

    @Override
    public void onDisable() {
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
            VexViewAPI.sendFlowView(player, text, 15);
        }
    }

    private void broadcast(String text) {
        if (!this.isEnabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline()) {
                VexViewAPI.sendFlowView(player, text, 15);
            }
        }
    }
}

package com.aermini.vexhorn;

import lk.vexview.api.VexViewAPI;
import lk.vexview.hud.VexMcImageShow;
import org.bukkit.entity.Player;

final class ShoutBackdrop {
    private static final String ID = "aervexhorn:shout-background";
    private static final String TEXTURE = "minecraft:textures/gui/achievement/achievement_background.png";

    private ShoutBackdrop() {}

    static void show(Player player, int screenWidth, int topY) {
        // In the stock 1.8.9 texture this texel is black with alpha 118/255.
        // Stretch it across the lane with the existing client-side texture.
        VexViewAPI.sendHUD(player, new VexMcImageShow(ID, TEXTURE, 0, topY, 0,
                256, 256, screenWidth, 14, 0, 1, 1, 16, 21));
    }

    static void remove(Player player) {
        VexViewAPI.removeHUD(player, ID);
    }
}

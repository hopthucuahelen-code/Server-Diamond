package com.serverdiamond.autodig;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AutoDig extends JavaPlugin {

    private static final int Y_THRESHOLD = 0;  // only active at this Y or below
    private static final int LENGTH = 3;        // blocks forward
    private static final int WIDTH = 3;         // blocks across (centered)
    private static final int HEIGHT = 1;        // blocks tall (from feet up)

    private final Set<UUID> enabled = new HashSet<>();

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 1L);
        getLogger().info("AutoDig enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /autodig.");
            return true;
        }
        UUID id = player.getUniqueId();
        if (enabled.remove(id)) {
            player.sendMessage("§eAutoDig §cOFF§e.");
        } else {
            enabled.add(id);
            player.sendMessage("§eAutoDig §aON§e. Active when you are at Y " + Y_THRESHOLD + " or below.");
        }
        return true;
    }

    private void tick() {
        if (enabled.isEmpty()) return;
        for (UUID id : enabled) {
            Player p = getServer().getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            if (p.getLocation().getBlockY() > Y_THRESHOLD) continue;
            clearArea(p);
        }
    }

    private void clearArea(Player player) {
        BlockFace f = player.getFacing();   // cardinal direction
        int fx = f.getModX(), fz = f.getModZ();
        int sx = fz, sz = -fx;              // perpendicular (sideways) axis

        Location loc = player.getLocation();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        World w = player.getWorld();
        int half = WIDTH / 2;

        for (int d = 1; d <= LENGTH; d++) {
            for (int s = -half; s <= half; s++) {
                for (int h = 0; h < HEIGHT; h++) {
                    int x = bx + fx * d + sx * s;
                    int y = by + h;
                    int z = bz + fz * d + sz * s;
                    clearBlock(w.getBlockAt(x, y, z));
                }
            }
        }
    }

    private void clearBlock(Block b) {
        Material t = b.getType();
        if (t.isAir()) return;
        if (t == Material.BEDROCK || t == Material.BARRIER) return; // safety
        if (t.name().contains("DIAMOND")) return;                   // keep diamond ore for the player
        b.setType(Material.AIR, false);                             // remove (incl. lava/water), no drops
    }
}

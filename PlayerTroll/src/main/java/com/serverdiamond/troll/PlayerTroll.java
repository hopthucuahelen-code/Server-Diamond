package com.serverdiamond.troll;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerTroll extends JavaPlugin implements TabCompleter, Listener {

    private static final int SKY_HEIGHT = 150;
    private static final int PIT_DEPTH = 30;

    // players currently in a skydrop: uuid -> their stashed (hidden) items
    private final Map<UUID, List<ItemStack>> skydropStash = new HashMap<>();

    private NamespacedKey diamondCreeperKey;

    // remember where each player died so they respawn on the spot
    private final Map<UUID, Location> deathLocations = new HashMap<>();

    @Override
    public void onEnable() {
        diamondCreeperKey = new NamespacedKey(this, "diamond_creeper");
        for (String c : new String[]{"clearall", "webcage", "smite", "pit", "skydrop", "tntbox", "killplayer", "diamondcreeper"}) {
            getCommand(c).setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("PlayerTroll enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /" + command.getName() + " <player> <user_name>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
            return true;
        }
        String userName = args[1];

        switch (command.getName().toLowerCase()) {
            case "clearall" -> {
                clearAll(target);
                alert(target, userName, "clear", NamedTextColor.RED, Sound.ENTITY_ITEM_BREAK);
            }
            case "webcage" -> {
                webCage(target);
                alert(target, userName, "web cage", NamedTextColor.GRAY, Sound.ENTITY_SPIDER_AMBIENT);
            }
            case "smite" -> {
                target.getWorld().strikeLightning(target.getLocation());
                alert(target, userName, "lightning", NamedTextColor.YELLOW, Sound.ENTITY_LIGHTNING_BOLT_THUNDER);
            }
            case "pit" -> {
                pit(target);
                alert(target, userName, "pit", NamedTextColor.DARK_GRAY, Sound.BLOCK_STONE_BREAK);
            }
            case "skydrop" -> {
                skydrop(target);
                alert(target, userName, "skydrop", NamedTextColor.AQUA, Sound.ENTITY_PHANTOM_FLAP);
            }
            case "tntbox" -> {
                tntBox(target);
                alert(target, userName, "tnt box", NamedTextColor.GOLD, Sound.ENTITY_TNT_PRIMED);
            }
            case "killplayer" -> {
                alert(target, userName, "instant kill", NamedTextColor.DARK_RED, Sound.ENTITY_WITHER_SPAWN);
                target.setHealth(0);
            }
            case "diamondcreeper" -> {
                int radius = 4;
                if (args.length >= 3) {
                    try {
                        radius = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage("§cRadius must be a number.");
                        return true;
                    }
                    if (radius <= 0) { sender.sendMessage("§cRadius must be > 0."); return true; }
                }
                spawnDiamondCreeper(target, radius);
                alert(target, userName, "diamond creeper", NamedTextColor.AQUA, Sound.ENTITY_CREEPER_PRIMED);
            }
            default -> { return false; }
        }
        sender.sendMessage("§aDone on §e" + target.getName());
        return true;
    }

    // ======================================================================
    //  Troll effects
    // ======================================================================

    private void clearAll(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
    }

    private void webCage(Player p) {
        World w = p.getWorld();
        Location l = p.getLocation();
        int bx = l.getBlockX(), by = l.getBlockY(), bz = l.getBlockZ();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                for (int y = -1; y <= 1; y++) {
                    Block b = w.getBlockAt(bx + x, by + y, bz + z);
                    if (b.getType() != Material.BEDROCK) b.setType(Material.COBWEB, false);
                }
    }

    private void pit(Player p) {
        World w = p.getWorld();
        Location l = p.getLocation();
        int bx = l.getBlockX(), by = l.getBlockY(), bz = l.getBlockZ();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                for (int dy = 0; dy <= PIT_DEPTH; dy++) {
                    int y = by - dy;
                    if (y < w.getMinHeight()) break;
                    Block b = w.getBlockAt(bx + x, y, bz + z);
                    if (b.getType() != Material.BEDROCK) b.setType(Material.AIR, false);
                }
    }

    private void tntBox(Player p) {
        World w = p.getWorld();
        Location base = p.getLocation();
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();

        int r = 4;       // interior horizontal radius -> 9x9
        int ih = 8;      // interior height (y 0..ih) -> 9 tall

        // clear the interior (also handles the "underground" case)
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++)
                for (int y = 0; y <= ih; y++) {
                    Block b = w.getBlockAt(bx + x, by + y, bz + z);
                    if (b.getType() != Material.BEDROCK) b.setType(Material.AIR, false);
                }

        // TNT shell enclosing the interior (7x7x7 outer, y -1..ih+1)
        for (int x = -(r + 1); x <= r + 1; x++)
            for (int z = -(r + 1); z <= r + 1; z++)
                for (int y = -1; y <= ih + 1; y++) {
                    boolean shell = Math.abs(x) == r + 1 || Math.abs(z) == r + 1 || y == -1 || y == ih + 1;
                    if (!shell) continue;
                    Block b = w.getBlockAt(bx + x, by + y, bz + z);
                    if (b.getType() != Material.BEDROCK) b.setType(Material.TNT, false);
                }

        // two charged creepers inside to detonate it
        for (int i = 0; i < 2; i++) {
            Creeper c = (Creeper) w.spawnEntity(base.clone().add(i == 0 ? 0.7 : -0.7, 0, 0), EntityType.CREEPER);
            c.setPowered(true);
        }
    }

    // ---- skydrop ----
    private void skydrop(Player p) {
        UUID id = p.getUniqueId();
        if (skydropStash.containsKey(id)) return; // already mid-drop

        PlayerInventory inv = p.getInventory();
        List<ItemStack> stash = new ArrayList<>();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) continue;
            if (it.getType() == Material.DIAMOND) continue; // keep diamonds visible/usable
            stash.add(it);
            inv.setItem(i, null);
        }
        skydropStash.put(id, stash);
        inv.addItem(new ItemStack(Material.WATER_BUCKET)); // for the MLG clutch

        // launch up
        World w = p.getWorld();
        Location dest = p.getLocation().clone();
        double y = Math.min(dest.getY() + SKY_HEIGHT, w.getMaxHeight() - 2);
        dest.setY(y);
        p.teleport(dest);
        p.setFallDistance(0f);

        // monitor for landing / survival
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline()) { skydropStash.remove(id); cancel(); return; }
                if (!skydropStash.containsKey(id)) { cancel(); return; } // died -> handled elsewhere
                t++;
                if (t > 10 && (p.isOnGround() || p.isInWater())) { restoreSkydrop(p); cancel(); return; }
                if (t > 600) { restoreSkydrop(p); cancel(); }            // 30s safety
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private void restoreSkydrop(Player p) {
        List<ItemStack> stash = skydropStash.remove(p.getUniqueId());
        if (stash == null) return;
        PlayerInventory inv = p.getInventory();
        inv.remove(Material.WATER_BUCKET);
        inv.remove(Material.BUCKET);
        for (ItemStack it : stash) {
            if (it == null) continue;
            for (ItemStack left : inv.addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }

    // ---- diamond creeper ----
    private void spawnDiamondCreeper(Player target, int blastRadius) {
        Creeper c = (Creeper) target.getWorld().spawnEntity(target.getLocation(), EntityType.CREEPER);
        c.setPowered(true);
        // charged creepers double the radius on explosion, so halve to match the requested blast
        c.setExplosionRadius(Math.max(1, blastRadius / 2));
        c.getPersistentDataContainer().set(diamondCreeperKey, PersistentDataType.BYTE, (byte) 1);
        c.setMaxFuseTicks(20); // ~1s
        c.setIgnited(true); // start the fuse so it explodes shortly
    }

    private boolean isDiamondCreeper(org.bukkit.entity.Entity ent) {
        return ent instanceof Creeper c
                && c.getPersistentDataContainer().has(diamondCreeperKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (!isDiamondCreeper(e.getEntity())) return;
        for (Block b : new ArrayList<>(e.blockList())) {
            if (b.getType() == Material.BEDROCK || b.getType().isAir()) continue;
            Material ore = b.getType().name().contains("DEEPSLATE")
                    ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE;
            b.setType(ore, false);
        }
        e.blockList().clear(); // turn blocks to diamond ore instead of destroying them
    }

    @EventHandler
    public void onExplosionDamage(EntityDamageByEntityEvent e) {
        if (isDiamondCreeper(e.getDamager())) {
            e.setCancelled(true); // no damage to anyone from the diamond creeper
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        deathLocations.put(id, p.getLocation()); // respawn here
        if (skydropStash.remove(id) != null) {
            // died during skydrop -> lose everything, including diamonds
            e.getDrops().clear();
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Location loc = deathLocations.remove(e.getPlayer().getUniqueId());
        if (loc == null || loc.getWorld() == null) return;
        Location safe = findSafe(loc);
        if (safe != null) {
            e.setRespawnLocation(safe);
        }
        // else: leave the default respawn (bed / world spawn) so we never land in lava
    }

    /** Find a safe standing spot at/above the death location, or null if none nearby. */
    private Location findSafe(Location loc) {
        World w = loc.getWorld();
        int x = loc.getBlockX(), z = loc.getBlockZ();
        int startY = loc.getBlockY();
        for (int y = startY; y <= startY + 25 && y < w.getMaxHeight() - 1; y++) {
            if (isSafeStanding(w, x, y, z)) {
                return new Location(w, x + 0.5, y, z + 0.5, loc.getYaw(), loc.getPitch());
            }
        }
        for (int y = startY - 1; y > w.getMinHeight() + 1; y--) {
            if (isSafeStanding(w, x, y, z)) {
                return new Location(w, x + 0.5, y, z + 0.5, loc.getYaw(), loc.getPitch());
            }
        }
        return null;
    }

    private boolean isSafeStanding(World w, int x, int y, int z) {
        Block feet = w.getBlockAt(x, y, z);
        Block head = w.getBlockAt(x, y + 1, z);
        Block ground = w.getBlockAt(x, y - 1, z);
        return feet.getType().isAir() && !isDangerous(feet)
                && head.getType().isAir() && !isDangerous(head)
                && ground.getType().isSolid() && !isDangerous(ground);
    }

    private boolean isDangerous(Block b) {
        Material t = b.getType();
        return t == Material.LAVA || t == Material.FIRE || t == Material.SOUL_FIRE
                || t == Material.MAGMA_BLOCK || t == Material.CACTUS || t == Material.WITHER_ROSE
                || t == Material.CAMPFIRE || t == Material.SOUL_CAMPFIRE;
    }

    // ======================================================================
    //  Alert title (same style as the other plugins)
    // ======================================================================

    private void alert(Player target, String userName, String trollName, TextColor color, Sound sound) {
        Component big = Component.text(userName, NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        Component small = Component.text("Send ", NamedTextColor.WHITE)
                .append(Component.text(target.getName() + " ", NamedTextColor.YELLOW))
                .append(Component.text(trollName, color));
        target.showTitle(Title.title(big, small,
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2500), Duration.ofMillis(500))));
        target.playSound(target.getLocation(), sound, 1.0f, 1.0f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase().startsWith(p)) out.add(pl.getName());
            }
        } else if (command.getName().equalsIgnoreCase("diamondcreeper") && args.length == 3) {
            for (String n : new String[]{"2", "4", "6", "10"}) {
                if (n.startsWith(args[2])) out.add(n);
            }
        }
        return out;
    }
}

package com.serverdiamond.giveitem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GiveItem extends JavaPlugin implements TabCompleter, Listener {

    private final Map<String, ItemDef> items = new LinkedHashMap<>();

    @FunctionalInterface
    private interface ItemGiver { void giveOne(Player p); }

    private record ItemDef(ItemGiver giver, TextColor color) {}

    private static final String SPEED_PICKAXE = "speed_pickaxe";
    private NamespacedKey specialKey;
    private final Set<UUID> respawnGive = new HashSet<>();
    private int digHasteLevel = 5; // haste amplifier for the Speed Pickaxe (higher = faster)

    @Override
    public void onEnable() {
        specialKey = new NamespacedKey(this, "special_item");
        digHasteLevel = getConfig().getInt("dig_haste_level", 5);
        registerItems();
        getCommand("giveitem").setTabCompleter(this);
        getCommand("randomitem").setTabCompleter(this);
        getCommand("removeitem").setTabCompleter(this);
        getCommand("digspeed").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::applyHaste, 20L, 20L);
        getLogger().info("GiveItem enabled with " + items.size() + " items.");
    }

    // ---- registration helpers ----
    private void add(String name, Material mat, TextColor color) {
        items.put(name, new ItemDef(p -> giveStack(p, new ItemStack(mat, 1)), color));
    }

    private void add(String name, TextColor color, ItemGiver giver) {
        items.put(name, new ItemDef(giver, color));
    }

    private void registerItems() {
        add("diamond",                 Material.DIAMOND,                  TextColor.color(0x5BE0E6));
        add("golden_apple",            Material.GOLDEN_APPLE,             NamedTextColor.GOLD);
        add("enchanted_golden_apple",  Material.ENCHANTED_GOLDEN_APPLE,   NamedTextColor.LIGHT_PURPLE);
        add("shield",                  Material.SHIELD,                   NamedTextColor.GRAY);
        add("totem",                   Material.TOTEM_OF_UNDYING,         TextColor.color(0xC8E64B));
        add("ender_pearl",             Material.ENDER_PEARL,              TextColor.color(0x2E8B6F));
        add("elytra",                  Material.ELYTRA,                   TextColor.color(0xC9A0DC));

        // full iron armor + iron sword
        add("iron_kit", TextColor.color(0xB0C4DE), p -> giveStack(p,
                new ItemStack(Material.IRON_HELMET),
                new ItemStack(Material.IRON_CHESTPLATE),
                new ItemStack(Material.IRON_LEGGINGS),
                new ItemStack(Material.IRON_BOOTS),
                new ItemStack(Material.IRON_SWORD)));

        // full netherite armor
        add("netherite_armor", NamedTextColor.DARK_GRAY, p -> giveStack(p,
                new ItemStack(Material.NETHERITE_HELMET),
                new ItemStack(Material.NETHERITE_CHESTPLATE),
                new ItemStack(Material.NETHERITE_LEGGINGS),
                new ItemStack(Material.NETHERITE_BOOTS)));

        // custom item: Speed Pickaxe (2x2 fast mining, deletes drops except diamonds)
        add("speed_pickaxe", NamedTextColor.AQUA, p -> giveStack(p, makeSpeedPickaxe()));
    }

    // ======================================================================
    //  Custom item: Speed Pickaxe
    // ======================================================================

    private ItemStack makeSpeedPickaxe() {
        ItemStack it = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("⚡ Speed Pickaxe", NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Mines 3x3 instantly", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Deletes all drops except diamond", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        m.setUnbreakable(true);
        m.addEnchant(Enchantment.EFFICIENCY, 5, true);
        m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(specialKey, PersistentDataType.STRING, SPEED_PICKAXE);
        it.setItemMeta(m);
        return it;
    }

    private String specialTag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(specialKey, PersistentDataType.STRING);
    }

    private boolean isSpeedPickaxe(ItemStack it) {
        return SPEED_PICKAXE.equals(specialTag(it));
    }

    private boolean isDiamond(Material m) {
        return m.name().contains("DIAMOND");
    }

    private boolean isProtected(Block b) {
        Material t = b.getType();
        return t.isAir() || b.isLiquid() || t == Material.BEDROCK || t == Material.BARRIER;
    }

    // ---- Haste while holding (fast, but not instant) ----
    private void applyHaste() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isSpeedPickaxe(p.getInventory().getItemInMainHand())) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.HASTE, 60, digHasteLevel, true, false, false));
            }
        }
    }

    // ---- 3x3 mining ----
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!isSpeedPickaxe(tool)) return;

        Block main = e.getBlock();
        // We control ALL drops ourselves: cancel vanilla drops, then manually
        // drop only diamond blocks/ores.
        e.setDropItems(false);
        if (isDiamond(main.getType())) dropDrops(main, tool);

        // break the surrounding 3x3 on the targeted face plane
        for (Block b : extraBlocks(p, main)) {
            if (isProtected(b)) continue;
            if (isDiamond(b.getType())) dropDrops(b, tool); // keep diamond drops
            b.setType(Material.AIR);                        // remove block, no other drops
        }
    }

    /** Drop a block's normal drops (using the given tool) at its location. */
    private void dropDrops(Block b, ItemStack tool) {
        for (ItemStack d : b.getDrops(tool)) {
            b.getWorld().dropItemNaturally(b.getLocation(), d);
        }
    }

    private List<Block> extraBlocks(Player p, Block main) {
        BlockFace face = p.getFacing().getOppositeFace();
        RayTraceResult r = p.rayTraceBlocks(6.0);
        if (r != null && main.equals(r.getHitBlock()) && r.getHitBlockFace() != null) {
            face = r.getHitBlockFace();
        }

        int ux, uy, uz, vx, vy, vz;
        switch (face) {
            case UP, DOWN -> { ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = 1; }
            case NORTH, SOUTH -> { ux = 1; uy = 0; uz = 0; vx = 0; vy = 1; vz = 0; }
            default -> { ux = 0; uy = 0; uz = 1; vx = 0; vy = 1; vz = 0; } // EAST/WEST
        }

        // 3x3 centred on the broken block (8 surrounding blocks on the face plane)
        List<Block> list = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                list.add(main.getRelative(i * ux + j * vx, i * uy + j * vy, i * uz + j * vz));
            }
        }
        return list;
    }

    // ---- dropping it (Q) deletes it ----
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isSpeedPickaxe(e.getItemDrop().getItemStack())) {
            e.getItemDrop().remove();
        }
    }

    // ---- keep through death: don't drop, give back on respawn ----
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        boolean had = e.getDrops().removeIf(this::isSpeedPickaxe);
        if (had) respawnGive.add(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (respawnGive.remove(p.getUniqueId())) {
            getServer().getScheduler().runTask(this, () -> {
                if (!hasSpeedPickaxe(p)) p.getInventory().addItem(makeSpeedPickaxe());
            });
        }
    }

    private boolean hasSpeedPickaxe(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isSpeedPickaxe(it)) return true;
        }
        return false;
    }

    /** Add items to the inventory; overflow drops at the player's feet. */
    private void giveStack(Player p, ItemStack... stacks) {
        var leftover = p.getInventory().addItem(stacks);
        for (ItemStack rem : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rem);
        }
    }

    // ======================================================================
    //  Commands
    // ======================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("randomitem")) return handleRandom(sender, args);
        if (name.equals("removeitem")) return handleRemove(sender, args);
        if (name.equals("digspeed")) return handleDigSpeed(sender, args);
        return handleGive(sender, args);
    }

    private boolean handleDigSpeed(CommandSender sender, String[] args) {
        // /digspeed <level>   (higher = faster; 5 ≈ default, 10-20 = near instant)
        if (args.length != 1) {
            sender.sendMessage("§eCurrentSpeed Pickaxe dig speed (level): §f" + digHasteLevel);
            sender.sendMessage("§7Usage: /digspeed <level>  (higher = faster, e.g. 5, 10, 20)");
            return true;
        }
        int lvl;
        try {
            lvl = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLevel must be a whole number.");
            return true;
        }
        if (lvl < 0) lvl = 0;
        if (lvl > 255) lvl = 255;
        digHasteLevel = lvl;
        getConfig().set("dig_haste_level", lvl);
        saveConfig();
        sender.sendMessage("§aSpeed Pickaxe dig speed set to §f" + lvl + "§a (higher = faster).");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        // /removeitem <player> <item>
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /removeitem <player> <item>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
            return true;
        }
        String key = args[1].toLowerCase();
        int removed = 0;
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && key.equals(specialTag(it))) {
                target.getInventory().setItem(i, null);
                removed += it.getAmount();
            }
        }
        if (removed == 0) {
            sender.sendMessage("§eNo special '" + key + "' found on §f" + target.getName()
                    + "§e. (removeitem only works on special items like speed_pickaxe)");
        } else {
            sender.sendMessage("§aRemoved §f" + removed + " §a" + key + " from §e" + target.getName());
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        // /giveitem <player> <user_name> <item> <amount>
        if (args.length != 4) {
            sender.sendMessage("§cUsage: /giveitem <player> <user_name> <item> <amount>");
            sender.sendMessage("§7Items: §f" + String.join(", ", items.keySet()));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
            return true;
        }
        String userName = args[1];
        String key = args[2].toLowerCase();
        ItemDef def = items.get(key);
        if (def == null) {
            sender.sendMessage("§cUnknown item '" + args[2] + "'.");
            sender.sendMessage("§7Items: §f" + String.join(", ", items.keySet()));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cAmount must be a number.");
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage("§cAmount must be at least 1.");
            return true;
        }
        giveItems(target, userName, def.giver(), key, def.color(), amount);
        sender.sendMessage("§aGiving §f" + amount + " §a" + key + " to §e" + target.getName());
        return true;
    }

    private boolean handleRandom(CommandSender sender, String[] args) {
        // /randomitem <player> <user_name> <item> <max_amount>
        if (args.length != 4) {
            sender.sendMessage("§cUsage: /randomitem <player> <user_name> <item> <max_amount>");
            sender.sendMessage("§7Items: §f" + String.join(", ", items.keySet()));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
            return true;
        }
        String userName = args[1];
        String key = args[2].toLowerCase();
        ItemDef def = items.get(key);
        if (def == null) {
            sender.sendMessage("§cUnknown item '" + args[2] + "'.");
            sender.sendMessage("§7Items: §f" + String.join(", ", items.keySet()));
            return true;
        }
        int max;
        try {
            max = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cMax amount must be a number.");
            return true;
        }
        if (max <= 0) {
            sender.sendMessage("§cMax amount must be at least 1.");
            return true;
        }
        spinItem(target, userName, def, key, max, 0);
        sender.sendMessage("§aRandom item spinning for §e" + target.getName());
        return true;
    }

    // ======================================================================
    //  Giving (one unit per tick, with countdown title)
    // ======================================================================

    private void giveItems(Player target, String userName, ItemGiver giver, String itemName, TextColor color, int amount) {
        new BukkitRunnable() {
            int given = 0;

            @Override
            public void run() {
                if (!target.isOnline()) {
                    cancel();
                    return;
                }
                if (given < amount) {
                    giver.giveOne(target);
                    target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    given++;
                }
                int remaining = amount - given;
                boolean done = given >= amount;
                showAlert(target, userName, itemName, color, remaining, done);
                if (done) cancel();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ======================================================================
    //  Random spin (big number only), then give that many
    // ======================================================================

    private void spinItem(Player target, String userName, ItemDef def, String itemName, int max, int idx) {
        if (!target.isOnline()) return;

        int[] delays = {2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 4, 6, 9, 13};
        int number = 1 + (int) (Math.random() * max);

        if (idx >= delays.length) {
            showBigNumber(target, number, true);
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            int finalAmount = number;
            getServer().getScheduler().runTaskLater(this,
                    () -> giveItems(target, userName, def.giver(), itemName, def.color(), finalAmount), 12L);
            return;
        }

        showBigNumber(target, number, false);
        float pitch = (float) Math.min(2.0, 0.8 + (idx / (double) delays.length) * 1.2);
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
        getServer().getScheduler().runTaskLater(this,
                () -> spinItem(target, userName, def, itemName, max, idx + 1), delays[idx]);
    }

    private void showBigNumber(Player target, int number, boolean finalFrame) {
        Component big = Component.text(String.valueOf(number), NamedTextColor.RED).decorate(TextDecoration.BOLD);
        Title.Times times = finalFrame
                ? Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ZERO)
                : Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ZERO);
        target.showTitle(Title.title(big, Component.empty(), times));
    }

    // ======================================================================
    //  Title alert (same style as the mob plugin)
    // ======================================================================

    private void showAlert(Player target, String userName, String itemName, TextColor itemColor, int number, boolean finalFrame) {
        Component big = Component.text(userName, NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        Component small = Component.text("Send ", NamedTextColor.WHITE)
                .append(Component.text(target.getName() + " ", NamedTextColor.YELLOW))
                .append(Component.text(itemName + " ", itemColor))
                .append(Component.text(String.valueOf(number), NamedTextColor.RED));
        Title.Times times = finalFrame
                ? Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(500))
                : Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ZERO);
        target.showTitle(Title.title(big, small, times));
    }

    // ======================================================================
    //  Tab completion
    // ======================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = command.getName().toLowerCase();

        if (name.equals("digspeed")) {
            if (args.length == 1) {
                for (String n : new String[]{"5", "10", "15", "20", "30"}) {
                    if (n.startsWith(args[0])) out.add(n);
                }
            }
            return out;
        }

        if (args.length == 1) {
            String p = args[0].toLowerCase();
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase().startsWith(p)) out.add(pl.getName());
            }
            return out;
        }

        if (name.equals("removeitem")) {
            if (args.length == 2) {
                String p = args[1].toLowerCase();
                for (String it : items.keySet()) if (it.startsWith(p)) out.add(it);
            }
            return out;
        }

        // giveitem / randomitem
        if (args.length == 3) {
            String p = args[2].toLowerCase();
            for (String it : items.keySet()) if (it.startsWith(p)) out.add(it);
        } else if (args.length == 4) {
            for (String n : new String[]{"1", "5", "10", "16", "32", "64"}) {
                if (n.startsWith(args[3])) out.add(n);
            }
        }
        return out;
    }
}

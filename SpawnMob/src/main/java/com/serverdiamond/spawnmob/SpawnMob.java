package com.serverdiamond.spawnmob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpawnMob extends JavaPlugin implements TabCompleter {

    /** name (lowercase) -> [spawn behaviour, display color] for the allowed hostile mobs. */
    private final Map<String, MobDef> mobs = new LinkedHashMap<>();

    @FunctionalInterface
    private interface MobSpawner { void spawn(World world, Location loc); }

    private record MobDef(MobSpawner spawner, TextColor color) {}

    @Override
    public void onEnable() {
        registerMobs();
        getCommand("spawnmob").setTabCompleter(this);
        getCommand("randommob").setTabCompleter(this);
        getCommand("tnt").setTabCompleter(this);
        getCommand("randomtnt").setTabCompleter(this);
        getLogger().info("SpawnMob enabled with " + mobs.size() + " hostile mobs.");
    }

    /** Simple mob: just spawn the entity type. */
    private void add(String name, EntityType type, TextColor color) {
        mobs.put(name, new MobDef((w, l) -> w.spawnEntity(l, type), color));
    }

    /** Custom variant: provide your own spawn behaviour. */
    private void add(String name, TextColor color, MobSpawner spawner) {
        mobs.put(name, new MobDef(spawner, color));
    }

    /** Dress a mob in full iron armor with a weapon (no gear drops). */
    private void equipIron(LivingEntity e, ItemStack weapon) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) return;
        eq.setHelmet(new ItemStack(Material.IRON_HELMET));
        eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        eq.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        eq.setBoots(new ItemStack(Material.IRON_BOOTS));
        eq.setItemInMainHand(weapon);
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
    }

    private void registerMobs() {
        add("zombie",            EntityType.ZOMBIE,            NamedTextColor.DARK_GREEN);
        add("husk",              EntityType.HUSK,              TextColor.color(0xC8B273));
        add("zombie_villager",   EntityType.ZOMBIE_VILLAGER,   NamedTextColor.GREEN);
        add("drowned",           EntityType.DROWNED,           TextColor.color(0x4FA39A));
        add("skeleton",          EntityType.SKELETON,          NamedTextColor.WHITE);
        add("stray",             EntityType.STRAY,             NamedTextColor.AQUA);
        add("wither_skeleton",   EntityType.WITHER_SKELETON,   NamedTextColor.DARK_GRAY);
        add("creeper",           EntityType.CREEPER,           NamedTextColor.GREEN);
        add("spider",            EntityType.SPIDER,            NamedTextColor.DARK_GRAY);
        add("cave_spider",       EntityType.CAVE_SPIDER,       NamedTextColor.DARK_AQUA);
        add("witch",             EntityType.WITCH,             NamedTextColor.DARK_PURPLE);
        add("magma_cube",        EntityType.MAGMA_CUBE,        NamedTextColor.GOLD);
        add("blaze",             EntityType.BLAZE,             NamedTextColor.GOLD);
        add("ghast",             EntityType.GHAST,             NamedTextColor.WHITE);
        add("phantom",           EntityType.PHANTOM,           TextColor.color(0x2E8B9E));
        add("pillager",          EntityType.PILLAGER,          NamedTextColor.GRAY);
        add("vindicator",        EntityType.VINDICATOR,        NamedTextColor.GRAY);
        add("evoker",            EntityType.EVOKER,            NamedTextColor.WHITE);
        add("vex",               EntityType.VEX,               NamedTextColor.AQUA);
        add("ravager",           EntityType.RAVAGER,           TextColor.color(0x6B4B3A));
        add("guardian",          EntityType.GUARDIAN,          NamedTextColor.DARK_AQUA);
        add("elder_guardian",    EntityType.ELDER_GUARDIAN,    NamedTextColor.DARK_AQUA);
        add("shulker",           EntityType.SHULKER,           NamedTextColor.LIGHT_PURPLE);
        add("piglin",            EntityType.PIGLIN,            NamedTextColor.GOLD);
        add("piglin_brute",      EntityType.PIGLIN_BRUTE,      NamedTextColor.GOLD);
        add("hoglin",            EntityType.HOGLIN,            TextColor.color(0xC68A6A));
        add("zoglin",            EntityType.ZOGLIN,            NamedTextColor.LIGHT_PURPLE);
        add("zombified_piglin",  EntityType.ZOMBIFIED_PIGLIN,  NamedTextColor.GOLD);
        add("warden",            EntityType.WARDEN,            NamedTextColor.DARK_AQUA);
        add("wither",            EntityType.WITHER,            NamedTextColor.DARK_GRAY);

        // ---- rare / special variants ----
        add("charged_creeper", TextColor.color(0x00E5FF), (w, l) -> {
            Creeper c = (Creeper) w.spawnEntity(l, EntityType.CREEPER);
            c.setPowered(true);
        });
        add("chicken_jockey", NamedTextColor.YELLOW, (w, l) -> {
            Chicken chicken = (Chicken) w.spawnEntity(l, EntityType.CHICKEN);
            Zombie baby = (Zombie) w.spawnEntity(l, EntityType.ZOMBIE);
            baby.setBaby();
            chicken.addPassenger(baby);
        });
        add("spider_jockey", NamedTextColor.DARK_GRAY, (w, l) -> {
            Spider spider = (Spider) w.spawnEntity(l, EntityType.SPIDER);
            Skeleton rider = (Skeleton) w.spawnEntity(l, EntityType.SKELETON);
            spider.addPassenger(rider);
        });
        add("ravager_rider", TextColor.color(0x8B5A2B), (w, l) -> {
            Ravager ravager = (Ravager) w.spawnEntity(l, EntityType.RAVAGER);
            Pillager rider = (Pillager) w.spawnEntity(l, EntityType.PILLAGER);
            ravager.addPassenger(rider);
        });
        add("armored_zombie", TextColor.color(0xB0C4DE), (w, l) -> {
            Zombie z = (Zombie) w.spawnEntity(l, EntityType.ZOMBIE);
            equipIron(z, new ItemStack(Material.IRON_SWORD));
        });
        add("armored_skeleton", NamedTextColor.WHITE, (w, l) -> {
            Skeleton sk = (Skeleton) w.spawnEntity(l, EntityType.SKELETON);
            equipIron(sk, new ItemStack(Material.BOW));
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("randommob")) return handleRandom(sender, args);
        if (name.equals("tnt")) return handleTnt(sender, args);
        if (name.equals("randomtnt")) return handleRandomTnt(sender, args);
        return handleSpawn(sender, args);
    }

    // Default TNT params used by /randomtnt.
    private static final int RTNT_FUSE_TICKS = 40;   // 2 seconds
    private static final float RTNT_POWER = 2.0f;
    private static final int RTNT_SPEED = 2;         // ticks between spawns
    private static final int RTNT_MIN = 10, RTNT_MAX = 200;

    private boolean handleRandomTnt(CommandSender sender, String[] args) {
        // /randomtnt <player> <user_name>
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /randomtnt <player> <user_name>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
            return true;
        }
        spinTnt(target, args[1], 0);
        sender.sendMessage("§aRandom TNT spinning for §e" + target.getName());
        return true;
    }

    /** Slot spin showing only a big random number, then spawns that many TNT. */
    private void spinTnt(Player target, String userName, int idx) {
        if (!target.isOnline()) return;

        int[] delays = {2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 4, 6, 9, 13};
        Location loc = target.getLocation();
        int number = RTNT_MIN + (int) (Math.random() * (RTNT_MAX - RTNT_MIN + 1));

        if (idx >= delays.length) {
            // Final number: reveal it big, then spawn that many TNT like normal.
            showBigNumber(target, number, true);
            target.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            int finalAmount = number;
            getServer().getScheduler().runTaskLater(this,
                    () -> spawnTnt(target, userName, finalAmount, RTNT_FUSE_TICKS, RTNT_POWER, RTNT_SPEED), 12L);
            return;
        }

        showBigNumber(target, number, false);
        float pitch = (float) Math.min(2.0, 0.8 + (idx / (double) delays.length) * 1.2);
        target.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
        getServer().getScheduler().runTaskLater(this, () -> spinTnt(target, userName, idx + 1), delays[idx]);
    }

    /** Big centered number only (no subtitle), used while spinning the random TNT amount. */
    private void showBigNumber(Player target, int number, boolean finalFrame) {
        Component big = Component.text(String.valueOf(number), NamedTextColor.RED).decorate(TextDecoration.BOLD);
        Title.Times times = finalFrame
                ? Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ZERO)
                : Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ZERO);
        target.showTitle(Title.title(big, Component.empty(), times));
    }

    private boolean handleTnt(CommandSender sender, String[] args) {
        // /tnt <player> <user_name> <amount> <fuse_seconds> <power> <spawn_speed_ticks>
        if (args.length != 6) {
            sender.sendMessage("§cUsage: /tnt <player> <user_name> <amount> <fuse_seconds> <power> <spawn_speed_ticks>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
            return true;
        }
        String userName = args[1];
        int amount;
        double fuseSeconds;
        float power;
        int speed;
        try {
            amount = Integer.parseInt(args[2]);
            fuseSeconds = Double.parseDouble(args[3]);
            power = Float.parseFloat(args[4]);
            speed = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cAmount/fuse/power/speed must be numbers.");
            return true;
        }
        if (amount <= 0 || fuseSeconds <= 0 || power <= 0 || speed <= 0) {
            sender.sendMessage("§cAmount, fuse, power and speed must be greater than 0.");
            return true;
        }
        int fuseTicks = Math.max(1, (int) Math.round(fuseSeconds * 20));
        spawnTnt(target, userName, amount, fuseTicks, power, speed);
        sender.sendMessage("§aSpawning §f" + amount + " §aTNT (fuse " + fuseSeconds + "s, power " + power + ", every " + speed + " ticks) on §e" + target.getName());
        return true;
    }

    private void spawnTnt(Player target, String userName, int amount, int fuseTicks, float power, int speed) {
        new BukkitRunnable() {
            int spawned = 0;

            @Override
            public void run() {
                if (!target.isOnline()) {
                    cancel();
                    return;
                }
                if (spawned < amount) {
                    Location loc = target.getLocation();
                    org.bukkit.entity.TNTPrimed tnt = target.getWorld().spawn(loc, org.bukkit.entity.TNTPrimed.class);
                    tnt.setFuseTicks(fuseTicks);
                    tnt.setYield(power);
                    target.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    spawned++;
                }
                int remaining = amount - spawned;
                boolean done = spawned >= amount;
                showAlert(target, userName, "tnt", NamedTextColor.GOLD, remaining, done);
                if (done) cancel();
            }
        }.runTaskTimer(this, 0L, speed);
    }

    private boolean handleSpawn(CommandSender sender, String[] args) {
        // /spawnmob <player> <user_name> <mob> <amount>
        if (args.length != 4) {
            sender.sendMessage("§cUsage: /spawnmob <player> <user_name> <mob> <amount>");
            sender.sendMessage("§7Mobs: §f" + String.join(", ", mobs.keySet()));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
            return true;
        }

        String userName = args[1];

        String mobKey = args[2].toLowerCase();
        MobDef def = mobs.get(mobKey);
        if (def == null) {
            sender.sendMessage("§cUnknown mob '" + args[2] + "'.");
            sender.sendMessage("§7Mobs: §f" + String.join(", ", mobs.keySet()));
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

        spawnMobs(target, userName, def.spawner(), mobKey, def.color(), amount);

        sender.sendMessage("§aSpawning §f" + amount + " §a" + mobKey + " on §e" + target.getName());
        return true;
    }

    private boolean handleRandom(CommandSender sender, String[] args) {
        // /randommob <player> <user_name> <amount>
        if (args.length != 3) {
            sender.sendMessage("§cUsage: /randommob <player> <user_name> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[0] + "' is not online.");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cAmount must be a number.");
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage("§cAmount must be at least 1.");
            return true;
        }
        spinRandom(target, args[1], amount);
        sender.sendMessage("§aRandom mob spinning for §e" + target.getName());
        return true;
    }

    /** Slot-machine spin: cycle mob names fast, decelerate, land on one, then spawn <amount>. */
    private void spinRandom(Player target, String userName, int amount) {
        List<String> keys = new ArrayList<>(mobs.keySet());
        List<Integer> delays = new ArrayList<>();
        for (int i = 0; i < 12; i++) delays.add(2);          // fast phase (~1.2s)
        for (int t : new int[]{3, 4, 6, 9, 13}) delays.add(t); // decelerate (~1.7s)
        spinStep(target, userName, keys, delays, 0, amount);
    }

    private void spinStep(Player target, String userName, List<String> keys, List<Integer> delays, int idx, int amount) {
        if (!target.isOnline()) return;

        String key = keys.get((int) (Math.random() * keys.size()));
        MobDef def = mobs.get(key);
        boolean last = idx >= delays.size();
        Location loc = target.getLocation();

        if (last) {
            // Landed on the result: spawn <amount> of this mob (with the countdown title + EXP sound).
            spawnMobs(target, userName, def.spawner(), key, def.color(), amount);
            return;
        }

        // Spinning frame: show the cycling mob name, with a rising pling pitch.
        showAlert(target, userName, key, def.color(), amount, false);
        float pitch = (float) Math.min(2.0, 0.8 + (idx / (double) delays.size()) * 1.2);
        target.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);

        getServer().getScheduler().runTaskLater(this,
                () -> spinStep(target, userName, keys, delays, idx + 1, amount), delays.get(idx));
    }

    private void spawnMobs(Player target, String userName, MobSpawner spawner, String mobName, TextColor color, int amount) {
        // Spawn one mob per tick at the player's feet; the title stays up the whole
        // time and the number counts down as mobs appear.
        new BukkitRunnable() {
            int spawned = 0;

            @Override
            public void run() {
                if (!target.isOnline()) {
                    cancel();
                    return;
                }
                if (spawned < amount) {
                    Location loc = target.getLocation();
                    spawner.spawn(target.getWorld(), loc);
                    target.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    spawned++;
                }
                int remaining = amount - spawned;
                boolean done = spawned >= amount;
                showAlert(target, userName, mobName, color, remaining, done);
                if (done) cancel();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    /** Big <user_name> line (green) + small "Send <player> <mob> <number>" line. */
    private void showAlert(Player target, String userName, String mobName, TextColor mobColor, int number, boolean finalFrame) {
        Component big = Component.text(userName, NamedTextColor.GREEN).decorate(TextDecoration.BOLD);

        Component small = Component.text("Send ", NamedTextColor.WHITE)
                .append(Component.text(target.getName() + " ", NamedTextColor.YELLOW))
                .append(Component.text(mobName + " ", mobColor))
                .append(Component.text(String.valueOf(number), NamedTextColor.RED));

        // While spawning: refresh every tick with no fade (solid). On the last frame:
        // let it linger and fade out nicely.
        Title.Times times = finalFrame
                ? Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(500))
                : Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ZERO);

        target.showTitle(Title.title(big, small, times));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = command.getName().toLowerCase();

        if (args.length == 1) {
            String p = args[0].toLowerCase();
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase().startsWith(p)) out.add(pl.getName());
            }
            return out;
        }

        switch (name) {
            case "spawnmob" -> {
                if (args.length == 3) {
                    String p = args[2].toLowerCase();
                    for (String m : mobs.keySet()) if (m.startsWith(p)) out.add(m);
                } else if (args.length == 4) {
                    suggest(out, args[3], "1", "5", "10", "20", "50");
                }
            }
            case "randommob" -> {
                if (args.length == 3) suggest(out, args[2], "1", "5", "10", "20", "50");
            }
            case "tnt" -> {
                if (args.length == 3) suggest(out, args[2], "1", "5", "10", "20");       // amount
                else if (args.length == 4) suggest(out, args[3], "0.5", "1", "2", "4");   // fuse seconds
                else if (args.length == 5) suggest(out, args[4], "2", "4", "8", "16");    // power
                else if (args.length == 6) suggest(out, args[5], "2", "3", "5", "10");    // spawn speed (ticks)
            }
        }
        return out;
    }

    private void suggest(List<String> out, String current, String... options) {
        for (String o : options) if (o.startsWith(current)) out.add(o);
    }
}

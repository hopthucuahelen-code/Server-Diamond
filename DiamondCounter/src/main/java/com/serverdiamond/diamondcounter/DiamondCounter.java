package com.serverdiamond.diamondcounter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class DiamondCounter extends JavaPlugin {

    private int goal = 64;
    private static final int PORT = 8080;
    private static final TextColor OCEAN = TextColor.color(0x29ABE2); // "xanh nuoc bien"

    private OverlayServer overlay;

    // --- live state (all mutated on the main thread) ---
    private String trackedName = null;
    private int count = 0;          // net diamond counter (can go negative)
    private int wins = 0;
    private int winGoal = 0;        // target number of wins (0 = no target shown)
    private int lastInv = 0;        // last observed inventory diamond total

    // --- countdown state ---
    private boolean counting = false;
    private BukkitTask countdownTask = null;

    @Override
    public void onEnable() {
        goal = Math.max(1, getConfig().getInt("goal", 64));
        winGoal = Math.max(0, getConfig().getInt("win_goal", 0));
        String html = loadResource("overlay.html");
        if (html == null) {
            getLogger().severe("overlay.html missing from jar! Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        overlay = new OverlayServer(PORT, html, this::stateJson, getLogger());
        try {
            overlay.start();
        } catch (IOException e) {
            getLogger().severe("Could not start overlay web server on port " + PORT + ": " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Poll the tracked player's inventory 5x/second to detect diamond changes.
        getServer().getScheduler().runTaskTimer(this, this::scanInventory, 20L, 4L);

        getLogger().info("DiamondCounter enabled. Overlay: http://localhost:" + PORT);
    }

    @Override
    public void onDisable() {
        if (overlay != null) overlay.stop();
    }

    // ======================================================================
    //  Inventory tracking
    // ======================================================================

    private void scanInventory() {
        if (trackedName == null) return;
        Player p = Bukkit.getPlayerExact(trackedName);
        if (p == null) return; // offline -> keep state frozen

        int cur = countDiamonds(p);
        int delta = cur - lastInv;
        if (delta != 0) {
            lastInv = cur;
            changeCount(delta);
        }
    }

    private int countDiamonds(Player p) {
        int total = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == Material.DIAMOND) {
                total += it.getAmount();
            }
        }
        return total;
    }

    /** Add diamonds to the player's inventory; any overflow is dropped at their feet. */
    private void giveDiamonds(Player p, int amount) {
        var leftover = p.getInventory().addItem(new ItemStack(Material.DIAMOND, amount));
        for (ItemStack rem : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rem);
        }
    }

    /** Remove up to {@code amount} diamonds from the player's inventory. */
    private void removeDiamonds(Player p, int amount) {
        p.getInventory().removeItem(new ItemStack(Material.DIAMOND, amount));
    }

    // ======================================================================
    //  Core counter logic
    // ======================================================================

    /** Apply a diamond delta, fire the floating popup, and drive the win countdown. */
    private void changeCount(int delta) {
        if (delta == 0) return;
        count += delta;
        broadcast(delta);

        if (counting && count < goal) {
            cancelCountdown();
        } else if (!counting && count >= goal) {
            startCountdown();
        }
    }

    // ======================================================================
    //  WIN countdown (in-game only)
    // ======================================================================

    private void startCountdown() {
        if (counting) return;
        counting = true;
        countStep(15);
    }

    private void countStep(int n) {
        if (!counting) return;
        if (count < goal) { cancelCountdown(); return; }

        if (n <= 0) {
            completeWin();
            return;
        }

        showCountdownNumber(n);
        playCountdownSound(n);

        long delay = (n <= 3) ? 60L : 20L; // 3,2,1 last ~3s each; the rest 1s.
        countdownTask = getServer().getScheduler().runTaskLater(this, () -> countStep(n - 1), delay);
    }

    private void cancelCountdown() {
        counting = false;
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        Player p = tracked();
        if (p != null) p.clearTitle();
    }

    private void completeWin() {
        counting = false;
        countdownTask = null;
        wins++;
        count -= goal; // carry the remainder (e.g. 70 -> 6)

        Player p = tracked();
        if (p != null) {
            removeDiamonds(p, goal);     // consume the 64 real diamonds for this win
            lastInv = countDiamonds(p);  // resync so the scanner does not subtract again
            p.showTitle(Title.title(
                    Component.text("WIN", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(3000), Duration.ofMillis(500))
            ));
        }
        broadcastWin(); // trigger the big diamond-burst "-64" effect

        // chain another win if the player banked multiple stacks at once
        if (count >= goal) startCountdown();
    }

    private void showCountdownNumber(int n) {
        Player p = tracked();
        if (p == null) return;
        TextColor color = (n <= 3) ? NamedTextColor.RED : NamedTextColor.YELLOW;
        p.showTitle(Title.title(
                Component.text(String.valueOf(n), color).decorate(TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofMillis(150))
        ));
    }

    private void playCountdownSound(int n) {
        Player p = tracked();
        if (p == null) return;
        if (n <= 3) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.6f);
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.6f);
        }
    }

    // ======================================================================
    //  Commands
    // ======================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("diamond")) return handleDiamond(sender, args);
        if (name.equals("win")) return handleWin(sender, args);
        return false;
    }

    private boolean handleDiamond(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("track")) {
            if (args.length < 2) { sender.sendMessage("§cUsage: /diamond track <player>"); return true; }
            startTracking(args[1]);
            sender.sendMessage("§aNow tracking §e" + trackedName + "§a. Counter: §f" + count + "/" + goal + " §aWins: §f" + wins);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("untrack")) {
            trackedName = null;
            sender.sendMessage("§aTracking stopped.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§bTracked: §f" + (trackedName == null ? "(none)" : trackedName)
                    + " §bCount: §f" + count + "/" + goal + " §bWins: §f" + wins);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("goal")) {
            if (args.length < 2) { sender.sendMessage("§eCurrent goal: §f" + goal + "§e. Usage: /diamond goal <number>"); return true; }
            int g;
            try { g = Integer.parseInt(args[1]); } catch (NumberFormatException e) { sender.sendMessage("§cGoal must be a number."); return true; }
            if (g < 1) { sender.sendMessage("§cGoal must be at least 1."); return true; }
            goal = g;
            getConfig().set("goal", g);
            saveConfig();
            if (counting && count < goal) cancelCountdown();
            else if (!counting && count >= goal) startCountdown();
            broadcast(0);
            sender.sendMessage("§aWin goal set to §f" + goal + " §adiamonds.");
            return true;
        }

        // TikFinity gift: /diamond <player> <user_name> <+/-> <amount>
        if (args.length == 4) {
            int sign = parseSign(args[2]);
            Integer amount = parseAmount(args[3]);
            if (sign == 0 || amount == null) { sender.sendMessage("§cUsage: /diamond <player> <user_name> <+/-> <amount>"); return true; }
            int delta = sign * amount;

            // Physically give / take diamonds from the player's inventory.
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null && amount > 0) {
                if (sign > 0) giveDiamonds(target, amount);
                else removeDiamonds(target, amount);
                // Resync so the inventory scanner does not double-count this change.
                if (target.getName().equalsIgnoreCase(trackedName)) {
                    lastInv = countDiamonds(target);
                }
            }

            showGiftTitle(args[0], args[1], delta, "diamond", false);
            changeCount(delta);
            return true;
        }

        sender.sendMessage("§cUsage:");
        sender.sendMessage("§7  /diamond track <player>");
        sender.sendMessage("§7  /diamond untrack | status");
        sender.sendMessage("§7  /diamond <player> <user_name> <+/-> <amount>");
        return true;
    }

    private boolean handleWin(CommandSender sender, String[] args) {
        // /win goal <number>   -> set the WIN target (0 = no target)
        if (args.length >= 1 && args[0].equalsIgnoreCase("goal")) {
            if (args.length < 2) { sender.sendMessage("§eCurrent win target: §f" + (winGoal > 0 ? winGoal : "(none)") + "§e. Usage: /win goal <number>  (0 = off)"); return true; }
            int g;
            try { g = Integer.parseInt(args[1]); } catch (NumberFormatException e) { sender.sendMessage("§cTarget must be a number."); return true; }
            if (g < 0) g = 0;
            winGoal = g;
            getConfig().set("win_goal", g);
            saveConfig();
            broadcast(0);
            sender.sendMessage(g > 0 ? "§aWin target set to §f" + g : "§aWin target turned off.");
            return true;
        }

        // /win <player> <user_name> <+/-> <amount>
        if (args.length != 4) { sender.sendMessage("§cUsage: /win <player> <user_name> <+/-> <amount>"); return true; }
        int sign = parseSign(args[2]);
        Integer amount = parseAmount(args[3]);
        if (sign == 0 || amount == null) { sender.sendMessage("§cUsage: /win <player> <user_name> <+/-> <amount>"); return true; }
        int delta = sign * amount;
        wins += delta;
        showGiftTitle(args[0], args[1], delta, "Win", true);
        broadcast(0); // update wins on overlay, no diamond popup
        return true;
    }

    private void startTracking(String playerName) {
        trackedName = playerName;
        cancelCountdown();
        Player p = Bukkit.getPlayerExact(playerName);
        int have = (p != null) ? countDiamonds(p) : 0;
        lastInv = have;
        // Count diamonds already in the inventory: bank full stacks as instant wins
        // (no countdown), the remainder becomes the current counter.
        wins = have / goal;
        count = have % goal;
        broadcast(0);
    }

    /**
     * Big colored line + small "<user> send <amount> <unit>" line, shown in-game.
     * isWin -> top line is "+X WIN" colored by sign; otherwise "+X" in ocean blue.
     */
    private void showGiftTitle(String playerName, String userName, int delta, String unit, boolean isWin) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) target = tracked();
        if (target == null) return;

        String signed = (delta >= 0 ? "+" : "") + delta;

        Component bigLine;
        if (isWin) {
            TextColor c = (delta >= 0) ? NamedTextColor.GREEN : NamedTextColor.RED;
            bigLine = Component.text(signed + " WIN", c).decorate(TextDecoration.BOLD);
        } else {
            bigLine = Component.text(signed, OCEAN).decorate(TextDecoration.BOLD);
        }

        // "<user_name> send <signed> <unit>"  -> user yellow, send white, value+unit ocean
        Component smallLine = Component.text(userName, NamedTextColor.YELLOW)
                .append(Component.text(" send ", NamedTextColor.WHITE))
                .append(Component.text(signed + " " + unit, OCEAN));

        target.showTitle(Title.title(
                bigLine,
                smallLine,
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2500), Duration.ofMillis(500))
        ));
    }

    // ======================================================================
    //  Helpers
    // ======================================================================

    private Player tracked() {
        return trackedName == null ? null : Bukkit.getPlayerExact(trackedName);
    }

    private int parseSign(String s) {
        if (s.equals("+")) return 1;
        if (s.equals("-")) return -1;
        return 0;
    }

    private Integer parseAmount(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return v < 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void broadcast(int pop) {
        if (overlay != null) overlay.broadcast(stateJson(pop, 0));
    }

    /** Special broadcast for a completed win: triggers the big diamond-burst effect. */
    private void broadcastWin() {
        if (overlay != null) overlay.broadcast(stateJson(0, goal));
    }

    private String stateJson() { return stateJson(0, 0); }

    private String stateJson(int pop, int winpop) {
        return "{\"count\":" + count + ",\"goal\":" + goal + ",\"wins\":" + wins
                + ",\"wingoal\":" + winGoal + ",\"pop\":" + pop + ",\"winpop\":" + winpop + "}";
    }

    private String loadResource(String name) {
        try (InputStream in = getResource(name)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}

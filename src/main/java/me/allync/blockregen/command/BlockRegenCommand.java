package me.allync.blockregen.command;

import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.AutoScanPoint;
import me.allync.blockregen.listener.WandListener;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class BlockRegenCommand implements CommandExecutor, TabCompleter {

    private final BlockRegen plugin;

    public BlockRegenCommand(BlockRegen plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("blockregen.admin")) {
            sender.sendMessage(plugin.getConfigManager().noPermissionMessage);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(plugin.getConfigManager().helpHeader);
            sender.sendMessage(plugin.getConfigManager().helpTitle);
            plugin.getConfigManager().helpCommands.forEach(sender::sendMessage);
            sender.sendMessage(plugin.getConfigManager().helpFooter);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            sender.sendMessage(plugin.getConfigManager().reloadMessage);
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Perintah ini hanya bisa dijalankan oleh player.");
                return true;
            }
            Player player = (Player) sender;
            plugin.toggleDebug(player);
            boolean isDebugging = plugin.isPlayerDebugging(player);
            player.sendMessage(plugin.getConfigManager().prefix + "Debug personal sekarang " + (isDebugging ? "§aaktif" : "§cnonaktif") + "§f.");
            return true;
        }

        if (args[0].equalsIgnoreCase("bypass")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Perintah ini hanya bisa dijalankan oleh player.");
                return true;
            }
            Player player = (Player) sender;
            plugin.toggleBypass(player);
            boolean isBypassing = plugin.isPlayerBypassing(player);
            player.sendMessage(plugin.getConfigManager().prefix + "Mode bypass sekarang " + (isBypassing ? "§aaktif" : "§cnonaktif") + "§f.");
            return true;
        }

        if (args[0].equalsIgnoreCase("wand")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Perintah ini hanya bisa dijalankan oleh player.");
                return true;
            }
            Player player = (Player) sender;
            ItemStack wand = new ItemStack(plugin.getConfigManager().wandMaterial);
            ItemMeta meta = wand.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.getConfigManager().wandName);
                meta.setLore(Collections.singletonList("§7Klik kiri untuk set posisi 1"));
                wand.setItemMeta(meta);
            }
            player.getInventory().addItem(wand);
            player.sendMessage(plugin.getConfigManager().wandReceiveMessage);
            return true;
        }

        if (args[0].equalsIgnoreCase("save")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Perintah ini hanya bisa dijalankan oleh player.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Gunakan: /br save <nama>");
                return true;
            }
            Player player = (Player) sender;
            String regionName = args[1];
            try {
                plugin.getRegionManager().saveRegion(player, regionName);
                sender.sendMessage(plugin.getConfigManager().regionSaveMessage.replace("%region%", regionName));
            } catch (IOException e) {
                sender.sendMessage(plugin.getConfigManager().prefix + "§cGagal menyimpan region ke file. Cek console.");
                e.printStackTrace();
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Gunakan: /br remove <nama>");
                return true;
            }
            String regionName = args[1];
            try {
                if (plugin.getRegionManager().removeRegion(regionName)) {
                    sender.sendMessage(plugin.getConfigManager().regionRemovedMessage.replace("%region%", regionName));
                } else {
                    sender.sendMessage(plugin.getConfigManager().regionNotFoundMessage.replace("%region%", regionName));
                }
            } catch (IOException e) {
                sender.sendMessage(plugin.getConfigManager().prefix + "§cGagal menghapus region dari file. Cek console.");
                e.printStackTrace();
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("block")) {
            return handleRandomBlockCommand(sender, args);
        }

        if (args[0].equalsIgnoreCase("scan")) {
            return handleScanCommand(sender, args);
        }

        if (args[0].equalsIgnoreCase("refresh")) {
            return handleRefreshCommand(sender, args);
        }

        if (args[0].equalsIgnoreCase("admin")) {
            return handleAdminCommand(sender, args);
        }

        sender.sendMessage(plugin.getConfigManager().prefix + "Perintah tidak dikenal. Gunakan /br help.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            completions.add("wand");
            completions.add("save");
            completions.add("remove");
            completions.add("debug");
            completions.add("bypass");
            completions.add("block");
            completions.add("scan");
            completions.add("refresh");
            completions.add("admin");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("remove")) {
                completions.addAll(plugin.getRegionManager().getRegionNames());
            } else if (args[0].equalsIgnoreCase("block")) {
                completions.add("set");
                completions.add("remove");
                completions.add("list");
                completions.add("refresh");
                completions.add("spawn");
                completions.add("debug");
            } else if (args[0].equalsIgnoreCase("scan")) {
                completions.add("wand");
                completions.add("list");
                completions.add("cycle");
                completions.add("info");
            } else if (args[0].equalsIgnoreCase("refresh")) {
                completions.add("all");
                for (World w : org.bukkit.Bukkit.getWorlds()) {
                    completions.add(w.getName());
                }
            } else if (args[0].equalsIgnoreCase("admin")) {
                completions.add("status");
                completions.add("list");
                completions.add("forceall");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("block")) {
            if (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("list")) {
                completions.addAll(plugin.getRandomOreManager().getRegionNames());
                completions.addAll(plugin.getRegionManager().getRegionNames());
            } else if (args[1].equalsIgnoreCase("refresh")) {
                completions.add("all");
                completions.addAll(plugin.getRandomOreManager().getRegionNames());
                completions.addAll(plugin.getRegionManager().getRegionNames());
            } else if (args[1].equalsIgnoreCase("spawn")) {
                completions.addAll(plugin.getRandomOreManager().getRegionNames());
                completions.addAll(plugin.getRegionManager().getRegionNames());
            } else if (args[1].equalsIgnoreCase("debug")) {
                completions.addAll(plugin.getRandomOreManager().getRegionNames());
                completions.addAll(plugin.getRegionManager().getRegionNames());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("block")) {
            if (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("spawn")) {
                completions.addAll(plugin.getBlockManager().getConfiguredIdentifiers());
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("block") && args[1].equalsIgnoreCase("spawn")) {
            completions.add("1");
            completions.add("5");
            completions.add("10");
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }

    private boolean handleRandomBlockCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().prefix + "Perintah ini hanya bisa dijalankan oleh player.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getConfigManager().prefix + "Gunakan: /regen block <set|remove|list> <region> <id_block>");
            return true;
        }

        Player player = (Player) sender;
        String action = args[1].toLowerCase(Locale.ROOT);

        if (action.equals("refresh")) {
            String regionName = args.length >= 3 ? args[2] : "all";
            int refreshed = plugin.getRandomOreManager().refreshRegionNow(regionName);
            if (refreshed <= 0) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Region random ore tidak ditemukan: §c" + regionName);
            } else {
                sender.sendMessage(plugin.getConfigManager().prefix + "Refresh random ore berhasil. Region terproses: §a" + refreshed);
            }
            return true;
        }

        if (action.equals("spawn")) {
            if (args.length < 5) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Gunakan: /regen block spawn <region> <id_block> <jumlah>");
                return true;
            }

            String regionName = args[2];
            String blockId = args[3];
            if (!plugin.getBlockManager().isRegenBlock(blockId)) {
                sender.sendMessage(plugin.getConfigManager().prefix + "ID block tidak terdaftar di blocks.yml: §c" + blockId);
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Jumlah harus angka.");
                return true;
            }

            int placed = plugin.getRandomOreManager().spawnBlockInRegion(regionName, blockId, amount);
            sender.sendMessage(plugin.getConfigManager().prefix + "Spawn selesai. Berhasil place §a" + placed + "§f block dari target §e" + amount + "§f.");
            return true;
        }

        if (action.equals("debug")) {
            if (args.length < 3) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Gunakan: /regen block debug <region>");
                return true;
            }

            String regionName = args[2];
            List<String> lines = plugin.getRandomOreManager().getRegionDebugLines(regionName);
            if (lines.isEmpty()) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Belum ada data cycle untuk region itu. Jalankan /regen block refresh " + regionName + " dulu.");
                return true;
            }

            sender.sendMessage(plugin.getConfigManager().prefix + "&6Debug random cycle:");
            for (String line : lines) {
                sender.sendMessage(me.allync.blockregen.util.ColorUtil.color(line));
            }
            return true;
        }

        if (action.equals("list")) {
            if (args.length < 4) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Gunakan: /regen block list <region> <id_block>");
                return true;
            }
            String regionName = args[2];
            String blockId = args[3];
            int count = plugin.getRandomOreManager().getPointCount(regionName, blockId);
            sender.sendMessage(plugin.getConfigManager().prefix + "Total titik random ore §e" + blockId + "§f di region §e" + regionName + "§f: §a" + count);
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().prefix + "Gunakan: /regen block " + action + " <region> <id_block>");
            return true;
        }

        String regionName = args[2];

        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().prefix + "Arahkan crosshair ke block target terlebih dulu.");
            return true;
        }

        String blockId;
        if (args.length >= 4) {
            blockId = args[3];
        } else {
            blockId = plugin.getMiningManager().getBlockIdentifier(target);
        }

        if (!plugin.getBlockManager().isRegenBlock(blockId)) {
            sender.sendMessage(plugin.getConfigManager().prefix + "ID block tidak terdaftar di blocks.yml: §c" + blockId);
            return true;
        }

        if (action.equals("set")) {
            boolean added = plugin.getRandomOreManager().addPoint(regionName, blockId, target.getLocation());
            sender.sendMessage(added
                    ? plugin.getConfigManager().prefix + "Titik random ore berhasil ditambahkan untuk §e" + blockId + "§f di region §e" + regionName
                    : plugin.getConfigManager().prefix + "Titik ini sudah ada atau gagal disimpan.");
            return true;
        }

        if (action.equals("remove")) {
            if (args.length < 4) {
                sender.sendMessage(plugin.getConfigManager().prefix + "Gunakan: /regen block remove <region> <id_block>");
                return true;
            }

            boolean removed = plugin.getRandomOreManager().removePoint(regionName, blockId, target.getLocation());
            sender.sendMessage(removed
                    ? plugin.getConfigManager().prefix + "Titik random ore berhasil dihapus dari §e" + blockId + "§f di region §e" + regionName
                    : plugin.getConfigManager().prefix + "Titik tidak ditemukan pada ID block tersebut.");
            return true;
        }

        sender.sendMessage(plugin.getConfigManager().prefix + "Aksi tidak dikenal. Gunakan set/remove/list.");
        return true;
    }

    // -------------------------------------------------------------------------
    // /br refresh handler
    // -------------------------------------------------------------------------

    /**
     * /br refresh [all|<world>]
     * Force-regenerates all blocks that are currently waiting to regenerate.
     * Optionally filtered to a specific world.
     */
    private boolean handleRefreshCommand(CommandSender sender, String[] args) {
        String scope = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";

        int count;
        if (scope.equals("all")) {
            count = plugin.getRegenManager().forceRegenAll();
            sender.sendMessage(plugin.getConfigManager().prefix
                    + "§aForce-regen selesai. §e" + count + "§a blok telah diregen ulang.");
        } else {
            // filter by world name
            World world = org.bukkit.Bukkit.getWorld(scope);
            if (world == null) {
                sender.sendMessage(plugin.getConfigManager().prefix
                        + "§cWorld §e" + scope + "§c tidak ditemukan. Gunakan /br refresh all atau nama world yang valid.");
                return true;
            }
            Set<Location> pending = plugin.getRegenManager().getPendingLocations();
            count = 0;
            for (Location loc : new java.util.ArrayList<>(pending)) {
                if (loc.getWorld() != null && loc.getWorld().equals(world)) {
                    plugin.getRegenManager().forceRegenLocation(loc);
                    count++;
                }
            }
            sender.sendMessage(plugin.getConfigManager().prefix
                    + "§aForce-regen selesai di world §e" + world.getName() + "§a. §e" + count + "§a blok diregen ulang.");
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // /br admin handler
    // -------------------------------------------------------------------------

    /**
     * /br admin <status|list|forceall>
     */
    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";

        switch (sub) {
            case "status" -> {
                int pending   = plugin.getRegenManager().getPendingCount();
                int blocks    = plugin.getBlockManager().getConfiguredIdentifiers().size();
                int regions   = plugin.getRegionManager().getRegionNames().size();
                int scanPts   = plugin.getAutoScanManager().getPointCount();
                boolean scanOn = plugin.getAutoScanManager().isEnabled();

                sender.sendMessage(plugin.getConfigManager().prefix + "§6§lBlockRegen Admin Status");
                sender.sendMessage("  §7Plugin versi   : §e" + plugin.getDescription().getVersion());
                sender.sendMessage("  §7Config block   : §e" + blocks + "§7 terdaftar");
                sender.sendMessage("  §7Region internal: §e" + regions);
                sender.sendMessage("  §7Pending regen  : §e" + pending + "§7 blok menunggu");
                sender.sendMessage("  §7Auto-Scan      : " + (scanOn ? "§aAktif" : "§cNonaktif") + "§7, §e" + scanPts + "§7 titik");
            }

            case "list" -> {
                Set<Location> pending = plugin.getRegenManager().getPendingLocations();
                if (pending.isEmpty()) {
                    sender.sendMessage(plugin.getConfigManager().prefix + "§7Tidak ada blok yang sedang menunggu regen.");
                    return true;
                }
                sender.sendMessage(plugin.getConfigManager().prefix
                        + "§6Pending regen blocks (§e" + pending.size() + "§6):");
                int i = 1;
                for (Location loc : pending) {
                    String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
                    sender.sendMessage(String.format("  §e%d. §f%s §7(§f%d, %d, %d§7)",
                            i++, world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                    if (i > 50) {
                        sender.sendMessage("  §7... dan §e" + (pending.size() - 50) + "§7 lainnya (ditampilkan 50 pertama).");
                        break;
                    }
                }
            }

            case "forceall" -> {
                int count = plugin.getRegenManager().forceRegenAll();
                sender.sendMessage(plugin.getConfigManager().prefix
                        + "§aForce-regen semua blok selesai. §e" + count + "§a blok diregen ulang.");
            }

            default -> {
                sender.sendMessage(plugin.getConfigManager().prefix + "§6/br admin §7<status|list|forceall>");
                sender.sendMessage("  §e/br admin status  §7- Lihat status sistem BlockRegen");
                sender.sendMessage("  §e/br admin list    §7- Daftar semua blok yang sedang menunggu regen");
                sender.sendMessage("  §e/br admin forceall§7- Force regen semua blok yang pending");
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // /br scan handler
    // -------------------------------------------------------------------------

    private boolean handleScanCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().prefix + "Perintah ini hanya bisa dijalankan oleh player.");
            return true;
        }
        Player player = (Player) sender;

        String sub = args.length >= 2 ? args[1].toLowerCase() : "help";

        switch (sub) {
            case "wand" -> {
                player.getInventory().addItem(WandListener.createScanWand());
                player.sendMessage(plugin.getConfigManager().prefix
                        + "§aKamu menerima §bAuto-Scan Wand§a. Klik block ore yang sudah dikonfigurasi untuk mendaftar/menghapus titik.");
            }

            case "list" -> {
                int count = plugin.getAutoScanManager().getPointCount();
                if (count == 0) {
                    player.sendMessage(plugin.getConfigManager().prefix + "§7Belum ada Auto-Scan point yang terdaftar.");
                    return true;
                }
                player.sendMessage(plugin.getConfigManager().prefix + "§6Auto-Scan Points (" + count + " total):");
                int i = 1;
                for (AutoScanPoint p : plugin.getAutoScanManager().getAllPoints()) {
                    String status = p.isActive() ? "§aAKTIF" : "§cNON-AKTIF";
                    Location loc = p.getLocation();
                    player.sendMessage(String.format("  §e%d. §f%s §7@ %s (§f%d, %d, %d§7) [%s§7]",
                            i++,
                            p.getBlockIdentifier(),
                            loc.getWorld().getName(),
                            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                            status));
                }
            }

            case "cycle" -> {
                // Force one full cycle immediately
                plugin.getAutoScanManager().runCycle();
                player.sendMessage(plugin.getConfigManager().prefix
                        + "§aSatu siklus Auto-Scan telah dijalankan secara paksa.");
            }

            case "info" -> {
                boolean enabled = plugin.getAutoScanManager().isEnabled();
                int interval  = plugin.getAutoScanManager().getCycleIntervalSeconds();
                int points    = plugin.getAutoScanManager().getPointCount();
                player.sendMessage(plugin.getConfigManager().prefix + "§6Auto-Scan Info:");
                player.sendMessage("  §7Status     : " + (enabled ? "§aAktif" : "§cNonaktif"));
                player.sendMessage("  §7Interval   : §e" + interval + "§7 detik");
                player.sendMessage("  §7Total Point: §e" + points);
            }

            default -> {
                player.sendMessage(plugin.getConfigManager().prefix + "§6/br scan §7<wand|list|cycle|info>");
                player.sendMessage("  §e/br scan wand  §7- Dapatkan wand untuk toggle scan point");
                player.sendMessage("  §e/br scan list  §7- Lihat semua scan point yang terdaftar");
                player.sendMessage("  §e/br scan cycle §7- Paksa jalankan siklus sekarang");
                player.sendMessage("  §e/br scan info  §7- Lihat status dan konfigurasi sistem");
            }
        }

        return true;
    }
}

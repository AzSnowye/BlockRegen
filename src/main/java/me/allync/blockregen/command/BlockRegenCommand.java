package me.allync.blockregen.command;

import me.allync.blockregen.BlockRegen;
import me.allync.blockregen.data.AutoScanPoint;
import me.allync.blockregen.listener.WandListener;
import me.allync.blockregen.manager.AutoScanManager;
import me.allync.blockregen.util.ModelEngineUtil;
import org.bukkit.Location;
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

        if (args[0].equalsIgnoreCase("model")) {
            return handleModelCommand(sender, args);
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
            if (BlockRegen.modelEngineEnabled) completions.add("model");
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
                completions.add("region");
            } else if (args[0].equalsIgnoreCase("model") && BlockRegen.modelEngineEnabled) {
                completions.add("clean");
                completions.add("cleanall");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("scan") && args[1].equalsIgnoreCase("region")) {
            completions.addAll(plugin.getRegionManager().getRegionNames());
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
    // /br scan handler
    // -------------------------------------------------------------------------

    private boolean handleScanCommand(CommandSender sender, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase() : "help";

        // 'region' can be run from console; all other sub-commands require a player
        if (!sub.equals("region") && !(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().prefix + "Perintah ini hanya bisa dijalankan oleh player.");
            return true;
        }

        Player player = (sender instanceof Player) ? (Player) sender : null;

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

            case "region" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.getConfigManager().prefix + "Gunakan: /br scan region <nama_region>");
                    return true;
                }
                String regionName = args[2];

                // Try internal BlockRegen region first
                me.allync.blockregen.data.Region internalRegion =
                        plugin.getRegionManager().getRegionByName(regionName);

                AutoScanManager.ScanResult result;
                String regionSource;

                if (internalRegion != null) {
                    regionSource = "BlockRegen";
                    // Volume warning
                    org.bukkit.Location mn = internalRegion.getMinPoint();
                    org.bukkit.Location mx = internalRegion.getMaxPoint();
                    long vol = (long)(mx.getBlockX() - mn.getBlockX() + 1)
                             * (mx.getBlockY() - mn.getBlockY() + 1)
                             * (mx.getBlockZ() - mn.getBlockZ() + 1);
                    if (vol > 500_000) {
                        sender.sendMessage(plugin.getConfigManager().prefix
                                + "§eRegion sangat besar (" + vol + " blok). Scan mungkin memakan waktu sebentar...");
                    }
                    sender.sendMessage(plugin.getConfigManager().prefix
                            + "§7Sedang scan region §e" + regionName + " §7(BlockRegen)...");
                    result = plugin.getAutoScanManager().scanRegion(internalRegion);
                } else {
                    // Fall back to WorldGuard
                    if (!plugin.getConfigManager().worldGuardEnabled || plugin.getWorldGuardPlugin() == null) {
                        sender.sendMessage(plugin.getConfigManager().prefix
                                + "§cRegion §e" + regionName + "§c tidak ditemukan di BlockRegen, dan WorldGuard tidak aktif.");
                        return true;
                    }
                    sender.sendMessage(plugin.getConfigManager().prefix
                            + "§7Tidak ditemukan di BlockRegen. Mencari di WorldGuard...");
                    result = plugin.getAutoScanManager().scanWorldGuardRegion(regionName);
                    if (result == null) {
                        sender.sendMessage(plugin.getConfigManager().prefix
                                + "§cRegion §e" + regionName + "§c tidak ditemukan di BlockRegen maupun WorldGuard.");
                        return true;
                    }
                    regionSource = "WorldGuard";
                    sender.sendMessage(plugin.getConfigManager().prefix
                            + "§7Sedang scan region §e" + regionName + " §7(WorldGuard)...");
                }

                if (result.total == 0) {
                    sender.sendMessage(plugin.getConfigManager().prefix
                            + "§7Tidak ada regen block yang ditemukan di region §e" + regionName + "§7.");
                } else {
                    sender.sendMessage(plugin.getConfigManager().prefix
                            + "§aScan region §e" + regionName + " §7[" + regionSource + "]§a selesai!");
                    sender.sendMessage("  §7Block ditemukan : §e" + result.total);
                    sender.sendMessage("  §aDidaftarkan baru: §e" + result.added);
                    sender.sendMessage("  §7Sudah terdaftar : §e" + result.skipped);
                }
            }

            default -> {
                sender.sendMessage(plugin.getConfigManager().prefix + "§6/br scan §7<wand|list|cycle|info|region>");
                sender.sendMessage("  §e/br scan wand          §7- Dapatkan wand untuk toggle scan point");
                sender.sendMessage("  §e/br scan list          §7- Lihat semua scan point yang terdaftar");
                sender.sendMessage("  §e/br scan cycle         §7- Paksa jalankan siklus sekarang");
                sender.sendMessage("  §e/br scan info          §7- Lihat status dan konfigurasi sistem");
                sender.sendMessage("  §e/br scan region <nama> §7- Scan region & daftarkan semua regen block secara otomatis");
            }
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // /br model handler
    // -------------------------------------------------------------------------

    private boolean handleModelCommand(CommandSender sender, String[] args) {
        if (!BlockRegen.modelEngineEnabled) {
            sender.sendMessage(plugin.getConfigManager().prefix + "§cModel Engine tidak aktif di server ini.");
            return true;
        }

        String sub = args.length >= 2 ? args[1].toLowerCase() : "help";

        switch (sub) {
            case "clean" -> {
                // Hapus model di block yang player lihat
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getConfigManager().prefix + "Perintah ini hanya bisa dijalankan oleh player.");
                    return true;
                }
                Block target = player.getTargetBlockExact(10);
                if (target == null) {
                    // Coba cari entity di depan player sebagai alternatif
                    sender.sendMessage(plugin.getConfigManager().prefix + "§cArahkan crosshair ke blok atau area yang modelnya ingin dihapus (jangkauan 10 blok).");
                    return true;
                }
                Location loc = target.getLocation();
                boolean hadModel = ModelEngineUtil.hasModel(loc);
                ModelEngineUtil.removeModel(loc);
                ModelEngineUtil.restoreHiddenBlock(loc);
                if (hadModel) {
                    player.sendMessage(plugin.getConfigManager().prefix + "§aModel di lokasi §e"
                            + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                            + " §atelah dihapus.");
                } else {
                    player.sendMessage(plugin.getConfigManager().prefix + "§7Tidak ada model terdaftar di lokasi tersebut. Model entity tetap dihapus jika ada.");
                }
            }

            case "cleanall" -> {
                // Hapus SEMUA model aktif
                int count = ModelEngineUtil.getActiveModelCount();
                ModelEngineUtil.removeAll();
                sender.sendMessage(plugin.getConfigManager().prefix + "§aSemua model §e(" + count + ")§a telah dihapus dari dunia.");
            }

            default -> {
                sender.sendMessage(plugin.getConfigManager().prefix + "§6/br model §7<clean|cleanall>");
                sender.sendMessage("  §e/br model clean    §7- Hapus model di block yang kamu lihat (jika bug/nyangkut)");
                sender.sendMessage("  §e/br model cleanall §7- Hapus SEMUA model aktif di dunia (emergency cleanup)");
            }
        }

        return true;
    }
}

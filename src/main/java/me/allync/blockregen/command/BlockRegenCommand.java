package me.allync.blockregen.command;

import me.allync.blockregen.BlockRegen;
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
                sender.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', line));
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
}
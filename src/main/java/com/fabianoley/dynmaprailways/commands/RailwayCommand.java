package com.fabianoley.dynmaprailways.commands;

import com.fabianoley.dynmaprailways.DynmapRailways;
import com.fabianoley.dynmaprailways.rail.RailLine;
import com.fabianoley.dynmaprailways.station.Station;
import com.fabianoley.dynmaprailways.scan.RailScanner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command handler for railway commands.
 */
public class RailwayCommand implements CommandExecutor, TabCompleter {
    
    private DynmapRailways plugin;
    
    public RailwayCommand(DynmapRailways plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "scan":
                return handleScan(sender);
            case "list":
                return handleList(sender);
            case "station":
                return handleStation(sender, args);
            case "reload":
                return handleReload(sender);
            case "debug":
                return handleDebug(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private boolean handleScan(CommandSender sender) {
        if (!sender.hasPermission("railway.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        sender.sendMessage("§eScanning all worlds for rail blocks...");
        
        // Scan all worlds asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int totalLines = 0;
                
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    List<RailLine> lines = RailScanner.scanWorld(world);
                    totalLines += lines.size();
                    
                    // Save lines
                    for (RailLine line : lines) {
                        plugin.getDataStorage().saveRailLine(line);
                    }
                }
                
                // Update map on main thread
                final int finalTotalLines = totalLines;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aScanning complete! Found " + finalTotalLines + " rail lines.");
                });
            } catch (Exception e) {
                sender.sendMessage("§cError during scan: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        return true;
    }
    
    private boolean handleList(CommandSender sender) {
        try {
            Map<String, RailLine> lines = plugin.getDataStorage().getRailLines();
            sender.sendMessage("§6Rail Lines (" + lines.size() + "):");
            
            if (lines.isEmpty()) {
                sender.sendMessage("§7No rail lines detected. Use /railway scan to detect them.");
                return true;
            }
            
            for (RailLine line : lines.values()) {
                if (line.isActive()) {
                    sender.sendMessage("§e" + line.getName() + " §7(" + line.getBlockCount() + " blocks)");
                }
            }
        } catch (Exception e) {
            sender.sendMessage("§cError listing rail lines: " + e.getMessage());
        }
        return true;
    }
    
    private boolean handleStation(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /railway station <create|list|remove> [station-name]");
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command requires player context.");
            return true;
        }
        
        Player player = (Player) sender;
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "create":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /railway station create <name>");
                    return true;
                }
                
                String stationName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                String stationId = stationName.toLowerCase().replace(" ", "_");
                int x = (int) player.getLocation().getX();
                int z = (int) player.getLocation().getZ();
                
                try {
                    Station station = new Station(stationId, stationName, x, z);
                    station.setCreatedBy(player.getName());
                    plugin.getDataStorage().saveStation(station);
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aStation created: " + stationName + " at (" + x + ", " + z + ")");
                } catch (Exception e) {
                    sender.sendMessage("§cError creating station: " + e.getMessage());
                }
                return true;
                
            case "list":
                try {
                    Map<String, Station> stations = plugin.getDataStorage().getStations();
                    sender.sendMessage("§6Stations (" + stations.size() + "):");
                    for (Station s : stations.values()) {
                        sender.sendMessage("§e" + s.getName() + " §7at (" + s.getX() + ", " + s.getZ() + ")");
                    }
                } catch (Exception e) {
                    sender.sendMessage("§cError listing stations: " + e.getMessage());
                }
                return true;
                
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /railway station remove <name>");
                    return true;
                }
                
                String removeName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                String removeId = removeName.toLowerCase().replace(" ", "_");
                
                try {
                    plugin.getDataStorage().removeStation(removeId);
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aStation removed: " + removeName);
                } catch (Exception e) {
                    sender.sendMessage("§cError removing station: " + e.getMessage());
                }
                return true;
                
            default:
                sender.sendMessage("§cUnknown action: " + action);
                return true;
        }
    }
    
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("railway.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        try {
            plugin.getMapRenderer().updateAllMarkers();
            sender.sendMessage("§aMap reloaded.");
        } catch (Exception e) {
            sender.sendMessage("§cError reloading: " + e.getMessage());
        }
        return true;
    }
    
    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("railway.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /railway debug <reinit|info>");
            return true;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "reinit":
                sender.sendMessage("§eForcing marker set reinitialization...");
                plugin.getMapRenderer().forceReinitialize();
                sender.sendMessage("§aReinitialization complete. Check console for details.");
                return true;
                
            case "info":
                sender.sendMessage("§6Debug Info:");
                sender.sendMessage("§eRail Lines: " + plugin.getDataStorage().getRailLines().size());
                sender.sendMessage("§eStations: " + plugin.getDataStorage().getStations().size());
                sender.sendMessage("§eCheck console for marker set details.");
                return true;
                
            default:
                sender.sendMessage("§cUnknown debug action: " + action);
                return true;
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6Railway Commands:");
        sender.sendMessage("§e/railway scan §7- Scan worlds for rail blocks (admin)");
        sender.sendMessage("§e/railway list §7- List detected rail lines");
        sender.sendMessage("§e/railway station create <name> §7- Create a station at your location");
        sender.sendMessage("§e/railway station list §7- List all stations");
        sender.sendMessage("§e/railway station remove <name> §7- Remove a station");
        sender.sendMessage("§e/railway reload §7- Reload map visualization (admin)");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("scan", "list", "station", "reload");
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("station")) {
            return Arrays.asList("create", "list", "remove");
        }
        
        return Collections.emptyList();
    }
}


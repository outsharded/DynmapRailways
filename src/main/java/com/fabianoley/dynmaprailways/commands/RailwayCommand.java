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

/**
 * Command handler for railway commands.
 */
public class RailwayCommand implements CommandExecutor, TabCompleter {
    
    private DynmapRailways plugin;
    
    public RailwayCommand(DynmapRailways plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Check if a sender can edit a specific line.
     * Returns true if:
     * - Sender has railway.admin permission, OR
     * - Sender has railway.line.personal permission AND is the line creator
     */
    private boolean canEditLine(CommandSender sender, RailLine line) {
        if (sender.hasPermission("railway.admin")) {
            return true;
        }
        
        if (sender.hasPermission("railway.line.personal")) {
            if (sender instanceof Player) {
                String playerName = ((Player) sender).getName();
                // Can edit if they created it
                if (line.getCreatedBy() != null && line.getCreatedBy().equals(playerName)) {
                    return true;
                }
            }
        }
        
        return false;
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
                return handleScan(sender, args);
            case "list":
                return handleList(sender);
            case "line":
                return handleLine(sender, args);
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
    
    private boolean handleScan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("railway.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        // Optional: radius overload for targeted chunk scanning around the player
        if (args.length >= 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cRadius scan can only be used by a player.");
                return true;
            }
            int radius;
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cInvalid radius. Usage: /railway scan <chunk-radius>");
                return true;
            }
            if (radius < 0) {
                sender.sendMessage("§cRadius must be zero or positive.");
                return true;
            }

            Player player = (Player) sender;
            org.bukkit.World world = player.getWorld();
            org.bukkit.Chunk playerChunk = player.getLocation().getChunk();
            int baseX = playerChunk.getX();
            int baseZ = playerChunk.getZ();

            // Pre-load existing generated chunks within radius on the main thread (non-generating)
            sender.sendMessage("§eScanning chunks within radius " + radius + " around you...");
            final java.util.Set<org.bukkit.Chunk> chunksToScan = new java.util.LinkedHashSet<>();
            for (int cx = baseX - radius; cx <= baseX + radius; cx++) {
                for (int cz = baseZ - radius; cz <= baseZ + radius; cz++) {
                    // If already loaded, include; else try to load without generating new chunks
                    if (world.isChunkLoaded(cx, cz)) {
                        chunksToScan.add(world.getChunkAt(cx, cz));
                    } else {
                        boolean loaded = world.loadChunk(cx, cz, false); // do not generate
                        if (loaded) {
                            chunksToScan.add(world.getChunkAt(cx, cz));
                        }
                    }
                }
            }

            // Run scan asynchronously for these chunks only
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // Get existing lines
                    List<RailLine> existingLines = new ArrayList<>(plugin.getDataStorage().getRailLines().values());
                    
                    // Scan for new lines - assign IDs after scanning
                    List<RailLine> newLines = RailScanner.scanChunks(world, chunksToScan);
                    
                    // Assign unique IDs to new lines
                    for (RailLine line : newLines) {
                        if (line.getId().startsWith("tmp_")) {
                            String newId = plugin.getDataStorage().generateLineId();
                            // Create new line with proper ID
                            RailLine properLine = new RailLine(newId, line.getColor());
                            properLine.setName(line.getName());
                            properLine.setCreatedBy(line.getCreatedBy());
                            properLine.addBlocks(line.getBlocks());
                            // Replace in list
                            newLines.set(newLines.indexOf(line), properLine);
                        }
                    }
                    
                    // Merge with existing lines to prevent duplicates
                    List<RailLine> mergedLines = RailScanner.mergeWithExistingLines(world, newLines, existingLines);
                    
                    // Filter and replace all lines with merged result
                    int minLineLength = plugin.getConfig().getInt("general.min-line-length", 15);
                    plugin.getDataStorage().replaceAllRailLinesFiltered(mergedLines, minLineLength);
                    
                    final int lineCount = mergedLines.size();
                    int humanCount = 0;
                    for (RailLine line : mergedLines) {
                        if (line.getCreatedBy() != null && !line.getCreatedBy().isEmpty()) {
                            humanCount++;
                        }
                    }
                    final int finalHumanCount = humanCount;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getMapRenderer().updateAllMarkers();
                        sender.sendMessage("§aRadius scan complete! Total " + lineCount + " rail lines (§b" + finalHumanCount + " player-placed§a).");
                        boolean playerOnly = plugin.getConfig().getBoolean("coreprotect.player-placed-only", false);
                        if (playerOnly) {
                            sender.sendMessage("§7Note: Rendering is set to player-placed lines only.");
                        }
                    });
                } catch (Exception e) {
                    sender.sendMessage("§cError during radius scan: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            return true;
        }

        // Default: scan all currently loaded chunks in all worlds
        sender.sendMessage("§eScanning all worlds for rail blocks...");
        
        // Scan all worlds asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Get all existing lines
                List<RailLine> existingLines = new ArrayList<>(plugin.getDataStorage().getRailLines().values());
                List<RailLine> allMergedLines = new ArrayList<>();
                
                int humanLines = 0;
                
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    // Scan this world
                    List<RailLine> newLines = RailScanner.scanWorld(world);
                    
                    // Assign unique IDs to new lines
                    for (RailLine line : newLines) {
                        if (line.getId().startsWith("tmp_")) {
                            String newId = plugin.getDataStorage().generateLineId();
                            RailLine properLine = new RailLine(newId, line.getColor());
                            properLine.setName(line.getName());
                            properLine.setCreatedBy(line.getCreatedBy());
                            properLine.addBlocks(line.getBlocks());
                            newLines.set(newLines.indexOf(line), properLine);
                        }
                    }
                    
                    // Filter existing lines for this world
                    List<RailLine> existingForWorld = new ArrayList<>();
                    for (RailLine line : existingLines) {
                        boolean isInWorld = false;
                        for (RailLine.RailBlock block : line.getBlocks()) {
                            if (block.world.equals(world.getName())) {
                                isInWorld = true;
                                break;
                            }
                        }
                        if (isInWorld) {
                            existingForWorld.add(line);
                        }
                    }
                    
                    // Merge new lines with existing for this world
                    List<RailLine> mergedForWorld = RailScanner.mergeWithExistingLines(world, newLines, existingForWorld);
                    
                    for (RailLine line : mergedForWorld) {
                        if (line.getCreatedBy() != null && !line.getCreatedBy().isEmpty()) {
                            humanLines++;
                        }
                    }
                    
                    allMergedLines.addAll(mergedForWorld);
                }
                
                // Also add lines from worlds that weren't scanned
                for (RailLine line : existingLines) {
                    boolean isFromScannedWorld = false;
                    for (RailLine.RailBlock block : line.getBlocks()) {
                        for (org.bukkit.World world : Bukkit.getWorlds()) {
                            if (block.world.equals(world.getName())) {
                                isFromScannedWorld = true;
                                break;
                            }
                        }
                        if (isFromScannedWorld) break;
                    }
                    if (!isFromScannedWorld) {
                        allMergedLines.add(line);
                    }
                }
                
                // Filter and replace all lines with merged result
                int minLineLength = plugin.getConfig().getInt("general.min-line-length", 15);
                plugin.getDataStorage().replaceAllRailLinesFiltered(allMergedLines, minLineLength);
                
                // Update map on main thread
                final int finalTotalLines = allMergedLines.size();
                final int finalHumanLines = humanLines;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aScanning complete! Total " + finalTotalLines + " rail lines (§b" + finalHumanLines + " player-placed§a).");
                    boolean playerOnly = plugin.getConfig().getBoolean("coreprotect.player-placed-only", false);
                    if (playerOnly) {
                        sender.sendMessage("§7Note: Rendering is set to player-placed lines only.");
                    }
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
                    sender.sendMessage("§e" + line.getId() + ": §f" + line.getName() + " §7(" + line.getBlockCount() + " blocks, " + line.getColor() + ")");
                }
            }
        } catch (Exception e) {
            sender.sendMessage("§cError listing rail lines: " + e.getMessage());
        }
        return true;
    }
    
    private boolean handleLine(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /railway line <list|color|rename|create|addpoint|remove>");
            return true;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "list":
                // Same as /railway list
                return handleList(sender);
                
            case "color":
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /railway line color <line-id> <hex-color>");
                    sender.sendMessage("§7Example: /railway line color a3f #FF0000");
                    return true;
                }
                
                String lineId = args[2];
                String color = args[3];
                
                // Validate hex color
                if (!color.matches("^#[0-9A-Fa-f]{6}$")) {
                    sender.sendMessage("§cInvalid color format. Use hex format like #FF0000");
                    return true;
                }
                
                try {
                    RailLine line = plugin.getDataStorage().getRailLine(lineId);
                    if (line == null) {
                        sender.sendMessage("§cLine not found: " + lineId);
                        sender.sendMessage("§7Use /railway list to see available lines.");
                        return true;
                    }
                    
                    if (!canEditLine(sender, line)) {
                        sender.sendMessage("§cYou don't have permission to edit this line.");
                        sender.sendMessage("§7You can only edit lines you created.");
                        return true;
                    }
                    
                    line.setColor(color.toUpperCase());
                    plugin.getDataStorage().saveRailLine(line);
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aSet color of " + line.getName() + " to " + color.toUpperCase());
                } catch (Exception e) {
                    sender.sendMessage("§cError setting color: " + e.getMessage());
                }
                return true;
                
            case "rename":
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /railway line rename <line-id> <new-name>");
                    sender.sendMessage("§7Example: /railway line rename a3f Northern Line");
                    return true;
                }
                
                String renameLineId = args[2];
                String newName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                
                try {
                    RailLine lineToRename = plugin.getDataStorage().getRailLine(renameLineId);
                    if (lineToRename == null) {
                        sender.sendMessage("§cLine not found: " + renameLineId);
                        sender.sendMessage("§7Use /railway list to see available lines.");
                        return true;
                    }
                    
                    if (!canEditLine(sender, lineToRename)) {
                        sender.sendMessage("§cYou don't have permission to edit this line.");
                        sender.sendMessage("§7You can only edit lines you created.");
                        return true;
                    }
                    
                    String oldName = lineToRename.getName();
                    lineToRename.setName(newName);
                    plugin.getDataStorage().saveRailLine(lineToRename);
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aRenamed line from '" + oldName + "' to '" + newName + "'");
                } catch (Exception e) {
                    sender.sendMessage("§cError renaming line: " + e.getMessage());
                }
                return true;
                
            case "create":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /railway line create <name>");
                    return true;
                }
                
                if (!sender.hasPermission("railway.line.personal") && !sender.hasPermission("railway.admin")) {
                    sender.sendMessage("§cYou don't have permission to create lines.");
                    return true;
                }
                
                String lineName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                
                try {
                    String newLineId = plugin.getDataStorage().generateLineId();
                    RailLine newLine = new RailLine(newLineId, "#888888"); // Default gray
                    newLine.setName(lineName);
                    if (sender instanceof Player) {
                        newLine.setCreatedBy(((Player) sender).getName());
                    }
                    plugin.getDataStorage().saveRailLine(newLine);
                    sender.sendMessage("§aCreated line: " + lineName + " (ID: " + newLineId + ")");
                    sender.sendMessage("§7Add waypoints with: /railway line addpoint " + newLineId);
                } catch (Exception e) {
                    sender.sendMessage("§cError creating line: " + e.getMessage());
                }
                return true;
                
            case "addpoint":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /railway line addpoint <line-id>");
                    return true;
                }
                
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cThis command requires player context.");
                    return true;
                }
                
                Player player = (Player) sender;
                String targetLineId = args[2];
                
                try {
                    RailLine targetLine = plugin.getDataStorage().getRailLine(targetLineId);
                    if (targetLine == null) {
                        sender.sendMessage("§cLine not found: " + targetLineId);
                        return true;
                    }
                    
                    if (!canEditLine(sender, targetLine)) {
                        sender.sendMessage("§cYou don't have permission to edit this line.");
                        sender.sendMessage("§7You can only edit lines you created.");
                        return true;
                    }
                    
                    int x = player.getLocation().getBlockX();
                    int y = player.getLocation().getBlockY();
                    int z = player.getLocation().getBlockZ();
                    String world = player.getWorld().getName();
                    
                    RailLine.RailBlock block = new RailLine.RailBlock(x, y, z, world);
                    targetLine.addBlock(block);
                    plugin.getDataStorage().saveRailLine(targetLine);
                    plugin.getMapRenderer().updateAllMarkers();
                    
                    sender.sendMessage("§aAdded waypoint to " + targetLine.getName() + " at (" + x + ", " + y + ", " + z + ")");
                    sender.sendMessage("§7Total waypoints: " + targetLine.getBlockCount());
                } catch (Exception e) {
                    sender.sendMessage("§cError adding waypoint: " + e.getMessage());
                }
                return true;
                
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /railway line remove <line-id>");
                    return true;
                }
                
                String removeLineId = args[2];
                
                try {
                    RailLine lineToRemove = plugin.getDataStorage().getRailLine(removeLineId);
                    if (lineToRemove == null) {
                        sender.sendMessage("§cLine not found: " + removeLineId);
                        return true;
                    }
                    
                    if (!canEditLine(sender, lineToRemove)) {
                        sender.sendMessage("§cYou don't have permission to remove this line.");
                        sender.sendMessage("§7You can only remove lines you created.");
                        return true;
                    }
                    
                    plugin.getDataStorage().removeRailLine(removeLineId);
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aRemoved line: " + lineToRemove.getName());
                } catch (Exception e) {
                    sender.sendMessage("§cError removing line: " + e.getMessage());
                }
                return true;
                
            default:
                sender.sendMessage("§cUnknown line action. Use: list, color, create, addpoint, or remove");
                return true;
        }
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
                
                if (!sender.hasPermission("railway.station") && !sender.hasPermission("railway.admin")) {
                    sender.sendMessage("§cYou don't have permission to create stations.");
                    return true;
                }
                
                String stationName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                String stationId = stationName.toLowerCase().replace(" ", "_");
                int x = (int) player.getLocation().getX();
                int z = (int) player.getLocation().getZ();
                
                try {
                    String world = player.getWorld().getName();
                    Station station = new Station(stationId, stationName, x, player.getLocation().getBlockY(), z, world);
                    station.setCreatedBy(player.getName());
                    plugin.getDataStorage().saveStation(station);
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aStation created: " + stationName + " at (" + x + ", " + z + ") in world " + world);
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
            plugin.getMapRenderer().forceReinitialize();
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
            sender.sendMessage("§cUsage: /railway debug <reinit|info|block>");
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
            
            // ========== TEMPORARY DEBUG COMMAND - REMOVE AFTER TESTING ==========
            case "block":
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /railway debug block <x> <y> <z>");
                    return true;
                }
                try {
                    int x = Integer.parseInt(args[2]);
                    int y = Integer.parseInt(args[3]);
                    int z = Integer.parseInt(args[4]);
                    
                    org.bukkit.World world;
                    if (sender instanceof Player) {
                        world = ((Player) sender).getWorld();
                    } else {
                        world = Bukkit.getWorlds().get(0); // Default to first world
                    }
                    
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    sender.sendMessage("§6=== Block Debug at (" + x + ", " + y + ", " + z + ") ===");
                    sender.sendMessage("§eMaterial: §f" + block.getType().name());
                    sender.sendMessage("§eIs Rail Block: §f" + (block.getBlockData() instanceof org.bukkit.block.data.Rail));
                    
                    if (block.getBlockData() instanceof org.bukkit.block.data.Rail) {
                        org.bukkit.block.data.Rail rail = (org.bukkit.block.data.Rail) block.getBlockData();
                        sender.sendMessage("§eRail Shape: §f" + rail.getShape().name());
                        sender.sendMessage("§eRail Type: §f" + block.getType().name());
                    }
                    
                    sender.sendMessage("§eChunk: §f(" + (x >> 4) + ", " + (z >> 4) + ")");
                    sender.sendMessage("§eChunk Loaded: §f" + world.isChunkLoaded(x >> 4, z >> 4));
                    
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid coordinates. Use integer values.");
                }
                return true;
            // =====================================================================
                
            default:
                sender.sendMessage("§cUnknown debug action: " + action);
                return true;
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6Railway Commands:");
        sender.sendMessage("§e/railway scan [radius] §7- Scan for rail blocks (admin)");
        sender.sendMessage("§e/railway list §7- List all rail lines");
        sender.sendMessage("§e/railway line list §7- List all rail lines with details");
        sender.sendMessage("§e/railway line color <id> <#color> §7- Set line color (admin)");
        sender.sendMessage("§e/railway line rename <id> <name> §7- Rename a line (admin)");
        sender.sendMessage("§e/railway line create <name> §7- Create manual line (admin)");
        sender.sendMessage("§e/railway line addpoint <id> §7- Add waypoint at location (admin)");
        sender.sendMessage("§e/railway line remove <id> §7- Remove a line (admin)");
        sender.sendMessage("§e/railway station create <name> §7- Create a station");
        sender.sendMessage("§e/railway station list §7- List all stations");
        sender.sendMessage("§e/railway station remove <name> §7- Remove a station");
        sender.sendMessage("§e/railway reload §7- Reload visualization (admin)");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("scan", "list", "line", "station", "reload");
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("line")) {
            return Arrays.asList("list", "color", "rename", "create", "addpoint", "remove");
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("station")) {
            return Arrays.asList("create", "list", "remove");
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("line") && 
            (args[1].equalsIgnoreCase("color") || args[1].equalsIgnoreCase("rename") || 
             args[1].equalsIgnoreCase("addpoint") || args[1].equalsIgnoreCase("remove"))) {
            // Tab complete line IDs
            List<String> lineIds = new ArrayList<>();
            try {
                for (RailLine line : plugin.getDataStorage().getRailLines().values()) {
                    lineIds.add(line.getId());
                }
            } catch (Exception e) {
                // Ignore
            }
            return lineIds;
        }
        
        return Collections.emptyList();
    }
}


package com.fabianoley.dynmaprailways.scan;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Material;

import com.fabianoley.dynmaprailways.DynmapRailways;
import com.fabianoley.dynmaprailways.integration.CoreProtectIntegration;
import com.fabianoley.dynmaprailways.rail.RailLine;
import com.fabianoley.dynmaprailways.rail.RailLine.RailBlock;
import java.util.*;
import java.util.logging.Logger;

/**
 * Scans the world for rail blocks and clusters them into lines.
 */
public class RailScanner {
    
    private static final Logger logger = Logger.getLogger("DynmapRailways");
    private static CoreProtectIntegration coreProtect;
    private static DynmapRailways plugin;

    public static void setCoreProtectIntegration(CoreProtectIntegration integration, DynmapRailways pluginInstance) {
        coreProtect = integration;
        plugin = pluginInstance;
    }

    private static boolean isDebugEnabled() {
        return plugin != null && plugin.getConfig().getBoolean("general.debug", false);
    }
    
    /**
     * Scan a world for rail blocks and cluster them into rail lines.
     */
    public static List<RailLine> scanWorld(World world) {
        logger.info("Scanning " + world.getName() + " for rail blocks...");
        
        Set<RailBlock> allRails = findAllRails(world);
        logger.info("Found " + allRails.size() + " rail blocks");
        
        List<RailLine> lines = clusterRails(world, allRails);
        logger.info("Clustered into " + lines.size() + " rail lines");
        
        return lines;
    }
    
    /**
     * Merge newly scanned lines with existing lines to prevent duplicates.
     * This method:
     * 1. Validates existing lines (checks if rails still exist)
     * 2. Checks if new lines overlap with existing lines
     * 3. Updates existing lines or creates new ones as needed
     * 
     * @param world The world being scanned
     * @param newLines Newly detected lines from scanning
     * @param existingLines Lines currently stored in the database
     * @return Merged list of lines (updated existing + genuinely new lines)
     */
    public static List<RailLine> mergeWithExistingLines(World world, List<RailLine> newLines, List<RailLine> existingLines) {
        logger.info("Merging " + newLines.size() + " newly scanned lines with " + existingLines.size() + " existing lines...");
        
        // Step 1: Validate existing lines - check if their rails still exist
        Set<RailBlock> currentRails = findAllRails(world);
        List<RailLine> validExistingLines = new ArrayList<>();
        
        for (RailLine existingLine : existingLines) {
            // Only keep existing lines from this world
            boolean isInThisWorld = false;
            for (RailBlock block : existingLine.getBlocks()) {
                if (block.world.equals(world.getName())) {
                    isInThisWorld = true;
                    break;
                }
            }
            
            if (!isInThisWorld) {
                // Line is from a different world, keep it as-is
                validExistingLines.add(existingLine);
                continue;
            }
            
            // Check if at least 50% of this line's blocks still exist
            int existingBlockCount = 0;
            for (RailBlock block : existingLine.getBlocks()) {
                if (currentRails.contains(block)) {
                    existingBlockCount++;
                }
            }
            
            float existenceRatio = (float) existingBlockCount / existingLine.getBlockCount();
            if (existenceRatio >= 0.5) {
                validExistingLines.add(existingLine);
                logger.info("Existing line " + existingLine.getId() + " validated (" + 
                           (int)(existenceRatio * 100) + "% blocks still exist)");
            } else {
                logger.info("Existing line " + existingLine.getId() + " removed (only " + 
                           (int)(existenceRatio * 100) + "% blocks still exist)");
            }
        }
        
        // Step 2: Build a set of all blocks covered by valid existing lines in this world
        Set<RailBlock> blocksInExistingLines = new HashSet<>();
        for (RailLine existingLine : validExistingLines) {
            for (RailBlock block : existingLine.getBlocks()) {
                if (block.world.equals(world.getName())) {
                    blocksInExistingLines.add(block);
                }
            }
        }
        
        // Step 3: Process new lines - only keep those that don't significantly overlap
        List<RailLine> mergedLines = new ArrayList<>(validExistingLines);
        int skippedDuplicates = 0;
        int addedNewLines = 0;
        
        for (RailLine newLine : newLines) {
            // Calculate how many blocks in this new line are already covered
            int overlappingBlocks = 0;
            for (RailBlock block : newLine.getBlocks()) {
                if (blocksInExistingLines.contains(block)) {
                    overlappingBlocks++;
                }
            }
            
            float overlapRatio = (float) overlappingBlocks / newLine.getBlockCount();
            
            // If more than 70% of the new line overlaps with existing lines, skip it
            if (overlapRatio > 0.7) {
                logger.info("Skipping new line (" + (int)(overlapRatio * 100) + "% overlap with existing lines)");
                skippedDuplicates++;
            } else {
                // This is a genuinely new line or significantly different, add it
                mergedLines.add(newLine);
                addedNewLines++;
                
                // Add its blocks to the covered set to prevent other duplicates
                for (RailBlock block : newLine.getBlocks()) {
                    blocksInExistingLines.add(block);
                }
                
                if (isDebugEnabled()) {
                    logger.info("Added new line " + newLine.getId() + " (" + newLine.getBlockCount() + " blocks, " + (int)(overlapRatio * 100) + "% overlap)");
                }

            }
        }
        
        logger.info("Merge complete: " + validExistingLines.size() + " existing lines kept, " + 
                   addedNewLines + " new lines added, " + skippedDuplicates + " duplicates skipped");
        
        return mergedLines;
    }

    /**
     * Scan only the provided chunk set for rail blocks and cluster them into rail lines.
     * This does not attempt to load or generate chunks; callers should ensure chunks are loaded.
     */
    public static List<RailLine> scanChunks(World world, Set<org.bukkit.Chunk> chunks) {
        logger.info("Scanning " + chunks.size() + " chunks in " + world.getName() + " for rail blocks...");

        Set<RailBlock> rails = findRailsInChunks(world, chunks);
        logger.info("Found " + rails.size() + " rail blocks in targeted chunks");

        List<RailLine> lines = clusterRails(world, rails);
        logger.info("Clustered into " + lines.size() + " rail lines from targeted chunks");

        return lines;
    }
    
    /**
     * Find all rail blocks in a world (only loaded chunks).
     */
    private static Set<RailBlock> findAllRails(World world) {
        Set<RailBlock> rails = new HashSet<>();
        
        // Only scan loaded chunks for performance
        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                        Block block = chunk.getBlock(x, y, z);
                        
                        if (isRailBlock(block)) {
                            rails.add(new RailBlock(
                                    block.getX(),
                                    block.getY(),
                                    block.getZ(),
                                    world.getName()
                            ));
                        }
                    }
                }
            }
        }
        
        return rails;
    }

    /**
     * Find rail blocks only within the provided chunk set.
     */
    private static Set<RailBlock> findRailsInChunks(World world, Set<org.bukkit.Chunk> chunks) {
        Set<RailBlock> rails = new HashSet<>();

        for (org.bukkit.Chunk chunk : chunks) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (isRailBlock(block)) {
                            rails.add(new RailBlock(
                                    block.getX(),
                                    block.getY(),
                                    block.getZ(),
                                    world.getName()
                            ));
                        }
                    }
                }
            }
        }

        return rails;
    }
    
    /**
     * Check if a block is a rail.
     */

    private static boolean isRailBlock(Block block) {
        Material material = block.getType();
        return material == Material.RAIL || material == Material.POWERED_RAIL || material == Material.DETECTOR_RAIL;
    }
    
    /**
     * Cluster adjacent rail blocks into connected lines using neighbor adjacency (XZ plane only).
     * Each rail is tagged with how many neighbors it has (ignoring Y and shape connectivity).
     * Lines are traced from endpoints (1 neighbor) following the path until reaching another endpoint.
     */
    private static List<RailLine> clusterRails(World world, Set<RailBlock> allRails) {
        return clusterRailsWithIdGenerator(world, allRails, null);
    }
    
    /**
     * Cluster rails with optional ID generator for creating unique IDs.
     */
    private static List<RailLine> clusterRailsWithIdGenerator(World world, Set<RailBlock> allRails, java.util.function.Supplier<String> idGenerator) {
        List<RailLine> lines = new ArrayList<>();
        Set<RailBlock> visited = new HashSet<>();
        String[] colors = getTflColors();
        int colorIndex = 0;
        Map<String, Integer> placerCounts = new HashMap<>();
        
        // Build neighbor map: each rail -> count of adjacent rails in XZ plane
        Map<RailBlock, Integer> neighborCount = new HashMap<>();
        Map<RailBlock, Set<RailBlock>> neighborMap = new HashMap<>();
        
        for (RailBlock rail : allRails) {
            Set<RailBlock> neighbors = new HashSet<>();
            // Check 4 cardinal directions (XZ plane only, ignore Y)
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                RailBlock neighbor = new RailBlock(rail.x + d[0], rail.y, rail.z + d[1], rail.world);
                if (allRails.contains(neighbor)) {
                    neighbors.add(neighbor);
                }
                // Also check ±1 in Y for that neighbor (ascending/descending)
                RailBlock neighborUp = new RailBlock(rail.x + d[0], rail.y + 1, rail.z + d[1], rail.world);
                if (allRails.contains(neighborUp)) {
                    neighbors.add(neighborUp);
                }
                RailBlock neighborDown = new RailBlock(rail.x + d[0], rail.y - 1, rail.z + d[1], rail.world);
                if (allRails.contains(neighborDown)) {
                    neighbors.add(neighborDown);
                }
            }
            neighborCount.put(rail, neighbors.size());
            neighborMap.put(rail, neighbors);
        }
        
        // Find endpoints (blocks with exactly 1 neighbor) and trace lines
        for (RailBlock startRail : allRails) {
            if (visited.contains(startRail)) continue;
            if (neighborCount.get(startRail) != 1) continue; // Start from endpoint
            
            // Trace line from this endpoint with directional preference
            Set<RailBlock> line = new HashSet<>();
            RailBlock current = startRail;
            RailBlock prev = null;
            
            while (true) {
                line.add(current);
                visited.add(current);
                
                // Find next rail: prefer continuing in the same direction we came from
                Set<RailBlock> neighbors = neighborMap.get(current);
                
                // If this is a junction (3+ neighbors), stop tracing here - don't overtrace
                if (neighbors.size() >= 3) {
                    break;
                }
                
                RailBlock next = null;
                
                if (prev == null) {
                    // At start (endpoint), just pick the one neighbor
                    for (RailBlock n : neighbors) {
                        if (!n.equals(prev)) {
                            next = n;
                            break;
                        }
                    }
                } else {
                    // Calculate direction we came from
                    int fromDx = current.x - prev.x;
                    int fromDz = current.z - prev.z;
                    
                    // Prefer: straight > turn > never backtrack
                    RailBlock straightContinue = null;
                    List<RailBlock> turnOptions = new ArrayList<>();
                    
                    for (RailBlock n : neighbors) {
                        if (n.equals(prev)) continue;
                        
                        int toDx = n.x - current.x;
                        int toDz = n.z - current.z;
                        
                        // Check if this neighbor continues the line (dot product)
                        int dotProduct = (toDx * fromDx) + (toDz * fromDz);
                        
                        if (dotProduct > 0) {
                            // Straight continuation (best option)
                            straightContinue = n;
                            break; // Take it immediately
                        } else if (dotProduct == 0) {
                            // Perpendicular (90-degree turn, collect as fallback)
                            turnOptions.add(n);
                        }
                        // If dotProduct < 0, it's backtracking - skip it
                    }
                    
                    if (straightContinue != null) {
                        next = straightContinue;
                    } else if (!turnOptions.isEmpty()) {
                        // Tiebreak: prefer turn that goes toward positive X or Z (or less negative)
                        RailBlock bestTurn = turnOptions.get(0);
                        for (RailBlock n : turnOptions) {
                            int bestDx = bestTurn.x - current.x;
                            int bestDz = bestTurn.z - current.z;
                            int nDx = n.x - current.x;
                            int nDz = n.z - current.z;
                            
                            // Score: prefer positive direction, then prioritize X over Z
                            int bestScore = (bestDx > 0 ? 2 : bestDx < 0 ? -2 : 0) + (bestDz > 0 ? 1 : bestDz < 0 ? -1 : 0);
                            int nScore = (nDx > 0 ? 2 : nDx < 0 ? -2 : 0) + (nDz > 0 ? 1 : nDz < 0 ? -1 : 0);
                            
                            if (nScore > bestScore) {
                                bestTurn = n;
                            }
                        }
                        next = bestTurn;
                    }
                }
                
                if (next == null) break; // Reached endpoint
                
                prev = current;
                current = next;
            }
            
            // Create line if it has more than one block
            if (line.size() > 1) {
                String lineId = idGenerator != null ? idGenerator.get() : "tmp_" + lines.size();
                RailLine railLine = new RailLine(
                        lineId,
                        colors[colorIndex % colors.length]
                );
                railLine.addBlocks(line);

                // Determine placer for the line via CoreProtect (majority vote among blocks)
                String placer = resolveLinePlacer(world, line);
                if (placer != null) {
                    railLine.setCreatedBy(placer);
                    int num = placerCounts.getOrDefault(placer, 0) + 1;
                    placerCounts.put(placer, num);
                    railLine.setName(placer + "'s Line: No. " + num);
                }

                lines.add(railLine);
                colorIndex++;
            }
        }
        
        // Handle isolated blocks and cycles (blocks with multiple connections)
        for (RailBlock rail : allRails) {
            if (!visited.contains(rail)) {
                Set<RailBlock> cluster = dfsCluster(rail, neighborMap, new HashSet<>());
                if (!cluster.isEmpty()) {
                    String lineId = idGenerator != null ? idGenerator.get() : "tmp_" + lines.size();
                    RailLine railLine = new RailLine(
                            lineId,
                            colors[colorIndex % colors.length]
                    );
                    railLine.addBlocks(cluster);

                    String placer = resolveLinePlacer(world, cluster);
                    if (placer != null) {
                        railLine.setCreatedBy(placer);
                        int num = placerCounts.getOrDefault(placer, 0) + 1;
                        placerCounts.put(placer, num);
                        railLine.setName(placer + "'s Line: No. " + num);
                    }

                    lines.add(railLine);
                    colorIndex++;
                    visited.addAll(cluster);
                }
            }
        }
        
        return lines;
    }

    private static String resolveLinePlacer(World world, Set<RailBlock> lineBlocks) {
        if (coreProtect == null || !coreProtect.isEnabled()) return null;
        Map<String, Integer> freq = new HashMap<>();
        for (RailBlock rb : lineBlocks) {
            org.bukkit.block.Block b = world.getBlockAt(rb.x, rb.y, rb.z);
            String p = coreProtect.getBlockPlacer(b);
            if (p != null && coreProtect.matchesFilters(b, p)) {
                freq.put(p, freq.getOrDefault(p, 0) + 1);
            }
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }
    
    /**
     * DFS to find all connected rail blocks using neighbor adjacency map.
     * Used for handling isolated clusters and cycles.
     */
    private static Set<RailBlock> dfsCluster(RailBlock start, Map<RailBlock, Set<RailBlock>> neighborMap, Set<RailBlock> visited) {
        Set<RailBlock> cluster = new HashSet<>();
        Stack<RailBlock> stack = new Stack<>();
        stack.push(start);
        
        while (!stack.isEmpty()) {
            RailBlock current = stack.pop();
            if (visited.contains(current)) continue;
            
            visited.add(current);
            cluster.add(current);
            
            Set<RailBlock> neighbors = neighborMap.getOrDefault(current, new HashSet<>());
            for (RailBlock neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    stack.push(neighbor);
                }
            }
        }
        
        return cluster;
    }

    // Removed shape-based direction helpers; clustering now uses spatial adjacency only.
    
    /**
     * Get TfL tube map colors.
     */
    private static String[] getTflColors() {
        return new String[]{
                "#E21836", // Bakerloo
                "#000000", // Central
                "#FFD300", // Circle
                "#00B0F0", // District
                "#EE7C0E", // Hammersmith & City
                "#A0A5A9", // Jubilee
                "#F391A0", // Metropolitan
                "#9B0056", // Northern
                "#E7A81E", // Piccadilly
                "#00A4EF", // Victoria
                "#0019A8"  // Waterloo & City
        };
    }
}

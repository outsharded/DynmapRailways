package com.fabianoley.dynmaprailways.scan;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Rail;
import org.bukkit.Bukkit;
import com.fabianoley.dynmaprailways.rail.RailLine;
import com.fabianoley.dynmaprailways.rail.RailLine.RailBlock;
import java.util.*;
import java.util.logging.Logger;

/**
 * Scans the world for rail blocks and clusters them into lines.
 */
public class RailScanner {
    
    private static final Logger logger = Logger.getLogger("DynmapRailways");
    private static final int[] DX = {-1, 1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, -1, 1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, -1, 1};
    
    /**
     * Scan a world for rail blocks and cluster them into rail lines.
     */
    public static List<RailLine> scanWorld(World world) {
        logger.info("Scanning " + world.getName() + " for rail blocks...");
        
        Set<RailBlock> allRails = findAllRails(world);
        logger.info("Found " + allRails.size() + " rail blocks");
        
        List<RailLine> lines = clusterRails(allRails);
        logger.info("Clustered into " + lines.size() + " rail lines");
        
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
     * Check if a block is a rail.
     */
    private static boolean isRailBlock(Block block) {
        return block.getBlockData() instanceof Rail;
    }
    
    /**
     * Cluster adjacent rail blocks into connected lines using BFS.
     */
    private static List<RailLine> clusterRails(Set<RailBlock> allRails) {
        List<RailLine> lines = new ArrayList<>();
        Set<RailBlock> visited = new HashSet<>();
        String[] colors = getTflColors();
        int colorIndex = 0;
        
        for (RailBlock rail : allRails) {
            if (!visited.contains(rail)) {
                // BFS to find all connected rails
                Set<RailBlock> cluster = bfsCluster(rail, allRails, visited);
                
                if (!cluster.isEmpty()) {
                    RailLine line = new RailLine(
                            "line_" + lines.size(),
                            colors[colorIndex % colors.length]
                    );
                    line.addBlocks(cluster);
                    lines.add(line);
                    colorIndex++;
                }
            }
        }
        
        return lines;
    }
    
    /**
     * BFS to find all connected rail blocks.
     */
    private static Set<RailBlock> bfsCluster(RailBlock start, Set<RailBlock> allRails, Set<RailBlock> visited) {
        Set<RailBlock> cluster = new HashSet<>();
        Queue<RailBlock> queue = new LinkedList<>();
        
        queue.add(start);
        visited.add(start);
        
        while (!queue.isEmpty()) {
            RailBlock current = queue.poll();
            cluster.add(current);
            
            // Check all 6 adjacent blocks
            for (int i = 0; i < 6; i++) {
                RailBlock neighbor = new RailBlock(
                        current.x + DX[i],
                        current.y + DY[i],
                        current.z + DZ[i],
                        current.world
                );
                
                if (allRails.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        
        return cluster;
    }
    
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

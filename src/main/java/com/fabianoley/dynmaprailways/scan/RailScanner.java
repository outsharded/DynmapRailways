package com.fabianoley.dynmaprailways.scan;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rail;
import org.bukkit.material.DetectorRail;
import org.bukkit.material.PoweredRail;
import org.bukkit.Bukkit;
import org.bukkit.Material;

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
     * Scan only the provided chunk set for rail blocks and cluster them into rail lines.
     * This does not attempt to load or generate chunks; callers should ensure chunks are loaded.
     */
    public static List<RailLine> scanChunks(World world, Set<org.bukkit.Chunk> chunks) {
        logger.info("Scanning " + chunks.size() + " chunks in " + world.getName() + " for rail blocks...");

        Set<RailBlock> rails = findRailsInChunks(world, chunks);
        logger.info("Found " + rails.size() + " rail blocks in targeted chunks");

        List<RailLine> lines = clusterRails(rails);
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
    private static List<RailLine> clusterRails(Set<RailBlock> allRails) {
        List<RailLine> lines = new ArrayList<>();
        Set<RailBlock> visited = new HashSet<>();
        String[] colors = getTflColors();
        int colorIndex = 0;
        
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
                RailLine railLine = new RailLine(
                        "line_" + lines.size(),
                        colors[colorIndex % colors.length]
                );
                railLine.addBlocks(line);
                lines.add(railLine);
                colorIndex++;
            }
        }
        
        // Handle isolated blocks and cycles (blocks with multiple connections)
        for (RailBlock rail : allRails) {
            if (!visited.contains(rail)) {
                Set<RailBlock> cluster = dfsCluster(rail, neighborMap, new HashSet<>());
                if (!cluster.isEmpty()) {
                    RailLine railLine = new RailLine(
                            "line_" + lines.size(),
                            colors[colorIndex % colors.length]
                    );
                    railLine.addBlocks(cluster);
                    lines.add(railLine);
                    colorIndex++;
                    visited.addAll(cluster);
                }
            }
        }
        
        return lines;
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

    // Direction enum for rail path following
    private enum Direction {
        NORTH(0, 0, -1), SOUTH(0, 0, 1), EAST(1, 0, 0), WEST(-1, 0, 0),
        UP(0, 1, 0), DOWN(0, -1, 0),
        // Diagonal directions for ascending rails
        ASCENDING_NORTH_UP(0, 1, -1), ASCENDING_SOUTH_UP(0, 1, 1),
        ASCENDING_EAST_UP(1, 1, 0), ASCENDING_WEST_UP(-1, 1, 0),
        ASCENDING_NORTH_DOWN(0, -1, -1), ASCENDING_SOUTH_DOWN(0, -1, 1),
        ASCENDING_EAST_DOWN(-1, -1, 0), ASCENDING_WEST_DOWN(1, -1, 0);
        
        public final int dx, dy, dz;
        Direction(int dx, int dy, int dz) { this.dx = dx; this.dy = dy; this.dz = dz; }
        public Direction opposite() {
            switch (this) {
                case NORTH: return SOUTH;
                case SOUTH: return NORTH;
                case EAST: return WEST;
                case WEST: return EAST;
                case UP: return DOWN;
                case DOWN: return UP;
                case ASCENDING_NORTH_UP: return ASCENDING_NORTH_DOWN;
                case ASCENDING_SOUTH_UP: return ASCENDING_SOUTH_DOWN;
                case ASCENDING_EAST_UP: return ASCENDING_EAST_DOWN;
                case ASCENDING_WEST_UP: return ASCENDING_WEST_DOWN;
                case ASCENDING_NORTH_DOWN: return ASCENDING_NORTH_UP;
                case ASCENDING_SOUTH_DOWN: return ASCENDING_SOUTH_UP;
                case ASCENDING_EAST_DOWN: return ASCENDING_EAST_UP;
                case ASCENDING_WEST_DOWN: return ASCENDING_WEST_UP;
            }
            throw new IllegalStateException();
        }
    }

    // Get all directions a rail shape connects to
    private static Set<Direction> getConnectedDirections(org.bukkit.block.data.Rail.Shape shape) {
        Set<Direction> dirs = new HashSet<>();
        switch (shape) {
            case NORTH_SOUTH:
                dirs.add(Direction.NORTH); dirs.add(Direction.SOUTH); break;
            case EAST_WEST:
                dirs.add(Direction.EAST); dirs.add(Direction.WEST); break;
            case ASCENDING_EAST:
                // Connects to: flat rail to the west, ascending rail above to the east
                dirs.add(Direction.WEST); 
                dirs.add(Direction.ASCENDING_EAST_UP);
                break;
            case ASCENDING_WEST:
                // Connects to: flat rail to the east, ascending rail above to the west
                dirs.add(Direction.EAST); 
                dirs.add(Direction.ASCENDING_WEST_UP);
                break;
            case ASCENDING_NORTH:
                // Connects to: flat rail to the south, ascending rail above to the north
                dirs.add(Direction.SOUTH); 
                dirs.add(Direction.ASCENDING_NORTH_UP);
                break;
            case ASCENDING_SOUTH:
                // Connects to: flat rail to the north, ascending rail above to the south
                dirs.add(Direction.NORTH); 
                dirs.add(Direction.ASCENDING_SOUTH_UP);
                break;
            case SOUTH_EAST:
                dirs.add(Direction.SOUTH); dirs.add(Direction.EAST); break;
            case SOUTH_WEST:
                dirs.add(Direction.SOUTH); dirs.add(Direction.WEST); break;
            case NORTH_EAST:
                dirs.add(Direction.NORTH); dirs.add(Direction.EAST); break;
            case NORTH_WEST:
                dirs.add(Direction.NORTH); dirs.add(Direction.WEST); break;
        }
        return dirs;
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

package com.fabianoley.dynmaprailways.rail;

import java.util.*;
import org.bukkit.Location;

/**
 * Represents a detected rail line (continuous connected rails).
 */
public class RailLine {
    private String id;
    private String name;
    private String color; // TfL tube map color (hex)
    private Set<RailBlock> blocks; // All connected rail blocks
    private String createdBy;
    private long createdAt;
    private boolean isActive;
    
    public RailLine(String id, String color) {
        this.id = id;
        this.name = "Line " + id;
        this.color = color;
        this.blocks = new HashSet<>();
        this.createdAt = System.currentTimeMillis();
        this.isActive = true;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public Set<RailBlock> getBlocks() { return new HashSet<>(blocks); }
    public String getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }
    public boolean isActive() { return isActive; }
    
    public void setName(String name) { this.name = name; }
    public void setColor(String color) { this.color = color; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setActive(boolean active) { this.isActive = active; }
    
    public void addBlock(RailBlock block) {
        blocks.add(block);
    }
    
    public void addBlocks(Collection<RailBlock> newBlocks) {
        blocks.addAll(newBlocks);
    }
    
    public int getBlockCount() {
        return blocks.size();
    }
    
    /**
     * Get bounding box of this rail line.
     */
    public int[] getBoundingBox() {
        if (blocks.isEmpty()) return null;
        
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (RailBlock block : blocks) {
            minX = Math.min(minX, block.x);
            maxX = Math.max(maxX, block.x);
            minZ = Math.min(minZ, block.z);
            maxZ = Math.max(maxZ, block.z);
        }
        
        return new int[]{minX, minZ, maxX, maxZ};
    }
    
    @Override
    public String toString() {
        return "RailLine{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", color='" + color + '\'' +
                ", blocks=" + blocks.size() +
                '}';
    }
    
    /**
     * Represents a single rail block.
     */
    public static class RailBlock {
        public int x, y, z;
        public String world;
        
        public RailBlock(int x, int y, int z, String world) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RailBlock)) return false;
            RailBlock other = (RailBlock) o;
            return x == other.x && y == other.y && z == other.z && world.equals(other.world);
        }
        
        @Override
        public int hashCode() {
            return (x * 31 + y) * 31 + z;
        }
    }
}


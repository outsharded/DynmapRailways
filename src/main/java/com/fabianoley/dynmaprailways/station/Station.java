package com.fabianoley.dynmaprailways.station;

import org.bukkit.Location;

/**
 * Represents a station on the railway network.
 */
public class Station {
    private String id;
    private String name;
    private int x;
    private int y = 64; // Default Y if not set
    private int z;
    private String world;
    private String createdBy;
    private long createdAt;
    private boolean isActive;
    
    public Station(String id, String name, int x, int z) {
        this(id, name, x, 64, z, null); // Default y=64, world=null
    }

    public Station(String id, String name, int x, int y, int z, String world) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
        this.createdAt = System.currentTimeMillis();
        this.isActive = true;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getWorld() { return world; }
    public String getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }
    public boolean isActive() { return isActive; }
    
    public void setName(String name) { this.name = name; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setActive(boolean active) { this.isActive = active; }
    public void setY(int y) { this.y = y; }
    public void setWorld(String world) { this.world = world; }
    
    public Location toLocation(int y) {
        return new Location(null, x, y, z);
    }
    
    @Override
    public String toString() {
        return "Station{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", x=" + x +
                ", z=" + z +
                '}';
    }
}

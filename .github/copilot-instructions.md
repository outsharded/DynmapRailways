# Copilot Instructions for DynmapRailways

## Project Overview

DynmapRailways is a Dynmap addon that **automatically detects and visualizes player-created rail networks** as tube map-style overlays. It scans loaded chunks for rail blocks, clusters them into continuous lines, and renders them with TfL colors on Dynmap maps.

### Architecture Essentials

The plugin has **4 core subsystems**:

1. **Rail Detection** (`scan/RailScanner.java`): Scans loaded chunks for all rail blocks
2. **Rail Clustering** (`scan/RailScanner.java`): Groups adjacent rails into connected lines using BFS
3. **Rail Storage** (`rail/RailLine.java`, `storage/RailwayDataStorage.java`): Persists detected lines to JSON
4. **Map Rendering** (`map/RailwayMapRenderer.java`): Renders lines and stations as Dynmap markers

### Key Design Decisions

- **Automatic Detection**: No manual waypoint entry - scans for actual rail blocks
- **Spatial Clustering**: Uses breadth-first search (BFS) to find connected rail networks
- **TfL Colors**: Automatic color assignment using London Underground colors
- **Async Scanning**: Scanning runs async to avoid server lag
- **Marker-based**: Uses Dynmap's Marker API for rendering (polylines + circles)
- **Optional Stations**: Players can manually add named stations on the map

## Key Developer Workflows

### Scanning for Rails

```bash
/railway scan
```

This triggers `RailScanner.scanWorld()` which:
1. Iterates all loaded chunks in all worlds
2. Finds blocks where `block.getBlockData() instanceof Rail`
3. Stores as `RailBlock(x, y, z, world)` objects
4. Clusters using BFS algorithm with 6-directional connectivity (up/down/left/right/forward/back)
5. Assigns TfL colors by index
6. Persists to `rails.json`
7. Updates Dynmap markers

### Building the Project

```bash
mvn clean package
```

Outputs JAR to `target/dynmap-railways-1.0.0.jar`. Shades GSON; all other dependencies are `provided` by the server.

### Creating Stations

Players manually place stations using `/railway station create` at their location. These are stored separately in `stations.json` and rendered as white circles on the map.

## Project-Specific Conventions

### RailBlock Class

Represents a single rail block: `(x, y, z, world)`. Used in sets for efficient clustering and deduplication.

```java
public static class RailBlock {
    public int x, y, z;
    public String world;
    // equals() and hashCode() implemented for set operations
}
```

### BFS Clustering Algorithm

Connectivity defined by 6-directional neighbors:
- `DX = {-1, 1, 0, 0, 0, 0}` (left/right)
- `DY = {0, 0, -1, 1, 0, 0}` (up/down)
- `DZ = {0, 0, 0, 0, -1, 1}` (forward/back)

Clusters are stored as `Set<RailBlock>` in each `RailLine`.

### Marker Rendering

Two Dynmap marker sets:
- `railway-lines`: PolyLineMarkers connecting all blocks in each line
- `railway-stations`: CircleMarkers (10px radius, white fill)

Points sorted by (x, z) to create sensible line paths.

### Color Management

11 TfL colors hardcoded in `RailScanner.getTflColors()`:
```
#E21836 (Bakerloo), #000000 (Central), #FFD300 (Circle), ...
```

Colors cycle by line index: `colors[lineIndex % colors.length]`

### ID Generation

- Rail line IDs: `line_0`, `line_1`, etc. (auto-numbered)
- Station IDs: Derived from name (e.g., "King's Cross" → `kings_cross`)

### Data Persistence

All data stored as JSON in `plugins/DynmapRailways/`:
- `rails.json`: Array of RailLine objects with nested RailBlock sets
- `stations.json`: Array of Station objects

### Plugin Lifecycle

1. **onEnable()**: Dynmap → Storage → CoreProtect (optional) → Renderer
2. **Scanning**: Async task triggered by `/railway scan` command
3. **Rendering**: Happens after scan completes, on main thread
4. **onDisable()**: Flush data via `RailwayDataStorage.shutdown()`

## Integration Points

### Dynmap Integration

- **MarkerAPI**: Gets `markerAPI` via `dynmapAPI.getMarkerAPI()`
- **Marker Sets**: Create via `createMarkerSet(id, label, owner, persistent)`
- **Markers**: PolyLineMarker for rails, CircleMarker for stations
- **Reflection**: All calls use reflection to avoid compile-time deps

### Bukkit/Spigot Integration

- **Async Tasks**: `Bukkit.getScheduler().runTaskAsynchronously()` for scanning
- **Sync Tasks**: `runTask()` for marker updates (must be on main thread)
- **Permissions**: `railway.admin` for scan/reload commands
- **Commands**: `/railway scan|list|station|reload`

## Common Patterns

### Scanning Workflow
```
Player runs /railway scan
  ↓
Command async task created
  ↓
RailScanner.scanWorld() for each world
  ↓
Collect all rail blocks
  ↓
BFS cluster algorithm
  ↓
Create RailLine objects with colors
  ↓
Save to storage
  ↓
Sync task: updateAllMarkers()
  ↓
Player sees results on Dynmap
```

### Adding a New Utility Class

1. Create in appropriate package (`scan/`, `map/`, `storage/`, etc.)
2. Use static methods for utilities
3. Log via `Logger.getLogger("DynmapRailways")`
4. Use reflection if accessing external APIs

## Common Pitfalls & Solutions

1. **Scan hangs server**: Scanning is async but updates must be sync. Keep marker creation efficient.
2. **No markers appear**: Check `markerAPI` is not null. Verify marker sets created successfully.
3. **Wrong rails detected**: `Rail instanceof` check only detects actual rail blocks, not powered/detector rails properly in some versions.
4. **Performance**: Scanning all loaded chunks can be slow. Consider limiting chunk range or chunking over multiple ticks.

## Testing Strategy

When adding features:
1. Test with manually placed rails in different patterns
2. Verify clustering finds all connected blocks
3. Check marker appearance on Dynmap web UI
4. Test edge cases: rail T-junctions, circular lines, isolated single blocks

## Next Development Steps

- **Improve Rail Detection**: Handle powered rails, detector rails, activator rails separately
- **CoreProtect Integration**: Filter by player who placed rails
- **Async Chunking**: Spread scanning over multiple server ticks
- **Web UI**: Add custom layer in `/dynmap/web` for better visualization
- **Line Naming**: Allow players to name detected lines
- **Export/Import**: Save and restore rail networks


# Development Guide

## Project Structure

```
DynmapRailways/
├── pom.xml                          # Maven configuration, build settings
├── README.md                        # Complete user documentation
├── QUICKSTART.md                    # Quick reference for users
├── .github/
│   └── copilot-instructions.md     # AI agent guidance (this file)
└── src/main/
    ├── java/com/fabianoley/dynmaprailways/
    │   ├── DynmapRailways.java                  # Main plugin entry point
    │   ├── commands/
    │   │   └── RailwayCommand.java             # All command handlers
    │   ├── rail/
    │   │   └── RailLine.java                   # Rail line data model
    │   ├── station/
    │   │   └── Station.java                    # Station data model
    │   ├── map/
    │   │   └── RailwayMapRenderer.java         # Dynmap visualization layer
    │   ├── integration/
    │   │   └── CoreProtectIntegration.java     # CoreProtect API wrapper
    │   └── storage/
    │       └── RailwayDataStorage.java         # File-based data persistence
    └── resources/
        ├── plugin.yml                          # Bukkit plugin metadata
        └── config.yml                          # Default configuration
```

## Build & Deploy

### Build JAR
```bash
mvn clean package
# Output: target/dynmap-railways-1.0.0.jar
```

### Deploy to Test Server
```bash
cp target/dynmap-railways-1.0.0.jar /path/to/server/plugins/
# Restart server
```

## Key Development Patterns

### 1. Reflection-Based Integration
Uses reflection to work without compile-time Dynmap/CoreProtect dependencies:
- All external API calls use `getClass().getMethod().invoke()`
- Allows optional plugin support without build-time coupling
- See: `RailwayMapRenderer.initialize()`, `CoreProtectIntegration.initialize()`

### 2. Data Model Serialization
GSON automatically serializes rail lines and stations to JSON:
- Models are simple POJOs with getters/setters
- `RailwayDataStorage` handles JSON I/O
- Files: `plugins/DynmapRailways/rails.json`, `stations.json`

### 3. Command Builder Pattern
Rail line creation uses a builder pattern per player:
- `RailLineBuilder` stored in a UUID-keyed map
- Waypoints accumulated across multiple `/railway add` calls
- Finalized into `RailLine` on `/railway add finish`

### 4. Marker Lifecycle
Dynmap markers are fully recreated on update:
- No partial updates - always full rebuild
- Prevents duplicates by clearing old markers first
- Called from `updateAllMarkers()` after data changes

## Adding Features

### Add a New Command

1. Add handler method in `RailwayCommand.onCommand()`
2. Add case to the switch statement
3. Register permissions in `plugin.yml`
4. Add tab completion in `onTabComplete()` if needed

### Extend Rail Line Capabilities

1. Add properties to `RailLine.java`
2. Add GSON serialization (automatic for simple types)
3. Update `RailwayDataStorage` save/load if adding complex types
4. Update renderer in `RailwayMapRenderer.renderRailLine()`

### Add CoreProtect Scanning

1. Implement `getRailPlacements()` with actual Query objects
2. Use returned records to cluster into rail lines
3. Consider async execution for large networks
4. See existing stub in `CoreProtectIntegration.java`

### Switch to Database Storage

1. Create `storage/DatabaseStorage.java` class
2. Implement same methods as `RailwayDataStorage` (load/save)
3. Add configuration option to select storage type
4. Update `DynmapRailways.java` initialization logic

## Testing Strategy

### Manual Testing (Current)

1. Create a test world on local server
2. Place rails and test `/railway` commands
3. Verify Dynmap marker creation
4. Check JSON files for correct data persistence

### Automated Testing (Future)

Add to `src/test/java/`:
```java
// Example test structure
class RailLineTest {
    @Test
    void testWaypointCreation() { }
    @Test
    void testColorAssignment() { }
    @Test
    void testJsonSerialization() { }
}
```

Use:
- **JUnit 5** for test framework
- **Mockito** for mocking Bukkit/Dynmap APIs
- **Temp directories** for file I/O tests

## Common Development Tasks

### Debug Markers Not Showing
1. Check marker set is created: `getMarkerSet()` isn't null
2. Verify waypoints have >= 2 points
3. Check color parsing in `parseColorToInt()`
4. Confirm Dynmap marker API methods exist

### Add Plugin Configuration Option
1. Add key to `config.yml`
2. Read in `DynmapRailways.onEnable()` via `getConfig().get()`
3. Pass to relevant component via constructor or setter
4. Document in README.md

### Fix Data Corruption
1. Stop server
2. Manually edit JSON files in `plugins/DynmapRailways/`
3. Restart server
4. Data will reload from JSON

## Performance Considerations

- **Large networks**: Consider async marker creation
- **CoreProtect scanning**: Limit `max-records` in config
- **JSON files**: Switch to database if > 1000 entities
- **Marker updates**: Only call `updateAllMarkers()` when data changes

## Next Development Priorities

1. **Full CoreProtect scanning** with graph clustering
2. **Async rendering** for large networks
3. **Database backend** support (MySQL/PostgreSQL)
4. **Web UI controls** in Dynmap frontend
5. **Import/Export** functionality
6. **Unit tests** and CI/CD pipeline


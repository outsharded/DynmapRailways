# DynmapRailways

A powerful Minecraft plugin addon for Dynmap that **automatically detects and visualizes player-created rail networks** as tube map-style overlays with full customization support.

## ✨ Features

- **🔍 Automatic Detection**: Scans loaded chunks for rail blocks—no manual waypoint entry required
- **🧠 Smart Clustering**: Groups adjacent rails into continuous networks using BFS algorithm with intelligent path-walking
- **🎨 Customizable Colors**: TfL London Underground colors by default, with full custom color support per line
- **📍 Station Markers**: Create named stations that render as circles on the map
- **🔧 Manual Line Creation**: Build custom lines by adding waypoints through commands
- **💾 Data Persistence**: Stores lines and stations in JSON with automatic merge on rescan
- **🎯 Duplicate Prevention**: Smart merge system prevents overlapping lines when rescanning
- **👥 Granular Permissions**: Personal line control, station management, and admin permissions
- **🔌 CoreProtect Integration**: Optional filtering by player-placed rails
- **⚡ Performance Optimized**: Configurable debug mode and minimum line length filtering
- **📊 Layer Priority**: Stations always render above lines for clear visualization

## 📦 Installation

1. **Requirements**:
   - Spigot/Paper 1.21+
   - Dynmap plugin
   - (Optional) CoreProtect plugin

2. **Download the plugin**:
   - From Github Releases

3. **Install**:
   - Copy `target/dynmap-railways-1.0.0.jar` to your server's `plugins/` folder
   - Restart your server

4. **Verify**:
   - Check logs for "DynmapRailways v1.0.0 enabled!"
   - Look for "Railway Lines" and "Railway Stations" in Dynmap layers

## 🎮 Usage

### Rail Scanning

Automatically detect rail networks in your world:

```bash
/railway scan                      # Scan all loaded chunks in all worlds
/railway scan <radius>             # Scan chunks within radius around you (admin only)
```

**What happens during scanning:**
1. Finds all rail blocks in loaded chunks
2. Clusters adjacent rails into connected networks using BFS pathfinding
3. Assigns unique short hex IDs (e.g., `a3f`, `b2c`)
4. Assigns TfL colors automatically
5. Merges with existing lines to prevent duplicates
6. Filters out lines shorter than configured minimum (default: 15 blocks)
7. Updates Dynmap visualization

### Listing Lines

View all detected and created lines:

```bash
/railway list                      # Show all lines with IDs, names, block counts, and colors
/railway line list                 # Same as above
```

**Example output:**
```
Rail Lines (3):
a3f: Northern Line (245 blocks, #E21836)
b2c: Circle Line (189 blocks, #FFD300)
5d1: My Custom Route (42 blocks, #888888)
```

### Line Management

#### Create Manual Lines

Build custom lines by adding waypoints:

```bash
/railway line create <name>        # Create a new manual line
/railway line addpoint <id>        # Add waypoint at your current location
```

**Example workflow:**
```bash
/railway line create My Express Route
# Output: Created line: My Express Route (ID: a3f)

# Stand at each waypoint location and run:
/railway line addpoint a3f
# Repeat at each location along your route
```

#### Customize Lines

Personalize your lines:

```bash
/railway line color <id> <#hex>    # Set custom color (e.g., #FF0000 for red)
/railway line rename <id> <name>   # Rename any line
/railway line remove <id>          # Delete a line
```

**Examples:**
```bash
/railway line color a3f #DC241F    # Set to red
/railway line rename a3f Northern Line
/railway line remove b2c           # Remove a line
```

### Station Management

Mark important locations on your rail network:

```bash
/railway station create <name>     # Create station at your location
/railway station list              # View all stations
/railway station remove <name>     # Remove a station
```

**Examples:**
```bash
/railway station create King's Cross
/railway station create Paddington Station
/railway station list
/railway station remove King's Cross
```

Stations render as white circles (customizable in config) on the map.

### Admin Commands

Administrative tools:

```bash
/railway reload                    # Reload configuration and refresh visualization
/railway debug info                # Show debug information
```

## 🔐 Permissions

### Permission Structure

```
railway.admin (default: op)
  ├─ Full control over everything
  ├─ Can scan for rails
  ├─ Can edit any line regardless of creator
  └─ Inherits all other permissions

railway.line.personal (default: true)
  ├─ Create manual lines
  ├─ Edit lines you created
  └─ Edit scanned lines where you're majority placer

railway.station (default: true)
  └─ Create and manage stations

railway.use (default: true)
  └─ Basic command access
```

### Permission Details

| Permission | Default | Description |
|------------|---------|-------------|
| `railway.use` | true | Use basic commands like `/railway list` |
| `railway.line.personal` | true | Create and manage your own lines |
| `railway.station` | true | Create and manage stations |
| `railway.admin` | op | Full administrative access |

### Permission Examples

**Regular Player** (`railway.line.personal` + `railway.station`):
- ✅ Create manual lines
- ✅ Customize their own lines (color, rename, addpoint, remove)
- ✅ Create stations anywhere
- ❌ Cannot scan for rails
- ❌ Cannot edit other players' lines

**Admin** (`railway.admin`):
- ✅ Everything above, plus:
- ✅ Scan for rails
- ✅ Edit any line
- ✅ Delete any line
- ✅ Full control

## ⚙️ Configuration

Config file: `plugins/DynmapRailways/config.yml`

```yaml
# General settings
general:
  debug: false                    # Enable debug logging (default: false)
  min-line-length: 15             # Minimum blocks for a line to be saved (default: 15)

# CoreProtect integration
coreprotect:
  enabled: true                   # Enable CoreProtect integration (default: true)
  player-placed-only: true        # Only render player-placed lines (default: true)
  min-age-days: 0                 # Minimum age filter in days, 0 = disabled
  max-age-days: 0                 # Maximum age filter in days, 0 = disabled
  ignore-players: []              # List of players to ignore

# Rail line appearance
lines:
  width: 3                        # Line width in pixels (default: 3)
  opacity: 1.0                    # Line opacity 0.0-1.0 (default: 1.0)

# Station appearance
stations:
  radius: 5.0                     # Circle radius in blocks (default: 5.0)
  fill-color: "#FFFFFF"           # Fill color hex (default: white)
  fill-opacity: 0.9               # Fill opacity 0.0-1.0 (default: 0.9)
  border-width: 2                 # Border width in pixels (default: 2)
  border-color: "#000000"         # Border color hex (default: black)
  border-opacity: 1.0             # Border opacity 0.0-1.0 (default: 1.0)
```

## 🎨 Color Palette

Default TfL London Underground colors:

| Line | Color | Hex |
|------|-------|-----|
| Bakerloo | Red | `#E21836` |
| Central | Black | `#000000` |
| Circle | Yellow | `#FFD300` |
| District | Green | `#00B0F0` |
| H&C | Pink | `#EE7C0E` |
| Jubilee | Grey | `#A0A5A9` |
| Metropolitan | Magenta | `#F391A0` |
| Northern | Dark Red | `#9B0056` |
| Piccadilly | Blue | `#E7A81E` |
| Victoria | Light Blue | `#00A4EF` |
| W&C | Navy | `#0019A8` |

Colors cycle automatically for scanned lines. Set custom colors with `/railway line color <id> <#hex>`.

## 🛠️ Development

### Project Structure

```
src/main/java/com/fabianoley/dynmaprailways/
├── DynmapRailways.java              # Main plugin class
├── commands/
│   └── RailwayCommand.java          # Command handler with permission checks
├── rail/
│   └── RailLine.java                # Rail line data model with overlap detection
├── station/
│   └── Station.java                 # Station data model
├── scan/
│   └── RailScanner.java             # BFS clustering algorithm with merge logic
├── map/
│   └── RailwayMapRenderer.java      # Dynmap marker rendering with layer priority
├── integration/
│   └── CoreProtectIntegration.java  # CoreProtect API wrapper
└── storage/
    └── RailwayDataStorage.java      # JSON persistence with ID generation
```

### Building from Source

```bash
# Clone the repository
git clone <repo-url>
cd DynmapRailways

# Build with Maven
mvn clean package

# Output: target/dynmap-railways-1.0.0.jar
```

### Key Technical Details

**Rail Clustering Algorithm:**
- Uses breadth-first search (BFS) with 6-directional connectivity
- Traces lines from endpoints (1 neighbor) following direction preference
- Handles junctions, curves, and elevation changes
- Prevents diagonal connections for clean tube map rendering

**Smart Merge System:**
- Validates existing lines (checks if 50%+ rails still exist)
- Detects overlaps (skips new lines with >70% overlap)
- Prevents duplicate lines when rescanning
- Preserves manual lines and customizations

**ID Generation:**
- Short hex IDs (e.g., `a3f`, `5d1a`)
- Starts at 3 characters, grows if collisions occur
- Collision-resistant up to 8 characters (268M combinations)
- Deterministic fallback to timestamp hex

**Rendering Order:**
- Rail lines: Layer priority 5
- Stations: Layer priority 10
- Ensures stations always render above lines

### API Integration

**Dynmap API:**
- Uses reflection to avoid compile-time dependencies
- Creates separate marker sets for lines and stations
- PolyLineMarkers for rail lines
- CircleMarkers for stations

**CoreProtect API:**
- Optional integration (enabled in config)
- Filters rails by player who placed them
- Majority vote determines line creator
- Respects player ignore list

### Data Files

**Location:** `plugins/DynmapRailways/`

**rails.json:**
```json
[
  {
    "id": "a3f",
    "name": "Northern Line",
    "color": "#E21836",
    "blocks": [{"x": 100, "y": 64, "z": 200, "world": "world"}, ...],
    "createdBy": "PlayerName",
    "createdAt": 1735362000000,
    "isActive": true
  }
]
```

**stations.json:**
```json
[
  {
    "id": "kings_cross",
    "name": "King's Cross",
    "x": 100,
    "y": 64,
    "z": 200,
    "world": "world",
    "createdBy": "PlayerName",
    "createdAt": 1735362000000,
    "isActive": true
  }
]
```

### Testing

1. **Build fresh JAR** and install on test server
2. **Create test rail networks** with various patterns
3. **Test scanning**: `/railway scan`
4. **Verify merging**: Rescan to ensure no duplicates
5. **Test manual lines**: Create, add waypoints, customize
6. **Check permissions**: Test with different permission levels
7. **Validate rendering**: Check Dynmap web UI for proper display

## 🐛 Troubleshooting

**Lines not appearing on map:**
- Check Dynmap layers panel—ensure "Railway Lines" is enabled
- Run `/railway reload` to refresh visualization
- Verify lines exist: `/railway list`
- Check console for errors during rendering

**Duplicate lines after rescanning:**
- Ensure you're using the latest version with smart merge
- Lines with <70% overlap are considered unique
- Delete duplicates: `/railway line remove <id>`

**Permission denied errors:**
- Check player has correct permission nodes
- Verify they created the line they're trying to edit
- Admins bypass all ownership checks

**CoreProtect integration not working:**
- Ensure CoreProtect is installed and enabled
- Check `coreprotect.enabled: true` in config
- Verify plugin loads after CoreProtect (check plugin.yml softdepend)

**Performance issues:**
- Enable `general.debug: false` to reduce log spam
- Increase `general.min-line-length` to filter small fragments
- Scan smaller areas with `/railway scan <radius>` instead of full scan

## 📝 License

All rights reserved.

## 🤝 Contributing

For issues or feature requests, please submit detailed reports including:
- Plugin version
- Server version (Spigot/Paper)
- Dynmap version
- Steps to reproduce
- Expected vs actual behavior
- Console logs (if applicable)

## 🙏 Credits

- TfL color palette from London Underground
- Dynmap integration
- CoreProtect integration (optional)

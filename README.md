# DynmapRailways

A Minecraft plugin addon for Dynmap that **automatically detects and visualizes player-created rail networks** as a tube map-style overlay.

## Features

- **Automatic Detection**: Scans loaded chunks for rail blocks—no manual waypoint entry
- **Smart Clustering**: Groups adjacent rails into continuous networks using BFS algorithm
- **Tube Map Colors**: Automatically assigns TfL London Underground colors to each detected line
- **Station Markers**: Manually mark stations as white circles on the map
- **Data Persistence**: Stores detected lines in JSON files for persistence
- **Real-time Rendering**: Updates Dynmap visualization on-demand via simple commands
- **Optional CoreProtect**: Can filter rails by player who placed them (integration ready)

## Requirements

- Minecraft 1.21.8+
- Spigot/Paper server
- Dynmap plugin
- Java 21+
- (Optional) CoreProtect plugin for player-placed rail filtering

## Installation

1. Build the plugin:
   ```bash
   mvn clean package
   ```

2. Copy the generated JAR from `target/` to your server's `plugins/` folder

3. Restart your server

4. Check logs to confirm plugin loaded (look for "Railway map renderer initialized")

## Usage

### Scanning for Rails

```
/railway scan                     # Scan all loaded chunks for rail blocks and cluster them
```

This command:
1. Scans all loaded chunks across all worlds
2. Finds all rail blocks
3. Groups adjacent rails into continuous networks (lines)
4. Assigns TfL colors automatically
5. Saves to `rails.json`
6. Updates Dynmap visualization with detected lines

**Pro tip**: Run after building a new rail network or after adding new rails to your world.

### Viewing Detected Lines

```
/railway list                     # Show all detected rail lines with their colors
```

### Managing Stations

```
/railway station create <name>    # Create a station at your current location
/railway station list             # View all created stations
/railway station remove <name>    # Remove a station
```

Stations appear as white circles on the Dynmap layers panel under "Railway Stations".

### Admin Commands

```
/railway reload                   # Manually refresh the Dynmap visualization
```

## How It Works

When you run `/railway scan`:

1. **Detection Phase**: The plugin scans all loaded chunks looking for rail blocks
2. **Clustering Phase**: It uses a breadth-first search (BFS) algorithm to group adjacent rails into continuous networks
3. **Color Assignment**: Each detected network is automatically assigned a unique TfL color
4. **Persistence**: The rail networks are saved to `plugins/DynmapRailways/rails.json`
5. **Rendering Phase**: The map renderer updates Dynmap with polylines for each network

## Configuration

Edit `config.yml` in `plugins/DynmapRailways/` to customize (future versions):

- **Map rendering options**: Layer priorities, marker styles
- **CoreProtect integration**: Enable/disable player filtering
- **Auto-scanning**: Automatic scanning intervals
- **Colors**: Custom color palettes

## Architecture

### Core Components

- **RailScanner**: Detects rail blocks and clusters them using BFS
- **RailLine**: Represents a detected continuous rail network with blocks
- **Station**: Manually-marked locations appearing as white circles
- **RailwayMapRenderer**: Converts rail data to Dynmap polylines and markers
- **RailwayDataStorage**: Persists data to JSON files
- **CoreProtectIntegration**: Optional player-placed rail filtering (ready for implementation)

### Data Flow

1. Players create rail lines using commands or auto-scanned from CoreProtect
2. Data stored in `rails.json` and `stations.json`
3. RailwayMapRenderer reads data and creates Dynmap markers
4. Updates sync automatically when map reloads

## Permissions

- `railway.use`: Use basic railway commands (default: true)
- `railway.admin`: Use admin commands (default: op)

## Development

### Project Structure

```
src/main/java/com/fabianoley/dynmaprailways/
├── DynmapRailways.java           # Main plugin class
├── commands/
│   └── RailwayCommand.java       # Command handler
├── rail/
│   └── RailLine.java             # Rail line data model
├── station/
│   └── Station.java              # Station data model
├── map/
│   └── RailwayMapRenderer.java   # Dynmap rendering
├── integration/
│   └── CoreProtectIntegration.java # CoreProtect API wrapper
└── storage/
    └── RailwayDataStorage.java   # Data persistence layer
```

### Building

```bash
mvn clean install
```

### Dependencies

- Spigot API (provided by server)
- Dynmap Core API 3.8+
- CoreProtect API 21.4+ (optional)
- GSON 2.10.1 (shaded)

## Troubleshooting

**Plugin won't load**: Check that Dynmap is installed and enabled first.

**Rails not showing**: Ensure CoreProtect integration is enabled if auto-scanning, or manually create lines with `/railway create`.

**Performance issues**: Reduce `coreprotect.max-records` if scanning is slow.

## License

All rights reserved.

## Support

For issues or feature requests, check the plugin configuration and ensure all dependencies are properly installed.

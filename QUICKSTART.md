# DynmapRailways - Quick Start Guide

## Installation

1. Ensure your Spigot/Paper server has **Dynmap** installed and running
2. Copy `dynmap-railways-1.0.0.jar` from `target/` to your server's `plugins/` folder
3. Restart your server
4. Configuration files will be auto-generated in `plugins/DynmapRailways/`

## First Steps

### Create Your First Rail Line

```
/railway create "Northern Line"      # Start creating a line
/railway add                          # Add waypoint at current position
/railway add                          # Add another waypoint
/railway add finish                   # Save the line
```

### Add Stations

```
/railway station create "King's Cross"    # Create station at your location
/railway station list                     # View all stations
```

### View Your Network

1. Open Dynmap in your browser
2. Look for "Railway Lines" and "Railway Stations" in the layers panel
3. Stations appear as white circles, lines as colored polylines

## Commands

| Command | Usage | Permission |
|---------|-------|-----------|
| `/railway create <name>` | Start creating a rail line | railway.use |
| `/railway add` | Add waypoint at current position | railway.use |
| `/railway add finish` | Complete the rail line | railway.use |
| `/railway list` | View all rail lines | railway.use |
| `/railway station create <name>` | Create a station | railway.use |
| `/railway station list` | View all stations | railway.use |
| `/railway scan` | Auto-scan CoreProtect (requires CoreProtect) | railway.admin |
| `/railway reload` | Reload map visualization | railway.admin |

## Configuration

Edit `plugins/DynmapRailways/config.yml`:

- **enabled**: Turn plugin on/off
- **map.use-white-background**: Show rails on white background instead of flat map
- **coreprotect.enabled**: Enable CoreProtect scanning
- **colors**: Customize rail line colors (TfL colors by default)
- **storage.type**: Choose "file" or "database" storage

## How It Works

1. **Create rail lines** using commands or auto-detect from CoreProtect
2. **Assign TfL colors** automatically (cycling through 11 classic tube map colors)
3. **Add stations** as waypoints on your map
4. **View on Dynmap** - all updates sync in real-time

## Optional: CoreProtect Integration

If CoreProtect is installed, you can:

1. Run `/railway scan` to detect player-placed rail blocks
2. Enable `auto-scan` in config.yml for continuous detection
3. Adjust `max-records` to control scan performance

## Troubleshooting

**Plugin won't start**: Check Dynmap is installed and enabled first

**Rails not appearing**: Check `config.yml` is valid YAML and `marker-api` is enabled

**Performance issues**: Reduce `coreprotect.max-records` or disable auto-scan

## Building from Source

```bash
mvn clean package
```

JAR will be in `target/dynmap-railways-1.0.0.jar`

## Support

For issues, check:
1. Server logs for errors
2. That Dynmap is running properly
3. File permissions on `plugins/DynmapRailways/`

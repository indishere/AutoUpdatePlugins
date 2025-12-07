## AutoUpdatePlugins

[Chinese Document](https://github.com/ind/AutoUpdatePlugins/blob/main/extra/README_Original.md) — 
[English Document](https://github.com/ind/AutoUpdatePlugins/blob/main/README.md)

Download: [https://modrinth.com/plugin/AutoUpdatePlugins](https://modrinth.com/plugin/AutoUpdatePlugins)

---

## Features and Commands

* `/aup` — Show plugin information

  * `/aup reload` — Reload configuration
  * `/aup update` — Run an update manually
  * `/aup log` — View full logs
  * `/aup stop` — Cancel the current update task

### Supported Features

* Uses an `update` directory to install plugin updates
* Automatically detects download links from plugin release pages

  * Supports: GitHub, Jenkins, SpigotMC, Modrinth, Bukkit, Guizhan Build Station v2, MineBBS, CurseForge
  * Can download GitHub pre-release versions
* Supports selecting specific files inside a release
* File integrity checking
* Caches last update to prevent duplicate downloads
* Prevents reinstalling identical versions
* Per-plugin configurable update settings
* Optional certificate verification
* Customizable log levels
* Multi-language support
* (Planned) Ability to run system commands after updating

---

## Example Log Output

```yaml
[INFO]: [AUP] Update check will run in 64 seconds, repeating every 14400 seconds.
[INFO]: [AUP] [## Starting automatic update ##]
[INFO]: [AUP] [EssentialsX.jar] Updating...
[INFO]: [AUP] [EssentialsX.jar] [GitHub] Version found: https://github.com/EssentialsX/Essentials/releases/download/2.20.1/EssentialsX-2.20.1.jar
[INFO]: [AUP] [EssentialsX.jar] Update complete [1.17MB] -> [2.92MB]
[INFO]: [AUP] [EssentialsXChat.jar] Updating...
[INFO]: [AUP] [EssentialsXChat.jar] [GitHub] Version found: https://github.com/EssentialsX/Essentials/releases/download/2.20.1/EssentialsXChat-2.20.1.jar
[INFO]: [AUP] [EssentialsXChat.jar] Update complete [0.01MB] -> [0.01MB]
[INFO]: [AUP] [CoreProtect.jar] Updating...
[INFO]: [AUP] [CoreProtect.jar] [Modrinth] Version found: https://cdn.modrinth.com/data/Lu3KuzdV/versions/w3P6ufP1/CoreProtect-22.2.jar
[INFO]: [AUP] [CoreProtect.jar] Already up to date.
...
[INFO]: [AUP] [## All updates completed ##]
[INFO]: [AUP] - Time taken: 268 seconds
[INFO]: [AUP] - Failed: 2, Updated: 22, Total: 24
[INFO]: [AUP] - Network requests: 48, Downloaded: 40.10MB
```

---

## Configuration

```yaml
# How long to wait before running the first update (in seconds)
startupDelay: 64

# How often to run updates after the first
startupCycle: 14400  # 4 hours

# Directory for update files (must end with "/")
updatePath: './plugins/update/'

# Cache directory for downloads
tempPath: './plugins/AutoUpdatePlugins/temp/'

# Directory of installed plugins (used for hash checks)
filePath: './plugins/'

# Enable use of previous update info to skip duplicates
enablePreviousUpdate: true

# File integrity check (jar/zip)
zipFileCheck: true
zipFileCheckList: '\\.(?:jar|zip)$'

# Skip update if file hash matches existing file
ignoreDuplicates: true

# Proxy settings
proxy:
  type: DIRECT
  host: '127.0.0.1'
  port: 7890

# Custom request headers
setRequestProperty:
  - name: 'User-Agent'
    value: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

# Log levels to enable
logLevel:
  - 'DEBUG'
  - 'MARK'
  - 'INFO'
  - 'WARN'
  - 'NET_WARN'

# Plugin update list
list:
  - file: 'AutoUpdatePlugins.jar'
    url: https://github.com/ApliNi/AutoUpdatePlugins/
```

---

## Example Configurations

```yaml
# EssentialsX (GitHub)
# - file: 'EssentialsX.jar'
#   url: https://github.com/EssentialsX/Essentials
#   get: 'EssentialsX-([0-9.]+)\\.jar'

# EssentialsXChat
# - file: 'EssentialsXChat.jar'
#   url: https://github.com/EssentialsX/Essentials
#   get: 'EssentialsXChat-([0-9.]+)\\.jar'

# Geyser (Direct URL)
# - file: 'Geyser-Spigot.jar'
#   url: https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot

# ViaVersion (Jenkins)
# - file: 'ViaVersion-DEV.jar'
#   url: https://ci.viaversion.com/job/ViaVersion-DEV/

# CoreProtect (Modrinth)
# - file: 'CoreProtect.jar'
#   url: https://modrinth.com/plugin/coreprotect/
```

---

## All Supported Config Options (Advanced)

```yaml
# file: File name
# url: Download page or direct URL
# tempPath: Temporary download directory
# updatePath: Directory to place updates
# filePath: Final file location after installation
# path: Overrides both updatePath & filePath
# get: Regex to match a specific file
# getPreRelease: Whether to allow GitHub pre-release downloads
# zipFileCheck: Enable zip integrity checking
# ignoreDuplicates: Disable hash comparison
```

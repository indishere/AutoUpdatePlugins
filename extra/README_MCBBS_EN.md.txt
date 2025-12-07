[postbg]bg5.png[/postbg][markdown]

https://github.com/ApliNi/AutoUpdatePlugins

AutoUpdatePlugins

[Chinese Document]
 -- [English Document]

A better automatic plugin updater

Download: https://modrinth.com/plugin/AutoUpdatePlugins

Features & Commands

/aup — show plugin info

/aup reload — reload configuration

/aup update — run update manually

/aup log — view full update logs

/aup stop — stop the current update task

 Uses the update directory to install plugin updates

 Automatically finds download links from plugin release pages

Supports: GitHub, Jenkins, Spigot, Modrinth, Bukkit, Guizhan Build v2

Supports downloading GitHub pre-release versions

 Supports matching multiple files within the same release

Works on: GitHub, Jenkins, Modrinth

 Supports file integrity checks

 Caches previous update information to avoid re-downloading

 Avoids reinstalling identical updates

 Per-file configurable update settings

 Optional certificate verification

 Customizable log output levels

 Multi-language support!

 Run system commands after updating (planned)

[Usage Statistics]

<a href="https://bstats.org/plugin/bukkit/ApliNi-AutoUpdatePlugins/20629">
</a>

Example Log Output

[INFO]: [AUP] Update check will run in 64 seconds, and repeat every 14400 seconds
[INFO]: [AUP] [## Starting automatic update ##]
[INFO]: [AUP] [EssentialsX.jar] Updating...
[INFO]: [AUP] [EssentialsX.jar] [GitHub] Found version: https://github.com/EssentialsX/Essentials/releases/download/2.20.1/EssentialsX-2.20.1.jar
[INFO]: [AUP] [EssentialsX.jar] Update complete [1.17MB] -> [2.92MB]
[INFO]: [AUP] [EssentialsXChat.jar] Updating...
[INFO]: [AUP] [EssentialsXChat.jar] [GitHub] Found version: https://github.com/EssentialsX/Essentials/releases/download/2.20.1/EssentialsXChat-2.20.1.jar
[INFO]: [AUP] [EssentialsXChat.jar] Update complete [0.01MB] -> [0.01MB]
[INFO]: [AUP] [CoreProtect.jar] Updating...
[INFO]: [AUP] [CoreProtect.jar] [Modrinth] Found version: https://cdn.modrinth.com/data/Lu3KuzdV/versions/w3P6ufP1/CoreProtect-22.2.jar
[INFO]: [AUP] [CoreProtect.jar] File is already the latest version
...
[INFO]: [AUP] [Dynmap_Webmap.jar] Updating...
[WARN]: [AUP] [Dynmap_Webmap.jar] [HTTP] Request failed? (403): https://legacy.curseforge.com/minecraft/bukkit-plugins/dynmap
[WARN]: [AUP] [Dynmap_Webmap.jar] [CurseForge] Failed to parse direct download link, skipping update
[INFO]: [AUP] [## All updates completed ##]
[INFO]: [AUP]   - Time: 268 seconds
[INFO]: [AUP]   - Failed: 2, Updated: 22, Total: 24
[INFO]: [AUP]   - Network requests: 48, Downloaded data: 40.10MB

Configuration
# How long to wait after the server finishes starting before running the first update (seconds)
startupDelay: 64

# How frequently updates repeat after the first run (seconds, requires restart if changed)
startupCycle: 14400 # 4 hours

# Directory used for plugin update files, must match the setting in bukkit.yml
# NOTE: Path must end with "/"
updatePath: './plugins/update/'

# Download cache directory, no need to modify
# Newly downloaded .jar files are saved here first, verified, then moved to the update directory
tempPath: './plugins/AutoUpdatePlugins/temp/'

# Directory of currently installed plugins/files, used for hash checking
filePath: './plugins/'

# Use previous update record (temp.yml) to skip re-downloads
enablePreviousUpdate: true

# Integrity check for .jar/.zip files. Attempts to open them as archives; if it fails, file is incomplete
zipFileCheck: true
# Regex to decide which files use integrity checking
zipFileCheckList: '\.(?:jar|zip)$'

# If downloaded file hash equals the existing plugin hash, do not replace it (MD5 check)
ignoreDuplicates: true

# Disable certificate validation (requires restart)
disableCertificateVerification: false

# Add or modify headers for HTTP requests
setRequestProperty:
  - name: 'User-Agent'
    value: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

# Enable/disable log levels
logLevel:
  - 'DEBUG'     # Debugging logs
  - 'MARK'      # Same as DEBUG but highlighted
  - 'INFO'      # Regular log messages
  - 'WARN'      # Warning messages
  - 'NET_WARN'  # Network module warnings

# Plugin update list
# URLs support automatic detection from: GitHub, Jenkins, SpigotMC, Modrinth, Bukkit, Guizhan Build v2
# Other URLs will be treated as direct download links
# GitHub/Jenkins/Modrinth pages support "get" regex to choose a specific file
# For GitHub, you can enable getPreRelease: true to download pre-release builds
list:

  - file: 'AutoUpdatePlugins_AutoUpdate.jar'
    url: https://github.com/ApliNi/AutoUpdatePlugins/

Example Config
#  - file: 'EssentialsX.jar' # GitHub
#    url: https://github.com/EssentialsX/Essentials
#    get: 'EssentialsX-([0-9.]+)\.jar'

#  - file: 'EssentialsXChat.jar' # Match another file in same release
#    url: https://github.com/EssentialsX/Essentials
#    get: 'EssentialsXChat-([0-9.]+)\.jar'

#  - file: 'Geyser-Spigot.jar' # Direct URL
#    url: https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot

#  - file: 'ViaVersion-DEV.jar' # Jenkins
#    url: https://ci.viaversion.com/job/ViaVersion-DEV/

#  - file: 'CoreProtect.jar' # Modrinth
#    url: https://modrinth.com/plugin/coreprotect/

All available config options

(Only modify these if you truly understand what you're doing)

# String file;              # File name
# String url;               # Download page or direct URL
# String tempPath;          # Temporary download path
# String updatePath;        # Update directory
# String filePath;          # Location of installed plugin
# String path;              # Overrides updatePath & filePath
# String get;               # Regex to pick a specific file (GitHub/Jenkins/Modrinth only)
# boolean getPreRelease;    # Allow GitHub pre-release downloads
# boolean zipFileCheck;     # Enable zip integrity check
# boolean ignoreDuplicates; # Disable duplicate-hash check

"MCBBS Note

All code used in this plugin is original.
No borrowed/copied code." ~The Orignal Document from ApliNi

The plugin is non-profit, free to distribute, and reselling is strictly prohibited.

[/markdown]
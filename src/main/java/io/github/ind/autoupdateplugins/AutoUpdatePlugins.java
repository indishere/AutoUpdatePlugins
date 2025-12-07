package io.github.ind.autoupdateplugins;

import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipException;

public final class AutoUpdatePlugins extends JavaPlugin implements Listener, CommandExecutor, TabExecutor {
    // Prevent running update multiple times at once
    boolean lock = false;
    // If true, reload config after the current update finishes
    boolean awaitReload = false;
    // Timer for scheduled update checks
    Timer timer = null;
    // Async update task
    CompletableFuture<Void> future = null;
    // Last command sender who used /aup
    CommandSender lastSender = null;

    File tempFile;
    FileConfiguration temp;

    List<String> logList = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("aup")).setExecutor(this);

        // bStats
        Metrics metrics = new Metrics(this, 28258);
        metrics.addCustomChart(new Metrics.SingleLineChart("Plugins",
                () -> ((List<?>) Objects.requireNonNull(getConfig().get("list"))).size()));

        // Disable certificate verification globally (only if explicitly enabled in config)
        if (getConfig().getBoolean("disableCertificateVerification", false)) {
            // Create a TrustManager that trusts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };
            // Get default SSLContext
            SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            // Set default SSLSocketFactory
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        }
    }

    public void saveDate() {
        try {
            temp.save(tempFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @EventHandler // Fired when the server has finished loading
    public void onServerLoad(ServerLoadEvent event) {
        // Run timer setup asynchronously
        CompletableFuture.runAsync(this::setTimer);

        // Check deprecated configuration
        if (getConfig().getBoolean("debugLog", false)) {
            getLogger().warning("`debugLog` is deprecated, please use `logLevel` instead to control which log levels are enabled.");
        }

        // Check for missing configuration keys
        if (getConfig().get("setRequestProperty") == null) {
            getLogger().warning("Missing config `setRequestProperty` - used to edit HTTP request headers.");
        }
        if (getConfig().get("message") == null) {
            getLogger().warning("Missing config `message` - plugin message configuration.");
        }
    }

    public void loadConfig() {
        // Export locale config files
        List<String> locales = List.of("config_en.yml");
        getPath("./plugins/AutoUpdatePlugins/Locales");
        for (String li : locales) {
            File file = new File("./plugins/AutoUpdatePlugins/Locales/" + li);
            if (file.exists()) {
                continue;
            }
            try {
                file.createNewFile();
                ByteStreams.copy(
                        Objects.requireNonNull(getResource("Locales/" + li)),
                        new FileOutputStream(file));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Load messages and config
        reloadConfig();
        loadMessage();

        tempFile = new File("./plugins/AutoUpdatePlugins/temp.yml");
        temp = YamlConfiguration.loadConfiguration(tempFile);
        if (temp.get("previous") == null) {
            temp.set("previous", new HashMap<>());
        }
        saveDate();
    }

    public void setTimer() {
        long startupDelay = getConfig().getLong("startupDelay", 64);
        long startupCycle = getConfig().getLong("startupCycle", 61200);
        // Check if update interval is too low
        if (startupCycle < 256 && !getConfig().getBoolean("disableUpdateCheckIntervalTooLow", false)) {
            getLogger().warning(m.updateCheckIntervalTooLow);
            startupCycle = 512;
        }
        // Start timer
        getLogger().info(m.piece(m.timer, startupDelay, startupCycle));
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        timer = new Timer();
        timer.schedule(new updatePlugins(), startupDelay * 1000, startupCycle * 1000);
    }

    @Override // Tab completion for /aup
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of(
                    "reload",   // Reload plugin config
                    "update",   // Run update now
                    "log",      // View log
                    "stop"      // Stop current update
            );
        }
        return null;
    }

    @Override // Handle /aup command
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        lastSender = sender;

        // Default: show plugin info and help
        if (args.length == 0) {
            sender.sendMessage("""
                    IND > AutoUpdatePlugins: Automatic plugin updater
                      Commands:
                        - /aup reload - Reload configuration
                        - /aup update - Run update now
                        - /aup log    - View full update log
                        - /aup stop   - Stop current update""");
            return true;
        }

        // Reload configuration
        else if (args[0].equals("reload")) {
            if (lock) {
                awaitReload = true;
                sender.sendMessage("[AUP] " + m.commandReloadOnUpdating);
                return true;
            }
            loadConfig();
            sender.sendMessage("[AUP] " + m.commandReloadOK);
            setTimer();
            return true;
        }

        // Run update manually
        else if (args[0].equals("update")) {
            if (lock && !getConfig().getBoolean("disableLock", false)) {
                sender.sendMessage("[AUP] " + m.commandRepeatedRunUpdate);
                return true;
            }
            sender.sendMessage("[AUP] " + m.commandUpdateStart);
            new Timer().schedule(new updatePlugins(), 0);
            return true;
        }

        // Show full log
        else if (args[0].equals("log")) {
            sender.sendMessage("[AUP] " + m.commandFullLog);
            for (String li : logList) {
                sender.sendMessage("  | " + li);
            }
            return true;
        }

        // Stop current update
        else if (args[0].equals("stop")) {
            if (lock) {
                future.cancel(true);
                sender.sendMessage("[AUP] " + m.commandStopUpdateIng);
            } else {
                sender.sendMessage("[AUP] " + m.stopUpdate);
            }
        }
        return false;
    }

    private class updatePlugins extends TimerTask {
        String _fileName = "[???] ";    // Display name of current file
        String _nowParser = "[???] ";   // Name of the current parser (GitHub / Jenkins / etc)
        int _fail = 0;                  // Number of failed updates
        int _success = 0;               // Number of successful updates
        int _updateFul = 0;             // Number of update tasks processed
        int _allRequests = 0;           // Number of HTTP requests made
        long _startTime;                // Start time for timing
        float _allFileSize = 0;         // Total downloaded file size in bytes

        // Per-entry config values
        String c_file;              // Target file name
        String c_url;               // Download page or direct URL
        String c_tempPath;          // Temp download path (global default unless overridden)
        String c_updatePath;        // Update directory (global default unless overridden)
        String c_filePath;          // Final install path (global default unless overridden)
        String c_get;               // Regex to pick specific file (GitHub, Jenkins, Modrinth)
        boolean c_zipFileCheck;     // Enable zip/jar integrity check
        boolean c_getPreRelease;    // Allow GitHub pre-release

        public void run() {
            // Run in async thread
            future = CompletableFuture.runAsync(() -> {
                // Prevent multiple concurrent runs
                if (lock && !getConfig().getBoolean("disableLock", false)) {
                    log(logLevel.WARN, m.repeatedRunUpdate);
                    return;
                }
                lock = true;

                // Execute update logic
                runUpdate();

                // Print summary
                log(logLevel.INFO, m.updateFul);
                log(logLevel.INFO, "  - " + m.piece(m.updateFulTime, Math.round((System.nanoTime() - _startTime) / 1_000_000_000.0)));

                String st = "  - ";
                if (_fail != 0) {
                    st += m.piece(m.updateFulFail, _fail);
                }
                if (_success != 0) {
                    st += m.piece(m.updateFulUpdate, _success);
                }
                log(logLevel.INFO, st + m.piece(m.updateFulOK, _updateFul));

                log(logLevel.INFO, "  - " + m.piece(m.updateFulNetRequest, _allRequests) + m.piece(m.updateFulDownloadFile, String.format("%.2f", _allFileSize / 1048576)));

                // Handle delayed reload if requested during update
                if (awaitReload) {
                    awaitReload = false;
                    loadConfig();
                    setTimer();
                    getLogger().info("[AUP] " + m.logReloadOK);
                    if (lastSender != null && lastSender instanceof Player) {
                        lastSender.sendMessage("[AUP] " + m.logReloadOK);
                    }
                }

                lock = false;
            });
        }

        public void runUpdate() {

            logList = new ArrayList<>();    // Clear previous log list
            _startTime = System.nanoTime(); // Start timing

            log(logLevel.INFO, m.updateStart);

            List<?> list = (List<?>) getConfig().get("list");
            if (list == null) {
                log(logLevel.WARN, m.configErrList);
                return;
            }

            for (Object _li : list) {
                // If /aup stop was used
                if (future.isCancelled()) {
                    log(logLevel.INFO, m.stopUpdate);
                    if (lastSender != null && lastSender instanceof Player) {
                        lastSender.sendMessage("[AUP] " + m.stopUpdate);
                    }
                    return;
                }

                // Start processing one update entry
                _fail++;
                _updateFul++;

                Map<?, ?> li = (Map<?, ?>) _li;
                if (li == null) {
                    log(logLevel.WARN, m.configErrUpdate);
                    continue;
                }

                // Validate basic config for this entry
                c_file = (String) SEL(li.get("file"), "");
                c_url = ((String) SEL(li.get("url"), "")).trim();
                if (c_file.isEmpty() || c_url.isEmpty()) {
                    log(logLevel.WARN, m.configErrMissing);
                    continue;
                }

                // Get base file name for logging
                Matcher tempMatcher = Pattern.compile("([^/\\\\]+)\\..*$").matcher(c_file);
                if (tempMatcher.find()) {
                    _fileName = "[" + tempMatcher.group(1) + "] ";
                } else {
                    _fileName = "[" + c_file + "] ";
                }

                // If "file" contains a path, derive path from it
                tempMatcher = Pattern.compile("(.*/|.*\\\\)([^/\\\\]+)$").matcher(c_file);
                if (tempMatcher.find()) { // Also works for Windows backslash paths
                    getPath(tempMatcher.group(1));
                    c_updatePath = c_file;
                    c_filePath = c_file;
                    c_tempPath = getPath(getConfig().getString("tempPath", "./plugins/AutoUpdatePlugins/temp/")) + tempMatcher.group(2);
                }
                // If "path" is given, it overrides updatePath and filePath
                else if (li.get("path") != null) {
                    c_updatePath = getPath((String) li.get("path")) + c_file;
                    c_filePath = c_updatePath;
                    c_tempPath = getPath(getConfig().getString("tempPath", "./plugins/AutoUpdatePlugins/temp/")) + c_file;
                }
                // Use global config defaults
                else {
                    c_updatePath = getPath((String) SEL(li.get("updatePath"), getConfig().getString("updatePath", "./plugins/update/"))) + c_file;
                    c_filePath = getPath((String) SEL(li.get("filePath"), getConfig().getString("filePath", "./plugins/"))) + c_file;
                    c_tempPath = getPath(getConfig().getString("tempPath", "./plugins/AutoUpdatePlugins/temp/")) + c_file;
                }

                c_get = (String) SEL(li.get("get"), "");
                c_zipFileCheck = (boolean) SEL(li.get("zipFileCheck"), getConfig().getBoolean("zipFileCheck", true));
                c_getPreRelease = (boolean) SEL(li.get("getPreRelease"), false);

                // "[xx] Checking for updates..."
                log(logLevel.DEBUG, m.updateChecking);

                // Resolve download URL to a direct file URL
                String dUrl = getFileUrl(c_url, c_get);
                if (dUrl == null) {
                    log(logLevel.WARN, _nowParser + m.updateErrParsingDUrl);
                    continue;
                }
                dUrl = checkURL(dUrl);

                // Enable previous-update record and checks
                String feature = "";
                String pPath = "";
                if (getConfig().getBoolean("enablePreviousUpdate", true)) {
                    // Get “feature” identifier from the HEAD request
                    feature = getFeature(dUrl);
                    // Use a hash of the config entry as key
                    pPath = "previous." + li.toString().hashCode();
                    if (temp.get(pPath) != null) {
                        // Compare with previous update info
                        if (temp.getString(pPath + ".dUrl", "").equals(dUrl) &&
                                temp.getString(pPath + ".feature", "").equals(feature)) {
                            log(logLevel.MARK, m.updateTempAlreadyLatest);
                            _fail--;
                            continue;
                        }
                    }
                }

                // Download file to temp folder
                if (!downloadFile(dUrl, c_tempPath)) {
                    log(logLevel.WARN, m.updateErrDownload);
                    delFile(c_tempPath);
                    continue;
                }

                // Record size
                float fileSize = new File(c_tempPath).length();
                _allFileSize += fileSize;

                // File integrity check (zip/jar)
                if (c_zipFileCheck && Pattern.compile(getConfig().getString("zipFileCheckList", "\\.(?:jar|zip)$")).matcher(c_file).find()) {
                    if (!isJARFileIntact(c_tempPath)) {
                        log(logLevel.WARN, m.updateZipFileCheck);
                        delFile(c_tempPath);
                        continue;
                    }
                }

                // At this point, file is considered valid
                if (getConfig().getBoolean("enablePreviousUpdate", true)) {
                    // Save latest info for this entry
                    temp.set(pPath + ".file", c_file);
                    temp.set(pPath + ".time", nowDate());
                    temp.set(pPath + ".dUrl", dUrl);
                    temp.set(pPath + ".feature", feature);
                    saveDate();
                }

                // TODO: place any "run system command after update" logic here

                // Hash comparison: if the new file is identical to existing, skip updating
                if (getConfig().getBoolean("ignoreDuplicates", true) && (boolean) SEL(li.get("ignoreDuplicates"), true)) {
                    String updatePathFileHas = fileHash(c_updatePath);
                    String tempFileHas = fileHash(c_tempPath);
                    if (Objects.equals(tempFileHas, updatePathFileHas) || Objects.equals(tempFileHas, fileHash(c_filePath))) {
                        log(logLevel.MARK, m.updateFileAlreadyLatest);
                        _fail--;
                        delFile(c_tempPath);
                        continue;
                    }
                }

                // Get old file size: prefer update directory, then installed path
                float oldFileSize = new File(c_updatePath).exists()
                        ? new File(c_updatePath).length()
                        : new File(c_filePath).length();

                // Move downloaded file to update directory
                try {
                    Files.move(Path.of(c_tempPath), Path.of(c_updatePath), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    log(logLevel.WARN, e.getMessage());
                }

                // Log size change
                log(logLevel.DEBUG, m.piece(
                        m.updateFulSizeDifference,
                        String.format("%.2f", oldFileSize / 1048576),
                        String.format("%.2f", fileSize / 1048576)));

                _success++;
                _fail--;

                // Reset for safety
                _fileName = "[???] ";
                _nowParser = "[???] ";
            }
        }

        // Try to open JAR to check if file is intact
        public boolean isJARFileIntact(String filePath) {
            try {
                JarFile jarFile = new JarFile(new File(filePath));
                jarFile.close();
                return true;
            } catch (ZipException e) { // Corrupted / incomplete file
                return false;
            } catch (Exception e) { // Any other error
                return false;
            }
        }

        // Compute MD5 hash of a file
        public String fileHash(String filePath) {
            try {
                byte[] data = Files.readAllBytes(Paths.get(filePath));
                byte[] hash = MessageDigest.getInstance("MD5").digest(data);
                return new BigInteger(1, hash).toString(16);
            } catch (Exception e) {
                // File missing or error -> "null"
            }
            return "null";
        }

        // Resolve page URL into direct file download URL
        public String getFileUrl(String _url, String matchFileName) {
            // Remove trailing slash
            String url = _url.replaceAll("/$", "");

            if (url.contains("://github.com/")) { // GitHub releases
                _nowParser = "[GitHub] ";
                // Get path like "/owner/repo"
                Matcher matcher = Pattern.compile("/([^/]+)/([^/]+)$").matcher(url);
                if (matcher.find()) {
                    String data;
                    Map<?, ?> map;
                    // Allow pre-release versions if enabled
                    if (c_getPreRelease) {
                        // Fetch all releases and take first
                        data = httpGet("https://api.github.com/repos" + matcher.group(0) + "/releases");
                        if (data == null) {
                            return null;
                        }
                        map = (Map<?, ?>) new Gson().fromJson(data, ArrayList.class).get(0);
                    } else {
                        // Fetch latest release
                        data = httpGet("https://api.github.com/repos" + matcher.group(0) + "/releases/latest");
                        if (data == null) {
                            return null;
                        }
                        map = new Gson().fromJson(data, HashMap.class);
                    }
                    // Iterate release assets
                    ArrayList<?> assets = (ArrayList<?>) map.get("assets");
                    for (Object _li : assets) {
                        Map<?, ?> li = (Map<?, ?>) _li;
                        String fileName = (String) li.get("name");
                        if (matchFileName.isEmpty() || Pattern.compile(matchFileName).matcher(fileName).matches()) {
                            String dUrl = (String) li.get("browser_download_url");
                            log(logLevel.DEBUG, _nowParser + m.piece(m.debugGetVersion, dUrl));
                            return dUrl;
                        }
                    }
                    log(logLevel.WARN, "[GitHub] " + m.piece(m.debugNoFileMatching, url));
                    return null;
                }
                log(logLevel.WARN, "[GitHub] " + m.piece(m.debugNoRepositoryPath, url));
                return null;
            } else if (url.contains("://ci.")) { // Jenkins
                _nowParser = "[Jenkins] ";
                String data = httpGet(url + "/lastSuccessfulBuild/api/json");
                if (data == null) {
                    return null;
                }
                Map<?, ?> map = new Gson().fromJson(data, HashMap.class);
                ArrayList<?> artifacts = (ArrayList<?>) map.get("artifacts");
                // Iterate artifact list
                for (Object _li : artifacts) {
                    Map<?, ?> li = (Map<?, ?>) _li;
                    String fileName = (String) li.get("fileName");
                    if (matchFileName.isEmpty() || Pattern.compile(matchFileName).matcher(fileName).matches()) {
                        String dUrl = url + "/lastSuccessfulBuild/artifact/" + li.get("relativePath");
                        log(logLevel.DEBUG, _nowParser + m.piece(m.debugGetVersion, dUrl));
                        return dUrl;
                    }
                }
                log(logLevel.WARN, "[Jenkins] " + m.piece(m.debugNoFileMatching, url));
                return null;
            } else if (url.contains("://www.spigotmc.org/")) { // Spigot page
                _nowParser = "[Spigot] ";
                // Extract resource ID
                Matcher matcher = Pattern.compile("([0-9]+)$").matcher(url);
                if (matcher.find()) {
                    String dUrl = "https://api.spiget.org/v2/resources/" + matcher.group(1) + "/download";
                    log(logLevel.DEBUG, _nowParser + m.piece(m.debugGetVersion, dUrl));
                    return dUrl;
                }
                log(logLevel.WARN, "[Spigot] " + m.piece(m.debugErrUrlResolveNoID, url));
                return null;
            } else if (url.contains("://modrinth.com/")) { // Modrinth page
                _nowParser = "[Modrinth] ";
                Matcher matcher = Pattern.compile("/([^/]+)$").matcher(url);
                if (matcher.find()) {
                    String data = httpGet("https://api.modrinth.com/v2/project" + matcher.group(0) + "/version");
                    if (data == null) {
                        return null;
                    }
                    ArrayList<?> versions = new Gson().fromJson(data, ArrayList.class);
                    // Iterate versions
                    for (Object _version : versions) {
                        Map<?, ?> version = (Map<?, ?>) _version;
                        ArrayList<?> files = (ArrayList<?>) version.get("files");
                        // Iterate files for version
                        for (Object _file : files) {
                            Map<?, ?> file = (Map<?, ?>) _file;
                            String fileName = (String) file.get("filename");
                            if (matchFileName.isEmpty() || Pattern.compile(matchFileName).matcher(fileName).matches()) {
                                String dUrl = (String) file.get("url");
                                log(logLevel.DEBUG, _nowParser + m.piece(m.debugGetVersion, dUrl));
                                return dUrl;
                            }
                        }
                    }
                    log(logLevel.WARN, "[Modrinth] " + m.piece(m.debugNoFileMatching, url));
                    return null;
                }
                log(logLevel.WARN, "[Modrinth] " + m.piece(m.debugErrUrlResolveNoName, url));
                return null;
            } else if (url.contains("://dev.bukkit.org/")) { // Bukkit page
                _nowParser = "[Bukkit] ";
                String dUrl = url + "/files/latest";
                log(logLevel.DEBUG, _nowParser + m.piece(m.debugGetVersion, dUrl));
                return dUrl;
            } else if (url.contains("://builds.guizhanss.com/")) { // Guizhan build server
                _nowParser = "[GuizhanBuilds] ";
                // Example: https://builds.guizhanss.com/SlimefunGuguProject/AlchimiaVitae/master
                Matcher matcher = Pattern.compile("/([^/]+)/([^/]+)/([^/]+)$").matcher(url);
                if (matcher.find()) {
                    // Direct download API
                    String dUrl = "https://builds.guizhanss.com/api/download" + matcher.group(0) + "/latest";
                    log(logLevel.DEBUG, _nowParser + m.piece(m.debugGetVersion, dUrl));
                    return dUrl;
                }
                log(logLevel.WARN, _nowParser + m.piece(m.debugNoRepositoryPath, url));
                return null;
            } else if (url.contains("://www.minebbs.com/")) {   // MineBBS
                // Example: https://www.minebbs.com/resources/coreprotect-coi.7320/download
                _nowParser = "[MineBBS] ";
                return url + "/download";
            } else if (url.contains("://legacy.curseforge.com/")) { // CurseForge page
                _nowParser = "[CurseForge] ";
                String html = httpGet(url); // Fetch HTML and extract project ID
                if (html == null) {
                    return null;
                }
                String[] lines = html.split("<a"); // Split by anchor tags
                for (String li : lines) {
                    Matcher matcher = Pattern.compile("data-project-id=\"([0-9]+)\"").matcher(li);
                    if (matcher.find()) {
                        String data = httpGet("https://api.curseforge.com/servermods/files?projectIds=" + matcher.group(1));
                        if (data == null) {
                            return null;
                        }
                        ArrayList<?> arr = (ArrayList<?>) new Gson().fromJson(data, ArrayList.class);
                        Map<?, ?> map = (Map<?, ?>) arr.get(arr.size() - 1); // last entry
                        String dUrl = (String) map.get("downloadUrl");
                        log(logLevel.DEBUG, _nowParser + m.piece(m.debugGetVersion, dUrl));
                        return dUrl;
                    }
                }
                log(logLevel.WARN, _nowParser + m.piece(m.debugErrNoID, url));
                return null;
            } else { // No special handler, treat as direct URL
                _nowParser = "[URL] ";
                log(logLevel.DEBUG, _nowParser + _url);
                return _url;
            }
        }

        // Helper: if in1 is null, return in2, else return in1
        public Object SEL(Object in1, Object in2) {
            if (in1 == null) {
                return in2;
            }
            return in1;
        }

        // Build OkHttp Call object for HTTP requests
        public okhttp3.Call fetch(String url, boolean head) {
            _allRequests++;
            // HTTP client
            OkHttpClient.Builder client = new OkHttpClient.Builder();

            // Proxy support
            if (!getConfig().getString("proxy.type", "DIRECT").equals("DIRECT")) {
                client.proxy(new Proxy(
                        Proxy.Type.valueOf(getConfig().getString("proxy.type")),
                        new InetSocketAddress(
                                getConfig().getString("proxy.host", "127.0.0.1"),
                                getConfig().getInt("proxy.port", 7890))));
            }

            // Disable SSL verification (per-request) if sslVerify = false
            if (!getConfig().getBoolean("sslVerify", true)) {
                try {
                    X509TrustManager trustManager = new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    };

                    SSLContext sslContext = SSLContext.getInstance("SSL");
                    sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
                    client.sslSocketFactory(sslContext.getSocketFactory(), trustManager);

                } catch (Exception e) {
                    log(logLevel.NET_WARN, "[HTTP] [sslVerify: false]" + e.getMessage());
                }
            }

            // Build request
            Request.Builder request = new Request.Builder()
                    .url(url);
            if (head) {
                request.head();
            }

            // Add custom headers from config
            List<?> list = (List<?>) getConfig().get("setRequestProperty");
            if (list != null) {
                for (Object _li : list) {
                    Map<?, ?> li = (Map<?, ?>) _li;
                    request.header((String) li.get("name"), (String) li.get("value"));
                }
            }

            return client.build().newCall(request.build());
        }

        // Simple HTTP GET -> String
        public String httpGet(String url) {
            try (Response response = fetch(url, false).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }
                return response.body().string();
            } catch (IOException e) {
                log(logLevel.NET_WARN, "[HTTP] " + e.getMessage());
            }
            return null;
        }

        // Download a file to a path
        public boolean downloadFile(String url, String path) {
            try (Response response = fetch(url, false).execute()) {
                if (!response.isSuccessful()) {
                    return false;
                }
                try (InputStream inputStream = response.body().byteStream();
                     OutputStream outputStream = new FileOutputStream(path)) {

                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                return true;
            } catch (IOException e) {
                log(logLevel.NET_WARN, "[HTTP] " + e.getMessage());
            }
            return false;
        }

        // Use HEAD request to generate a “feature” identifier (size or redirect)
        public String getFeature(String url) {
            try (Response response = fetch(url, true).execute()) {
                if (!response.isSuccessful()) {
                    return "??_" + nowDate().hashCode();
                }
                String contentLength = SEL(response.headers().get("Content-Length"), -1).toString();
                if (!contentLength.equals("-1")) {
                    return "CL_" + contentLength;
                }
                String location = SEL(response.headers().get("Location"), "Invalid").toString();
                if (!location.equals("Invalid")) {
                    return "LH_" + location.hashCode();
                }
            } catch (IOException e) {
                log(logLevel.NET_WARN, "[HTTP.HEAD] " + e.getMessage());
            }
            return "??_" + nowDate().hashCode();
        }

        // Log with levels and also store log lines for /aup log
        public void log(logLevel level, String text) {

            // Read enabled log levels from config
            List<String> userLogLevel = getConfig().getStringList("logLevel");
            if (userLogLevel.isEmpty()) {
                userLogLevel = List.of("DEBUG", "MARK", "INFO", "WARN", "NET_WARN");
            }

            if (userLogLevel.contains(level.name)) {
                switch (level.name) {
                    case "DEBUG":
                        getLogger().info(_fileName + text);
                        break;
                    case "INFO":
                        getLogger().info(text);
                        break;
                    case "MARK":
                        // Some consoles have issues with color; this at least keeps formatting obvious
                        Bukkit.getConsoleSender().sendMessage(level.color + "[AUP] " + _fileName + text);
                        break;
                    case "WARN":
                    case "NET_WARN":
                        getLogger().warning(_fileName + text);
                        break;
                }
            }

            // Add colored log line to list (non-INFO includes filename prefix)
            logList.add(level.color + (level.name.equals("INFO") ? "" : _fileName) + text);
        }

        enum logLevel {
            // Can be ignored in config if desired
            DEBUG("", "DEBUG"),
            // Regular informational log
            INFO("", "INFO"),
            // Highlight completion of tasks
            MARK("§a", "MARK"),
            // Warning
            WARN("§e", "WARN"),
            // HTTP/network warnings
            NET_WARN("§e", "NET_WARN"),
            ;
            private final String color;
            private final String name;

            logLevel(String color, String name) {
                this.color = color;
                this.name = name;
            }
        }

        // Get current time as formatted string
        public String nowDate() {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return now.format(formatter);
        }

        // Clean URL and escape spaces
        public String checkURL(String url) {
            try {
                return new URI(url.trim()
                        .replace(" ", "%20"))
                        .toASCIIString();
            } catch (URISyntaxException e) {
                log(logLevel.WARN, "[URI] " + m.piece(m.urlInvalid, url));
                return null;
            }
        }

        // Delete a file
        public void delFile(String path) {
            new File(path).delete();
        }
    }

    // Create directory if it does not exist and return normalized path with trailing slash
    public String getPath(String path) {
        Path directory = Paths.get(path);
        try {
            Files.createDirectories(directory);
            return directory + "/";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Message container (loaded from config)
    public static class m {
        public static String updateCheckIntervalTooLow;
        public static String timer;
        public static String commandReloadOnUpdating;
        public static String commandReloadOK;
        public static String commandRepeatedRunUpdate;
        public static String commandUpdateStart;
        public static String commandFullLog;
        public static String commandStopUpdateIng;
        public static String stopUpdate;
        public static String repeatedRunUpdate;
        public static String updateStart;
               public static String configErrList;
        public static String configErrUpdate;
        public static String configErrMissing;
        public static String updateChecking;
        public static String updateErrParsingDUrl;
        public static String updateTempAlreadyLatest;
        public static String updateErrDownload;
        public static String updateZipFileCheck;
        public static String updateFileAlreadyLatest;
        public static String updateFulSizeDifference;
        public static String updateFul;
        public static String updateFulTime;
        public static String updateFulFail;
        public static String updateFulUpdate;
        public static String updateFulOK;
        public static String updateFulNetRequest;
        public static String updateFulDownloadFile;
        public static String logReloadOK;
        public static String debugGetVersion;
        public static String debugNoFileMatching;
        public static String debugNoRepositoryPath;
        public static String debugErrUrlResolveNoID;
        public static String debugErrUrlResolveNoName;
        public static String debugErrNoID;
        public static String urlInvalid;

        // Template helpers
        public static String piece(String message, Object in1) {
            return message.replace("%1", "" + in1);
        }

        public static String piece(String message, Object in1, Object in2) {
            return piece(message, in1).replace("%2", "" + in2);
        }
    }

    public String gm(String key, String _default) {
        return getConfig().getString("message." + key, _default);
    }

    public void loadMessage() {
        m.updateCheckIntervalTooLow = gm("updateCheckIntervalTooLow", "### Update check interval is too low and may cause performance issues! ###");
        m.timer = gm("timer", "Update check will run in %1 seconds and repeat every %2 seconds.");
        m.commandReloadOnUpdating = gm("commandReloadOnUpdating", "An update is currently running, config reload will be delayed.");
        m.commandReloadOK = gm("commandReloadOK", "Config reload completed.");
        m.commandRepeatedRunUpdate = gm("commandRepeatedRunUpdate", "An unfinished update is already running.");
        m.commandUpdateStart = gm("commandUpdateStart", "Update started!");
        m.commandFullLog = gm("commandFullLog", "Full log:");
        m.commandStopUpdateIng = gm("commandStopUpdateIng", "Stopping current update...");
        m.stopUpdate = gm("stopUpdate", "Current update has been stopped.");
        m.repeatedRunUpdate = gm("repeatedRunUpdate", "### Update task started twice, or encountered an unexpected error? ###");
        m.updateStart = gm("updateStart", "[## Starting automatic update ##]");
        m.configErrList = gm("configErrList", "Update list configuration error?");
        m.configErrUpdate = gm("configErrUpdate", "Update list configuration error? Entry is empty.");
        m.configErrMissing = gm("configErrMissing", "Update list configuration error? Missing required fields.");
        m.updateChecking = gm("updateChecking", "Checking for updates...");
        m.updateErrParsingDUrl = gm("updateErrParsingDUrl", "Error resolving direct download link, skipping this update.");
        m.updateTempAlreadyLatest = gm("updateTempAlreadyLatest", "[Cache] File is already up to date.");
        m.updateErrDownload = gm("updateErrDownload", "Exception while downloading file, skipping this update.");
        m.updateZipFileCheck = gm("updateZipFileCheck", "[Zip integrity check] File is incomplete, skipping this update.");
        m.updateFileAlreadyLatest = gm("updateFileAlreadyLatest", "File is already up to date.");
        m.updateFulSizeDifference = gm("updateFulSizeDifference", "Update complete [%1MB] -> [%2MB].");
        m.updateFul = gm("updateFul", "[## All updates finished ##]");
        m.updateFulTime = gm("updateFulTime", "Time taken: %1 seconds.");
        m.updateFulFail = gm("updateFulFail", "Failed: %1, ");
        m.updateFulUpdate = gm("updateFulUpdate", "Updated: %1, ");
        m.updateFulOK = gm("updateFulOK", "Succeeded: %1");
        m.updateFulNetRequest = gm("updateFulNetRequest", "Network requests: %1, ");
        m.updateFulDownloadFile = gm("updateFulDownloadFile", "Downloaded files: %1MB");
        m.logReloadOK = gm("logReloadOK", "Reload completed.");
        m.debugGetVersion = gm("debugGetVersion", "Found version: %1");
        m.debugNoFileMatching = gm("debugNoFileMatching", "No matching file: %1");
        m.debugNoRepositoryPath = gm("debugNoRepositoryPath", "Repository path not found: %1");
        m.debugErrUrlResolveNoID = gm("debugErrUrlResolveNoID", "URL parse error, missing plugin ID? %1");
        m.debugErrUrlResolveNoName = gm("debugErrUrlResolveNoName", "URL parse error, project name not found: %1");
        m.debugErrNoID = gm("debugErrNoID", "Project ID not found: %1");
        m.urlInvalid = gm("urlInvalid", "URL is invalid or malformed: %1");
    }
}

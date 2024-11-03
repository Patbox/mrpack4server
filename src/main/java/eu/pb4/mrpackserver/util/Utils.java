package eu.pb4.mrpackserver.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import eu.pb4.forgefx.ForgeInstallerFix;
import eu.pb4.forgefx.S;
import eu.pb4.mrpackserver.format.*;
import eu.pb4.mrpackserver.installer.FileDownloader;
import eu.pb4.mrpackserver.installer.ModrinthModpackLookup;
import eu.pb4.mrpackserver.installer.MrPackInstaller;
import eu.pb4.mrpackserver.launch.Launcher;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public interface Utils {
    Gson GSON_MAIN = new GsonBuilder().disableHtmlEscaping().registerTypeHierarchyAdapter(HashData.class, new HashData.Serializer()).create();
    Gson GSON_PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().registerTypeHierarchyAdapter(HashData.class, new HashData.Serializer()).create();

    private static int indexOf(byte[] data, byte[] pattern) {
        int j = 0;

        for (int i = 0; i < data.length; i++) {
            if (pattern[j] != data[i]) {
                j = 0;
            }
            if (pattern[j] == data[i]) { j++; }
            if (j == pattern.length) {
                return i - pattern.length + 1;
            }
        }
        return -1;
    }
    @Nullable
    static ModpackInfo resolveModpackInfo(Path currentDir) throws IOException {
        var x = resolveModpackInfoInternal();
        if (x != null) {
            return x;
        }

        return resolveModpackInfoExternal(currentDir);
    }
    @Nullable
    static ModpackInfo resolveModpackInfoInternal() throws IOException {
        var jarFile = Utils.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        try {
            var bytes = Files.readAllBytes(Path.of(jarFile));
            var jarStart = indexOf(bytes, new byte[]{0x50, 0x4b, 0x03, 0x04});
            if (jarStart > 0) {
                var jsonPart = Arrays.copyOf(bytes, jarStart);
                var x = ModpackInfo.read(new String(jsonPart));

                if (x.isValid()) {
                    return x;
                }
            }
        } catch (IOException e) {
            // ignored
        }

        try (var data = Utils.class.getResourceAsStream("/modpack-info.json")) {
            var x = ModpackInfo.read(new String(Objects.requireNonNull(data).readAllBytes()));

            if (x.isValid()) {
                return x;
            }
        } catch (NullPointerException e) {
            // ignored
        }

        return null;
    }
    @Nullable
    static ModpackInfo resolveModpackInfoExternal(Path currentDir) throws IOException {
        var possibleLocal = currentDir.resolve("modpack-info.json");
        if (Files.exists(possibleLocal)) {
            var x = ModpackInfo.read(Files.readString(possibleLocal));

            if (x.isValid()) {
                return x;
            }
        }

        var possibleMrpack = currentDir.resolve("local.mrpack");
        if (Files.exists(possibleMrpack)) {
            try (var zip = FileSystems.newFileSystem(possibleMrpack)) {
                var index = ModpackIndex.read(Files.readString(zip.getPath("modrinth.index.json")));
                if (!index.versionId.isEmpty() && !index.name.isEmpty()) {
                    var info = new ModpackInfo();
                    info.projectId = "<local>";
                    info.displayName = index.name;
                    info.versionId = index.versionId;
                    info.url = possibleMrpack.toUri();
                    return info;
                }

            } catch (Throwable e) {
                // ignored
            }
        }
        return null;
    }

    static InstallResult checkAndSetupModpack(ModpackInfo modpackInfo, InstanceInfo instance, Path currentDir, Path instanceDataDir) throws Throwable {
        var mrpackFile = getMrPackFile(modpackInfo, instanceDataDir);
        if (mrpackFile == null) {
            return null;
        }

        try (var zip = FileSystems.newFileSystem(mrpackFile)) {
            var index = ModpackIndex.read(Files.readString(zip.getPath("modrinth.index.json")));

            { // Safety validation
                var check = currentDir.toAbsolutePath();
                for (var file : index.files) {
                    var filePath = check.resolve(file.path).normalize();
                    if (!filePath.startsWith(check)) {
                        Logger.error("Modpack contains files, that are placed outside of server's root! Found '%s'", file.path);
                        return null;
                    }
                }
            }
            var hashPath = instanceDataDir.resolve("hashes.json");
            var hashes = new HashMap<String, HashData>();
            if (Files.exists(hashPath)) {
                hashes = Utils.GSON_MAIN.fromJson(Files.readString(hashPath), new TypeToken<HashMap<String, HashData>>() {}.getType());
            }
            var whitelistedDomains = new HashSet<String>();
            whitelistedDomains.addAll(Constants.WHITELISTED_URLS);
            whitelistedDomains.addAll(modpackInfo.whitelistedDomains);

            Logger.info("Starting %s of %s (%s)", hashes.isEmpty() ? "installation" : "update", modpackInfo.getDisplayName(), modpackInfo.getDisplayVersion());

            var handler = new MrPackInstaller(zip.getPath(""), index, currentDir, instance, hashes, whitelistedDomains);

            if (!handler.checkJavaVersion()) {
                return null;
            }

            handler.prepareFolders();
            var localExistingHashes = handler.getLocalFileUpdatedHashes();
            var x = new FileDownloader();
            handler.requestDownloads(x, localExistingHashes);
            if (!x.isEmpty()) {
                Logger.info("Downloading remote modpack files...");
                var failed = x.downloadFiles(handler.getHashes());
                if (!failed.isEmpty()) {
                    Logger.error("Failed to download provided files: \n- " + String.join("\n- ", failed));
                    return null;
                }
                Logger.info("Finished downloading remote modpack files!");
            }
            handler.extractIncluded(localExistingHashes);

            handler.cleanupLeftoverFiles(localExistingHashes);

            Files.deleteIfExists(hashPath);
            Files.writeString(hashPath, Utils.GSON_MAIN.toJson(handler.getHashes()));

            var newInstance = new InstanceInfo();
            newInstance.projectId = modpackInfo.projectId;
            newInstance.versionId = modpackInfo.versionId;
            newInstance.forceSystemClasspath = handler.getNewLauncher() != null ? handler.forceSystemClasspath() : instance.forceSystemClasspath;
            newInstance.runnablePath = Objects.requireNonNullElse(handler.getNewLauncher(), instance.runnablePath);
            newInstance.dependencies.putAll(index.dependencies);

            if (handler.getInstaller() != null) {
                var installer = handler.getInstaller();
                return new InstallResult(newInstance, createInstallerRunner(currentDir.resolve(installer.path()), installer.args()));
            }

            return new InstallResult(newInstance, () -> {});
        }
    }

    static Runnable createInstallerRunner(Path path, String[] args) {
        return () -> {
            var t = new Thread(() -> {
                for (var i = 0; i < 10; i ++) {
                    System.out.println();
                }
                Logger.info("Installer finished, but forces the mrpack4server to exit! Start the server again to run it!");
                Logger.info("You should ignore instructions about deleting installer files, as it is not needed!");

            });
            t.setDaemon(true);
            Runtime.getRuntime().addShutdownHook(t);
            var oldOut = System.out;
            var oldErr = System.err;
            System.setOut(new PrintStream(oldOut) {
                private static final byte[] M1 = ("You can delete this installer file now if you wish" + System.lineSeparator()).getBytes();
                private static final byte[] M2 = ("A problem installing was detected, install cannot continue" + System.lineSeparator()).getBytes();
                @Override
                public void write(byte[] buf) throws IOException {
                    if (!Arrays.equals(buf, M1) && !Arrays.equals(buf, M2)) {
                        super.write(buf);
                    }
                }
            });
            if (!Launcher.launchExec(path, ForgeInstallerFix::new, args)) {
                Logger.warn("Failed to execute the installer! See errors above.");
            }
            System.setOut(oldOut);
            System.setErr(oldErr);
            Runtime.getRuntime().removeShutdownHook(t);
        };
    }

    @Nullable
    static Path getMrPackFile(ModpackInfo modpackInfo, Path instanceDataDir) {
        if (modpackInfo.url != null && modpackInfo.url.getScheme().equals("file")) {
            return Path.of(modpackInfo.url);
        }

        var name = "modpack/" + modpackInfo.projectId + "_" + modpackInfo.versionId + ".mrpack";

        var modpackFile = instanceDataDir.resolve(name);
        if (Files.exists(modpackFile)) {
            return modpackFile;
        }
        URI uri;
        long size;
        String hash;
        if (modpackInfo.url != null) {
            uri = modpackInfo.url;
            size = modpackInfo.size != null ? modpackInfo.size : -1;
            hash = modpackInfo.sha512;
        } else {
            var result = ModrinthModpackLookup.findVersion(modpackInfo.getVersionListUrl(), modpackInfo.projectId, modpackInfo.getDisplayName(), modpackInfo.getDisplayVersion(), true,
                    x -> x.versionNumber.equals(modpackInfo.versionId) || x.id.equals(modpackInfo.versionId));
            if (result == null) {
                return null;
            }
            uri = result.uri();
            size = result.size();
            hash = result.hashes().get(Constants.MODRINTH_HASH);
        }

        try {
            Files.createDirectories(modpackFile.getParent());
            var req = createHttpClient().send(createGetRequest(uri), HttpResponse.BodyHandlers.ofInputStream());
            var x = handleDownloadedFile(modpackFile, req.body(), name, size, hash != null ? HashData.read(Constants.DEFAULT_HASH, hash) : null);
            if (x != null) {
                return modpackFile;
            }
        } catch (Throwable e) {
            Logger.error("Failed to locate source mrpack file!", e);
        }

        return null;
    }



    static @Nullable HashData handleDownloadedFile(Path out, InputStream body, String displayName, long fileSize, @Nullable HashData hashData) {
        var tmpOut = out.getParent().resolve(out.getFileName().toString() + ".mrpack4server.tmp");
        try {
            Files.deleteIfExists(tmpOut);
        } catch (Throwable e) {
            Logger.error("Failed to remove leftover temporary file '%s'!", tmpOut, e);
        }
        boolean success = false;
        Logger.info("Downloading file '%s': 0%%", displayName);
        try (var file = Files.newOutputStream(tmpOut)) {
            var hashType = hashData != null ? hashData.type() : Constants.DEFAULT_HASH;
            var stream = new DigestInputStream(body, MessageDigest.getInstance(hashType));
            stream.on(true);
            long current = 0;
            long lastSent = System.currentTimeMillis();

            while (true) {
                var dat = stream.readNBytes(Math.max(stream.available(), Constants.DOWNLOAD_CHUNK_SIZE));
                if (dat == null || dat.length == 0) {
                    break;
                }
                current += dat.length;
                file.write(dat);

                if ((System.currentTimeMillis() - lastSent) > Constants.DOWNLOAD_UPDATE_TIME) {
                    lastSent = System.currentTimeMillis();
                    Logger.info("Downloading file '%s': %s%%", displayName, fileSize != -1 ? String.valueOf(current * 100 / fileSize) : '?');
                }
            }
            file.flush();
            var hash = stream.getMessageDigest().digest();
            if (hashData == null || Arrays.equals(hashData.hash(), hash)) {
                success = true;
                Logger.info("Finished downloading file '%s' successfully!", displayName);
                return new HashData(hashType, hash);
            } else {
                Logger.warn("Couldn't validate file '%s'! Expected hash: %s, got: %s", displayName, hashData.hashString(), HexFormat.of().formatHex(hash));
                return null;
            }
        } catch (Throwable e) {
            Logger.error("Couldn't download file '%s' correctly!", displayName, e);
            return null;
        } finally {
            try {
                if (success) {
                    Files.move(tmpOut, out);
                } else {
                    Files.deleteIfExists(tmpOut);
                }
            } catch (Throwable e) {
                if (success) {
                    Logger.error("Failed to move temporary file to correct location '%s'!", out, e);
                } else {
                    Logger.error("Failed to remove temporary file '%s'!", tmpOut, e);
                }
            }
        }
    }

    static HttpClient createHttpClient() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    }

    static HttpRequest createGetRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .setHeader("User-Agent", Constants.USER_AGENT)
                .GET()
                .build();
    }

    static void configureModpack(Path runPath) throws IOException, InterruptedException {
        var scanner = new Scanner(System.in);
        while (true) {
            var newInfo = new ModpackInfo();
            Logger.info("Provide modpack name, it's id or url linking to it");
            Logger.label(">");
            var data = scanner.nextLine();
            boolean requestVersion = false;

            if (data.startsWith("http://") || data.startsWith("https://")) {
                try {
                    var uri = URI.create(data);
                    var parts = uri.getPath().split("/");

                    var requestName = false;
                    if (uri.getPath().endsWith(".mrpack")) {
                        newInfo.projectId = parts[parts.length - 1].substring(".mrpack".length());
                        newInfo.versionId = data;
                        newInfo.url = uri;
                    } else if (parts.length == 3 || parts.length == 4) {
                        newInfo.projectId = parts[2];
                        requestVersion = true;
                        requestName = true;
                    } else if (parts.length >= 5) {
                        newInfo.projectId = parts[2];
                        newInfo.versionId = parts[4];
                        requestName = true;
                    } else {
                        Logger.error("Invalid url! %s");
                        continue;
                    }

                    if (requestName) {
                        var client = Utils.createHttpClient();
                        var res = client.send(Utils.createGetRequest(URI.create(Constants.MODRINTH_API + "/project/" + newInfo.projectId)), HttpResponse.BodyHandlers.ofString());
                        if (res.statusCode() == 200) {
                            var project = ModrinthProjectData.read(res.body());
                            newInfo.displayName = project.title;
                        }
                    }
                } catch (Throwable e) {
                    Logger.error("Invalid url! %s");
                    continue;
                }
            } else {
                var client = Utils.createHttpClient();
                var res = client.send(Utils.createGetRequest(URI.create(Constants.MODRINTH_API + "/project/" + URLEncoder.encode(data, StandardCharsets.UTF_8))), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    var project = ModrinthProjectData.read(res.body());
                    newInfo.projectId = project.slug;
                    newInfo.displayName = project.title;
                    requestVersion = true;
                } else {
                    res = client.send(Utils.createGetRequest(URI.create(Constants.MODRINTH_API + "/search?query="
                            + URLEncoder.encode(data, StandardCharsets.UTF_8) + "&facets=[[%22project_type:modpack%22]]")), HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() != 200) {
                        Logger.error("Failed to request Modrinth search!");
                        return;
                    }
                    var search = ModrinthSearchData.read(res.body());

                    if (search.hits.isEmpty()) {
                        Logger.warn("Couldn't find any modpacks matching your description!");
                        continue;
                    }
                    Logger.info("Found Modpacks:");

                    for (int i = 0; i < Math.min(search.hits.size(), 9); i++) {
                        var modpack = search.hits.get(i);
                        Logger.info("%s > %s (%s) by %s", i + 1, modpack.title, modpack.slug, modpack.author);
                    }
                    while (true) {
                        Logger.info("Select Modpack by number [1]:");
                        Logger.label(">");
                        data = scanner.nextLine();
                        int id;
                        if (data.isEmpty()) {
                            id = 1;
                        } else {
                            try {
                                id = Integer.parseInt(data);
                            } catch (Throwable e) {
                                id = Integer.MAX_VALUE;
                            }

                            if (id > search.hits.size()) {
                                Logger.error("Invalid id, try again!");
                                continue;
                            }
                        }
                        var pack = search.hits.get(id - 1);
                        newInfo.projectId = pack.slug;
                        newInfo.displayName = pack.title;
                        requestVersion = true;
                        break;
                    }
                }
            }
            Logger.info("Selected Modpack: %s (%s)", newInfo.getDisplayName(), newInfo.projectId);

            if (requestVersion) {
                var versions = ModrinthModpackLookup.getVersions(newInfo.getVersionListUrl(), newInfo.projectId, newInfo.getDisplayName());
                if (versions == null || versions.isEmpty()) {
                    Logger.error("No versions found for %s (%s)", newInfo.getDisplayName(), newInfo.projectId);
                    continue;
                }

                while (true) {
                    Logger.info("Select Version [%s]", versions.get(0).versionNumber);
                    Logger.label(">");
                    data = scanner.nextLine();
                    if (data.isEmpty()) {
                        data = versions.get(0).versionNumber;
                        break;
                    }

                    if (data.equals(";;release") || data.equals(";;beta") || data.equals(";;alpha")) {
                        break;
                    }

                    ModrinthModpackVersion ver = null;
                    for (var version : versions) {
                        if (version.versionNumber.equals(data) || version.name.equals(data) || version.id.equals(data)) {
                            ver = version;
                            break;
                        }
                    }

                    if (ver != null) {
                        data = ver.versionNumber;
                        break;
                    }
                    Logger.error("%s is not a valid version of this modpack!: ", data);
                }

                newInfo.versionId = data;
            }

            if (!newInfo.versionId.isEmpty()) {
                Logger.info("Selected Version: %s (%s)", newInfo.getDisplayVersion(), newInfo.versionId);
            }


            Files.writeString(runPath.resolve("modpack-info.json"), newInfo.toJson());
            break;
        }
    }

    record InstallResult(InstanceInfo info, Runnable installer) {}
}

package eu.pb4.mrpackserver.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import eu.pb4.mrpackserver.format.InstanceInfo;
import eu.pb4.mrpackserver.format.ModpackIndex;
import eu.pb4.mrpackserver.format.ModpackInfo;
import eu.pb4.mrpackserver.installer.FileDownloader;
import eu.pb4.mrpackserver.installer.ModrinthModpackLookup;
import eu.pb4.mrpackserver.installer.MrPackInstaller;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public interface Utils {
    Gson GSON = new GsonBuilder().disableHtmlEscaping().setLenient().create();

    static boolean launchJar(Path path, String... args) {
        String mainClass;
        String launcherAgentClass = null;

        try (var jarFile = new JarFile(path.toFile())) {
            mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            launcherAgentClass = jarFile.getManifest().getMainAttributes().getValue("Launcher-Agent-Class");
            if (mainClass == null) {
                Logger.error("The '%s' is missing main class!!", path);
                return false;
            }
        } catch (Throwable e) {
            Logger.error("Failed to read data of '%s'!", e);
            return false;
        }

        try (var launchClassLoader = new URLClassLoader(new URL[]{ path.toUri().toURL() })) {
            if (launcherAgentClass != null) {
                if (InstrumentationCatcher.exists()) {
                    try {
                        var m = launchClassLoader.loadClass(launcherAgentClass).getDeclaredMethod("agentmain", String.class, Instrumentation.class);
                        m.setAccessible(true);
                        var handle = MethodHandles.lookup().unreflect(m);
                        handle.invoke("", InstrumentationCatcher.get());
                    } catch (Throwable e) {
                        Logger.error("Error occurred while invoking Launcher-Agent-Class!", e);
                    }
                } else {
                    Logger.error("The executed jar requires Instrumentation, but it's not set up! This might be related to your launch arguments / java version!");
                }
            }

            try {
                var handle = MethodHandles.publicLookup().findStatic(launchClassLoader.loadClass(mainClass), "main", MethodType.methodType(void.class, String[].class));
                handle.invoke((Object) args);
                return true;
            } catch (SecurityException e) {
                return e.getMessage() != null && e.getMessage().equals("Success");
            }
        } catch (Throwable e) {
            Logger.error("Exception occurred while executing '%s' with arguments '%s'!", path, String.join(" ", args), e);
            return false;
        }
    }

    @Nullable
    static ModpackInfo resolveModpackInfo(Path currentDir) throws IOException {
        try (var data = Utils.class.getResourceAsStream("/modpack-info.json")) {
            var x = GSON.fromJson(new String(Objects.requireNonNull(data).readAllBytes()), ModpackInfo.class);

            if (x.isValid()) {
                return x;
            }
        } catch (NullPointerException e) {
            // ignored
        }

        var possibleLocal = currentDir.resolve("modpack-info.json");
        if (Files.exists(possibleLocal)) {
            var x = GSON.fromJson(Files.readString(possibleLocal), ModpackInfo.class);

            if (x.isValid()) {
                return x;
            }
        }

        var possibleMrpack = currentDir.resolve("local.mrpack");
        if (Files.exists(possibleMrpack)) {
            try (var zip = FileSystems.newFileSystem(possibleMrpack)) {
                var index = Utils.GSON.fromJson(Files.readString(zip.getPath("modrinth.index.json")), ModpackIndex.class);
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
            var index = Utils.GSON.fromJson(Files.readString(zip.getPath("modrinth.index.json")), ModpackIndex.class);

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
            var hashes = new HashMap<String, String>();
            if (Files.exists(hashPath)) {
                hashes = Utils.GSON.fromJson(Files.readString(hashPath), new TypeToken<HashMap<String, String >>() {});
            }
            var whitelistedDomains = new HashSet<String>();
            whitelistedDomains.addAll(Constants.WHITELISTED_URLS);
            whitelistedDomains.addAll(modpackInfo.whitelistedDomains);

            Logger.info("Starting %s of %s (%s)", hashes.isEmpty() ? "installation" : "update", modpackInfo.getDisplayName(), modpackInfo.getDisplayVersion());

            var handler = new MrPackInstaller(zip.getPath(""), index, currentDir, instance, hashes, whitelistedDomains);
            handler.prepareFolders();
            handler.cleanupOutdatedFiles();
            var x = new FileDownloader();
            handler.requestDownloads(x);
            var failed = x.downloadFiles(handler.getHashes());
            if (!failed.isEmpty()) {
                Logger.error("Failed to download provided files: \n- ", String.join("\n- ", failed));
                return null;
            }
            handler.extractIncluded();

            Files.deleteIfExists(hashPath);
            Files.writeString(hashPath, Utils.GSON.toJson(handler.getHashes()));

            var newInstance = new InstanceInfo();
            newInstance.projectId = modpackInfo.projectId;
            newInstance.versionId = modpackInfo.versionId;
            newInstance.runnablePath = Objects.requireNonNullElse(handler.getNewLauncher(), instance.runnablePath);
            newInstance.dependencies.putAll(index.dependencies);

            if (handler.getForgeInstaller() != null) {
                return new InstallResult(newInstance, () -> {
                    Logger.info("Running installer! Older (Neo)Forge installers will quit after success, requiring you to start the server again!");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {

                    }
                    var t = new Thread(() -> {
                        for (var i = 0; i < 10; i ++) {
                            System.out.println();
                        }
                        Logger.info("Installer finished, but forces the mrpack4server to exit! Start the server again to run it!");
                        Logger.info("You should ignore instructions about deleting installer files, as it is not needed!");

                    });
                    Runtime.getRuntime().addShutdownHook(t);
                    if (!launchJar(currentDir.resolve(handler.getForgeInstaller()), "--installServer")) {
                        Logger.warn("Failed to execute the installer! See errors above.");
                    }
                    Runtime.getRuntime().removeShutdownHook(t);
                });
            }

            return new InstallResult(newInstance, () -> {});
        }
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
            var result = ModrinthModpackLookup.findVersion(modpackInfo.projectId, modpackInfo.getDisplayName(), modpackInfo.getDisplayVersion(), true,
                    x -> x.versionNumber.equals(modpackInfo.versionId) || x.id.equals(modpackInfo.versionId));
            if (result == null) {
                return null;
            }
            uri = result.uri();
            size = result.size();
            hash = result.hashes().get(Constants.HASH);
        }

        try {
            Files.createDirectories(modpackFile.getParent());
            var req = createHttpClient().send(createGetRequest(uri), HttpResponse.BodyHandlers.ofInputStream());
            var x = handleDownloadedFile(modpackFile, req.body(), name, size, hash);
            if (x != null) {
                return modpackFile;
            }
        } catch (Throwable e) {
            Logger.error("Failed to locate source mrpack file!", e);
        }

        return null;
    }



    static byte @Nullable [] handleDownloadedFile(Path out, InputStream body, String displayName, long fileSize, @Nullable String sha512) {
        try (var file = Files.newOutputStream(out)) {
            var stream = new DigestInputStream(body, MessageDigest.getInstance("SHA-512"));
            stream.on(true);
            Logger.info("Downloading file '%s': 0%%", displayName);
            long current = 0;
            var startingTime = System.currentTimeMillis();

            while (true) {
                var dat = stream.readNBytes(Math.max(stream.available(), 512));
                if (dat == null || dat.length == 0 ) {
                    break;
                }
                current += dat.length;
                file.write(dat);

                if ((System.currentTimeMillis() - startingTime) % 2000 == 1000) {
                    Logger.info("Downloading file '%s': %s%%", displayName, fileSize != -1 ? String.valueOf(current * 100 / fileSize) : '?');
                }
            }
            var hash = stream.getMessageDigest().digest();
            if (sha512 == null || Arrays.equals(HexFormat.of().parseHex(sha512), hash)) {
                Logger.info("Finished downloading file '%s' successfully!", displayName);
                return hash;
            } else {
                Logger.warn("Couldn't validate file '%s'! Expected hash: %s, got: %s", displayName, sha512, HexFormat.of().formatHex(hash));
                return null;
            }
        } catch (Throwable e) {
            Logger.error("Couldn't download file '%s' correctly!", displayName, e);
            return null;
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

    record InstallResult(InstanceInfo info, Runnable installer) {}
}

package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.util.*;
import eu.pb4.mrpackserver.format.InstanceInfo;
import eu.pb4.mrpackserver.format.ModpackIndex;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

public class MrPackInstaller {
    private final Path source;
    private final ModpackIndex index;
    private final Path destination;
    private final HashMap<String, HashData> oldHashes;
    private final HashMap<String, HashData> newHashes;
    private final Path destinationOldModified;
    private final InstanceInfo currentInstanceData;
    private final Set<String> whitelistedDomains;
    private final HashSet<String> nonOverwritablePaths;
    @Nullable
    private String newLauncher;
    @Nullable
    private Installer installer;
    private boolean forceSystemClasspath = false;

    public MrPackInstaller(Path source, ModpackIndex index, Path destination, InstanceInfo data, HashMap<String, HashData> hashes, Set<String> whitelistedDomains, HashSet<String> nonOverwritablePaths) throws IOException {
        this.source = source;
        this.index = index;
        this.currentInstanceData = data;
        this.destination = destination.toAbsolutePath();
        this.destinationOldModified = this.destination.resolve("old_modified_files");


        this.oldHashes = hashes;
        this.newHashes = new HashMap<>();

        this.whitelistedDomains = whitelistedDomains;
        this.nonOverwritablePaths = nonOverwritablePaths;
    }

    public void prepareFolders() throws IOException {
        Files.createDirectories(this.destination);
    }

    public void extractIncluded(Map<String, HashData> existingHashes) throws IOException {
        Logger.info("Extracting included files.");
        for (var base : Constants.OVERWRITES) {
            var path = this.source.resolve(base);
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        var local = path.relativize(dir).toString();
                        var p = destination.resolve(local).normalize();
                        if (!p.startsWith(destination.toAbsolutePath())) {
                            Logger.error("Modpack contains files, that are placed outside of server's root! Found '%s'", local);
                            return FileVisitResult.TERMINATE;
                        }

                        if (nonOverwritablePaths.contains(local) && Files.exists(p)) {
                            Logger.warn("Skipping non-overwritable path: %s", path);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        Files.createDirectories(p);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        var local = path.relativize(file).toString();
                        var outPath = destination.resolve(local);
                        if (!outPath.startsWith(destination.toAbsolutePath())) {
                            Logger.error("Modpack contains files, that are placed outside of server's root! Found '%s'", local);
                            return FileVisitResult.TERMINATE;
                        }

                        if (nonOverwritablePaths.contains(local) && Files.exists(file)) {
                            Logger.warn("Skipping non-overwritable file: %s", path);
                            return FileVisitResult.CONTINUE;
                        }

                        var oldHash = oldHashes.get(local);
                        var hashType = oldHash != null ? oldHash.type() : Constants.DEFAULT_HASH;
                        var ext = existingHashes.remove(local);
                        var existingHash = ext != null ? ext : getHash(hashType, outPath);
                        var newHash = getHash(hashType, file);

                        assert newHash != null;

                        if (existingHash != null) {
                            if ((oldHash != null && oldHash.equals(newHash) || newHash.equals(existingHash))) {
                                newHashes.put(local, newHash);
                                return FileVisitResult.CONTINUE;
                            } else if (oldHash != null && !oldHash.equals(existingHash)) {
                                var oldFilePath = destinationOldModified.resolve(local);
                                Files.createDirectories(oldFilePath.getParent());
                                Files.deleteIfExists(oldFilePath);
                                Files.move(outPath, oldFilePath);
                                Logger.info("File '%s' was modified, but modpack required it to be updated! Moving it to '%s'", local, "old_modified_files/" + local);
                            }
                            Files.deleteIfExists(outPath);
                        }

                        Files.copy(file, outPath, StandardCopyOption.REPLACE_EXISTING);
                        newHashes.put(local, newHash);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        return FileVisitResult.TERMINATE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        Logger.info("Finished extracting files from mrpack!");
    }

    public boolean checkJavaVersion() {
        var minecraft = this.index.dependencies.get(Constants.MINECRAFT);
        if (!minecraft.startsWith("1.")) {
            return true;
        }

        if (FlexVerComparator.compare(minecraft, "1.20.5-") >= 0 && !JavaVersion.IS_JAVA_21) {
            Logger.error("Minecraft %s! only supports Java 21 or newer! You are currently using Java %s!", minecraft, Runtime.version().feature());
            return false;
        }else if (FlexVerComparator.compare(minecraft, "1.18-") >= 0 && !JavaVersion.IS_JAVA_17) {
            Logger.error("Minecraft %s only supports Java 17 or newer! You are currently using Java %s!", minecraft, Runtime.version().feature());
            return false;
        } else if (FlexVerComparator.compare(minecraft, "1.17-") >= 0 && !JavaVersion.IS_JAVA_16) {
            Logger.error("Minecraft %s only supports Java 16 or newer! You are currently using Java %s!", minecraft, Runtime.version().feature());
            return false;
        } else if (FlexVerComparator.compare(minecraft, "1.13-") > 0 && FlexVerComparator.compare(minecraft, "1.17-") < 0 && this.index.dependencies.containsKey(Constants.FORGE) && JavaVersion.IS_JAVA_9) {
            Logger.error("Minecraft with Forge on %s only supports Java 8! You are currently using Java %s!", minecraft, Runtime.version().feature());
            return false;
        }


        return true;
    }

    public void requestDownloads(FileDownloader downloader, Map<String, HashData> hashExisting) throws Exception {
        if (this.currentInstanceData.runnablePath.isEmpty() || !this.currentInstanceData.dependencies.equals(this.index.dependencies) || !Files.exists(this.destination.resolve(this.currentInstanceData.runnablePath))) {
            var mcVersion = this.index.dependencies.get(Constants.MINECRAFT);
            if (mcVersion == null) {
                Logger.warn("Minecraft version is not set!");
                Logger.warn("The modpack will be still installed, but it won't be able to start!");
            } else if (this.index.dependencies.containsKey(Constants.FABRIC)) {
                var version = this.index.dependencies.get(Constants.FABRIC);
                if (FlexVerComparator.compare(version, "0.12.0") >= 0) {
                    var starter = FabricInstallerLookup.download(downloader, this.destination, mcVersion, version);
                    if (starter != null) {
                        this.newLauncher = starter.name();
                    }
                } else {
                    this.forceSystemClasspath = true;
                    var installer = FabricInstallerLookup.downloadGeneric(downloader, this.destination);
                    if (installer != null) {
                        this.installer = new Installer("Fabric Installer", installer.name(), "server", "-mcversion", mcVersion, "-loader", version);
                    }

                    this.newLauncher = "fabric-server-launch.jar";
                    var propPath = this.destination.resolve("fabric-server-launcher.properties");
                    Files.deleteIfExists(propPath);
                    var vanilla = VanillaInstallerLookup.download(downloader, this.destination, mcVersion);
                    if (vanilla != null) {
                        Files.writeString(propPath, "serverJar=" + vanilla.name());
                    }
                }
            } else if (this.index.dependencies.containsKey(Constants.NEOFORGE)) {
                this.forceSystemClasspath = true;
                var version = this.index.dependencies.get(Constants.NEOFORGE);
                var installer = mcVersion.equals("1.20.1")
                        ? NeoForgeInstallerLookup.downloadLegacy(downloader, this.destination, mcVersion, version)
                        : NeoForgeInstallerLookup.download(downloader, this.destination, version);
                if (installer != null) {
                    this.installer = new Installer("NeoForge Server Installer", installer.name(), "--installServer");
                }
                var starter = ForgeStarterLookup.download(downloader, this.destination);
                if (starter != null) {
                    this.newLauncher = starter.name();
                }
            } else if (this.index.dependencies.containsKey(Constants.FORGE)) {
                this.forceSystemClasspath = true;
                var version = this.index.dependencies.get(Constants.FORGE);

                var installer = ForgeInstallerLookup.download(downloader, this.destination, mcVersion, version);
                if (installer != null) {
                    this.installer = new Installer("Forge Server Installer", installer.name(), "--installServer");
                }
                if (FlexVerComparator.compare(mcVersion, "1.17.0") >= 0) {
                    var starter = ForgeStarterLookup.download(downloader, this.destination);
                    if (starter != null) {
                        this.newLauncher = starter.name();
                    }
                } else {
                    this.newLauncher = "forge-" + mcVersion + "-" + version + ".jar";
                }
            } else if (this.index.dependencies.containsKey(Constants.QUILT)) {
                this.forceSystemClasspath = true;
                var version = this.index.dependencies.get(Constants.QUILT);
                var installer = QuiltInstallerLookup.download(downloader, this.destination);
                if (installer != null) {
                    this.installer = new Installer("Quilt Installer", installer.name(), "install", "server", mcVersion, version, "--install-dir=./");
                }

                this.newLauncher = "quilt-server-launch.jar";

                var propPath = this.destination.resolve("quilt-server-launcher.properties");
                Files.deleteIfExists(propPath);
                var vanilla = VanillaInstallerLookup.download(downloader, this.destination, mcVersion);
                if (vanilla != null) {
                    Files.writeString(propPath, "serverJar=" + vanilla.name());
                }
            } else if (this.index.dependencies.size() == 1) {
                var starter = VanillaInstallerLookup.download(downloader, this.destination, mcVersion);
                if (starter != null) {
                    this.newLauncher = starter.name();
                }
            } else {
                Logger.warn("Modpack requires a modloader, which is not yet supported!");
                Logger.warn("The modpack will be still installed, but it won't be able to start!");
            }

            if (this.installer != null) {
                hashExisting.remove(this.installer.path);
            }
            if (this.newLauncher != null) {
                hashExisting.remove(this.newLauncher);
            }
        } else {
            hashExisting.remove(this.currentInstanceData.runnablePath);
        }

        for (var file : this.index.files) {
            var currentHash = hashExisting.remove(file.path);
            var path = this.destination.resolve(file.path);

            if (currentHash != null) {
                var newHash = HashData.read(Constants.MODRINTH_HASH, file.hashes);

                if (newHash.equals(this.oldHashes.get(file.path))) {
                    this.newHashes.put(file.path, this.oldHashes.get(file.path));
                    continue;
                } else if (currentHash.equals(newHash)) {
                    this.newHashes.put(file.path, newHash);
                    continue;
                } else if (nonOverwritablePaths.contains(file.path) && Files.exists(path)) {
                    Logger.warn("Skipping non-overwritable file: %s", file.path);
                    this.newHashes.put(file.path, this.oldHashes.get(file.path));
                    continue;
                } else if (currentHash.equals(this.oldHashes.get(file.path))) {
                    Files.deleteIfExists(this.destination.resolve(file.path));
                } else {
                    Files.createDirectories(this.destinationOldModified.resolve(file.path).getParent());
                    Files.move(this.destination.resolve(file.path), this.destinationOldModified.resolve(file.path));
                    Logger.info("File '%s' was modified, but modpack required it to be updated! Moving it to '%s'", file.path, "old_modified_files/" + file.path);
                    Files.deleteIfExists(this.destination.resolve(file.path));
                }
            } else if (nonOverwritablePaths.contains(file.path) && Files.exists(path)) {
                Logger.warn("Skipping non-overwritable file: %s", file.path);
                continue;
            }

            for (var url : file.downloads) {
                if (!this.whitelistedDomains.contains(url.getHost())) {
                    throw new RuntimeException("Non-whitelisted domain! " + url);
                }
            }

            if (Files.exists(path)) {
                Files.createDirectories(this.destinationOldModified.resolve(file.path).getParent());
                Files.deleteIfExists(this.destinationOldModified.resolve(file.path));
                Files.move(this.destination.resolve(file.path), this.destinationOldModified.resolve(file.path));
            }

            if (!file.env.getOrDefault("server", "required").equals("unsupported")) {
                Files.createDirectories(path.getParent());
                downloader.request(path, file.path, file.fileSize, HashData.read(Constants.MODRINTH_HASH, file.hashes.get(Constants.MODRINTH_HASH)), file.downloads);
            }
        }
    }
    public Map<String, HashData> getLocalFileUpdatedHashes() throws Exception {
        if (this.oldHashes.isEmpty()) {
            return new HashMap<>();
        }

        var hashExisting = new HashMap<String, HashData>();
        for (var entry : this.oldHashes.entrySet()) {
            var out = this.destination.resolve(entry.getKey());
            if (!Files.exists(out)) {
                continue;
            }
            try (var stream = Files.newInputStream(out)) {
                var digest = MessageDigest.getInstance(entry.getValue().type());
                byte[] dataBytes = new byte[1024];
                int nread = 0;
                while ((nread = stream.read(dataBytes)) != -1) {
                    digest.update(dataBytes, 0, nread);
                }
                hashExisting.put(entry.getKey(), new HashData(entry.getValue().type(), digest.digest()));
            }
        }
        return hashExisting;
    }

    public void cleanupLeftoverFiles(Map<String, HashData> hashExisting) throws Exception {
        for (var entry : hashExisting.keySet()) {
            var currentHash = hashExisting.get(entry);
            if (currentHash.equals(this.oldHashes.get(entry))) {
                Files.deleteIfExists(this.destination.resolve(entry));
            } else {
                Files.createDirectories(this.destinationOldModified.resolve(entry).getParent());
                Files.deleteIfExists(this.destinationOldModified.resolve(entry));
                Files.move(this.destination.resolve(entry), this.destinationOldModified.resolve(entry));
                Logger.info("File '%s' was modified, but modpack required it to be removed! Moving it to '%s'", entry, "old_modified_files/" + entry);
                Files.deleteIfExists(this.destination.resolve(entry));
            }
        }
    }

    public Map<String, HashData> getHashes() {
        return this.newHashes;
    }

    private @Nullable HashData getHash(String type, Path path) {
        if (!Files.exists(path)) {
            return null;
        }

        try (var stream = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance(type);
            byte[] dataBytes = new byte[1024];
            int nread = 0;
            while ((nread = stream.read(dataBytes)) != -1) {
                digest.update(dataBytes, 0, nread);
            }
            return new HashData(type, digest.digest());
        } catch (Throwable e) {
            Logger.error("Error occurred while creating hash for '%s'!", path, e);
        }
        return null;
    }

    @Nullable
    public String getNewLauncher() {
        return this.newLauncher;
    }


    @Nullable
    public Installer getInstaller() {
        return this.installer;
    }

    public boolean forceSystemClasspath() {
        return this.forceSystemClasspath;
    }

    public record Installer(String name, String path, String... args) {}
}

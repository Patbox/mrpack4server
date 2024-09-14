package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.format.InstanceInfo;
import eu.pb4.mrpackserver.format.ModpackIndex;
import eu.pb4.mrpackserver.util.FlexVerComparator;
import eu.pb4.mrpackserver.util.Logger;
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
    private final HashMap<String, String> oldHashes;
    private final HashMap<String, String> newHashes;
    private final Path destinationOldModified;
    private final InstanceInfo currentInstanceData;
    private final Set<String> whitelistedDomains;
    @Nullable
    private String newLauncher;
    @Nullable
    private String forgeInstaller;

    public MrPackInstaller(Path source, ModpackIndex index, Path destination, InstanceInfo data, HashMap<String, String> hashes, Set<String> whitelistedDomains) throws IOException {
        this.source = source;
        this.index = index;
        this.currentInstanceData = data;
        this.destination = destination.toAbsolutePath();
        this.destinationOldModified = this.destination.resolve("old_modified_files");


        this.oldHashes = hashes;
        this.newHashes = new HashMap<>();

        this.whitelistedDomains = whitelistedDomains;
    }

    public void prepareFolders() throws IOException {
        Files.createDirectories(this.destination);
    }

    public void extractIncluded() throws IOException {
        Logger.info("Extracting included files.");
        for (var base : Constants.OVERWRITES) {
            var path = this.source.resolve(base);
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        var p = destination.resolve(path.relativize(dir).toString()).normalize();
                        if (!p.startsWith(destination.toAbsolutePath())) {
                            Logger.error("Modpack contains files, that are placed outside of server's root! Found '%s'", path.relativize(dir).toString());
                            return FileVisitResult.TERMINATE;
                        }

                        if (Constants.NON_OVERWRITABLE.contains(p.toString()) && Files.exists(p)) {
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

                        if (!Constants.NON_OVERWRITABLE.contains(outPath.toString()) || !Files.exists(file)) {
                            var oldHash = oldHashes.get(local);
                            byte[] existingHash = getHash(outPath);
                            byte[] newHash = getHash(file);

                            assert newHash != null;

                            if (existingHash != null) {
                                if ((oldHash != null && Arrays.equals(HexFormat.of().parseHex(oldHash), newHash)) || Arrays.equals(existingHash, newHash)) {
                                    newHashes.put(local, HexFormat.of().formatHex(newHash));
                                    return FileVisitResult.CONTINUE;
                                } else if (oldHash != null && !Arrays.equals(HexFormat.of().parseHex(oldHash), existingHash)) {
                                    var oldFilePath = destinationOldModified.resolve(local);
                                    Files.createDirectories(oldFilePath.getParent());
                                    Files.deleteIfExists(oldFilePath);
                                    Files.move(outPath, oldFilePath);
                                    Logger.info("File '%s' was modified, but modpack required it to be updated! Moving it to '%s'", local, "old_modified_files/" + local);
                                }
                                Files.deleteIfExists(outPath);
                            }

                            Files.copy(file, outPath, StandardCopyOption.REPLACE_EXISTING);
                            newHashes.put(local, HexFormat.of().formatHex(newHash));
                        } else {
                            Logger.warn("Skipping non-overwritable file: %s", path);
                        }

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

    public void requestDownloads(FileDownloader downloader) throws Exception {
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
                    Logger.warn("Only Fabric Version 0.12.0 and newer is supported for launching!");
                    Logger.warn("The modpack will be still installed, but it won't be able to start!");
                }
            } else if (this.index.dependencies.containsKey(Constants.NEOFORGE)) {
                var version = this.index.dependencies.get(Constants.NEOFORGE);
                var installer = mcVersion.equals("1.20.1")
                        ? NeoForgeInstallerLookup.downloadLegacy(downloader, this.destination, mcVersion, version)
                        : NeoForgeInstallerLookup.download(downloader, this.destination, version);
                if (installer != null) {
                    this.forgeInstaller = installer.name();
                }
                var starter = ForgeStarterLookup.download(downloader, this.destination);
                if (starter != null) {
                    this.newLauncher = starter.name();
                }
            } else if (this.index.dependencies.containsKey(Constants.FORGE)) {
                var version = this.index.dependencies.get(Constants.FORGE);
                if (FlexVerComparator.compare(mcVersion, "1.17.0") >= 0) {
                    var installer = ForgeInstallerLookup.download(downloader, this.destination, mcVersion, version);
                    if (installer != null) {
                        this.forgeInstaller = installer.name();
                    }
                    var starter = ForgeStarterLookup.download(downloader, this.destination);
                    if (starter != null) {
                        this.newLauncher = starter.name();
                    }
                } else {
                    Logger.warn("Only Forge for Minecraft 1.17 and newer is supported for launching!");
                    Logger.warn("The modpack will be still installed, but it won't be able to start!");
                }
            } else if (this.index.dependencies.containsKey(Constants.QUILT)) {
                Logger.warn("Quilt Loader is not currently supported!");
                Logger.warn("The modpack will be still installed, but it won't be able to start!");
            } else if (this.index.dependencies.size() == 1) {
                var starter = VanillaInstallerLookup.download(downloader, this.destination, mcVersion);
                if (starter != null) {
                    this.newLauncher = starter.name();
                }
            } else {
                Logger.warn("Modpack requires a modloader, which is not yet supported!");
                Logger.warn("The modpack will be still installed, but it won't be able to start!");
            }
        }
        for (var file : this.index.files) {
            if (newHashes.containsKey(file.path)) {
                continue;
            }

            for (var url : file.downloads) {
                if (!this.whitelistedDomains.contains(url.getHost())) {
                    throw new RuntimeException("Non-whitelisted domain! " + url);
                }
            }

            var path = this.destination.resolve(file.path);
            if (Files.exists(path)) {
                Files.createDirectories(this.destinationOldModified.resolve(file.path).getParent());
                Files.deleteIfExists(this.destinationOldModified.resolve(file.path));
                Files.move(this.destination.resolve(file.path), this.destinationOldModified.resolve(file.path));
            }

            if (!file.env.getOrDefault("server", "required").equals("unsupported")) {
                Files.createDirectories(path.getParent());
                Logger.info("Requesting download for file %s", file.path);
                downloader.request(path, file.path, file.fileSize, file.hashes.get(Constants.HASH), file.downloads);
            }
        }
    }

    public void cleanupOutdatedFiles() throws Exception {
        if (this.oldHashes.isEmpty()) {
            return;
        }
        Logger.info("Cleaning up old existing files...");
        var hashExisting = new HashMap<String, byte[]>();
        for (var entry : this.oldHashes.keySet()) {
            var out = this.destination.resolve(entry);
            if (!Files.exists(out)) {
                continue;
            }
            try (var stream = Files.newInputStream(out)) {
                var digest = MessageDigest.getInstance("SHA-512");
                byte[] dataBytes = new byte[1024];
                int nread = 0;
                while ((nread = stream.read(dataBytes)) != -1) {
                    digest.update(dataBytes, 0, nread);
                }
                hashExisting.put(entry, digest.digest());
            }
        }

        for (var file : this.index.files) {
            var currentHash = hashExisting.remove(file.path);
            if (currentHash == null) {
                continue;
            }
            var newHash = HexFormat.of().parseHex(file.hashes.get(Constants.HASH));

            if (Arrays.equals(newHash, HexFormat.of().parseHex(this.oldHashes.get(file.path)))) {
                this.newHashes.put(file.path, this.oldHashes.get(file.path));
            } else if (Arrays.equals(currentHash, newHash)) {
                this.newHashes.put(file.path, file.hashes.get(Constants.HASH));
            } else if (Arrays.equals(currentHash, HexFormat.of().parseHex(this.oldHashes.get(file.path)))) {
                Files.deleteIfExists(this.destination.resolve(file.path));
            } else {
                Files.createDirectories(this.destinationOldModified.resolve(file.path).getParent());
                Files.move(this.destination.resolve(file.path), this.destinationOldModified.resolve(file.path));
                Logger.info("File '%s' was modified, but modpack required it to be updated! Moving it to '%s'", file.path, "old_modified_files/" + file.path);
                Files.deleteIfExists(this.destination.resolve(file.path));
            }
        }

        for (var entry : hashExisting.keySet()) {
            var currentHash = hashExisting.get(entry);

            if (Arrays.equals(currentHash, HexFormat.of().parseHex(this.oldHashes.get(entry)))) {
                Files.deleteIfExists(this.destination.resolve(entry));
            } else {
                Files.createDirectories(this.destinationOldModified.resolve(entry).getParent());
                Files.move(this.destination.resolve(entry), this.destinationOldModified.resolve(entry));
                Logger.info("File '%s' was modified, but modpack required it to be removed! Moving it to '%s'", entry, "old_modified_files/" + entry);
                Files.deleteIfExists(this.destination.resolve(entry));
            }
        }
    }

    public Map<String, String> getHashes() {
        return this.newHashes;
    }

    private byte @Nullable [] getHash(Path path) {
        if (!Files.exists(path)) {
            return null;
        }

        try (var stream = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance("SHA-512");
            byte[] dataBytes = new byte[1024];
            int nread = 0;
            while ((nread = stream.read(dataBytes)) != -1) {
                digest.update(dataBytes, 0, nread);
            }
            return digest.digest();
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
    public String getForgeInstaller() {
        return this.forgeInstaller;
    }
}

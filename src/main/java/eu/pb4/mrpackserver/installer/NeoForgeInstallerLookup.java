package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.Logger;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface NeoForgeInstallerLookup {
    static String createName(String version) {
        return Constants.DATA_FOLDER + "/server/neoforge_installer_" + version + ".jar";
    }

    @Nullable
    static Result download(FileDownloader downloader, Path path, String version) {
        try {
            var name = createName(version);
            var file = path.resolve(name);
            if (!Files.exists(file)) {
                var display = "NeoForge Server Installer " + version;
                Logger.info("Requesting download for %s.", display);
                Files.createDirectories(file.getParent());
                downloader.request(file, name, display, -1, null, List.of(
                        URI.create("https://maven.neoforged.net/releases/net/neoforged/neoforge/" + version + "/neoforge-" + version + "-installer.jar")
                ));
            }
            return new Result(name, file);
        } catch (Throwable e) {
            Logger.warn("Failed to lookup NeoForge Server Installer!", e);
        }
        return null;
    }

    @Nullable
    static Result downloadLegacy(FileDownloader downloader, Path path, String mcVersion, String version) {
        try {
            var name = createName(mcVersion + "-" + version);
            var file = path.resolve(name);
            if (!Files.exists(file)) {
                var display = "NeoForge Server Installer " + version;
                Logger.info("Requesting download for %s.", display);
                Files.createDirectories(file.getParent());
                downloader.request(file, name, display, -1, null, List.of(
                        URI.create("https://maven.neoforged.net/releases/net/neoforged/forge/" +
                                mcVersion + "-" + version + "/forge-" + mcVersion + "-" + version + "-installer.jar")
                ));
            }
            return new Result(name, file);
        } catch (Throwable e) {
            Logger.warn("Failed to lookup NeoForge Server Installer!", e);
        }
        return null;
    }

    record Result(String name, Path path) {};
}

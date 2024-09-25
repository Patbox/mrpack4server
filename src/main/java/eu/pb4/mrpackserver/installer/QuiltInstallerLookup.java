package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.Logger;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface QuiltInstallerLookup {
    String VERSION = "0.9.2";
    String STARTER_URL = "https://quiltmc.org/api/v1/download-latest-installer/java-universal";

    static String createName() {
        return Constants.DATA_FOLDER + "/server/quilt_installer_" + VERSION + ".jar";
    }

    @Nullable
    static Result download(FileDownloader downloader, Path path) {
        try {
            var name = createName();
            var file = path.resolve(name);
            if (!Files.exists(file)) {
                var display = "Quilt Installer " + VERSION;
                Logger.info("Requesting download for %s.", display);
                Files.createDirectories(file.getParent());
                downloader.request(file, name, display, -1, null, List.of(
                        URI.create(STARTER_URL)
                ));
            }
            return new Result(name, file);
        } catch (Throwable e) {
            Logger.warn("Failed to lookup Quilt Installer!", e);
        }
        return null;
    }

    record Result(String name, Path path) {};
}

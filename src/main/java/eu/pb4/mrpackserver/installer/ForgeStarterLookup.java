package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.format.FabricInstallerVersion;
import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.Logger;
import eu.pb4.mrpackserver.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface ForgeStarterLookup {
    String VERSION = "0.1.25";
    String STARTER_URL = "https://github.com/neoforged/ServerStarterJar/releases/download/0.1.25/server.jar";

    static String createName() {
        return Constants.DATA_FOLDER + "/server/forge_starter_" + VERSION + ".jar";
    }

    @Nullable
    static Result download(FileDownloader downloader, Path path) {
        try {
            var name = createName();
            var file = path.resolve(name);
            if (!Files.exists(file)) {
                var display = "(Neo)Forge Server Starter " + VERSION;
                Files.createDirectories(file.getParent());
                downloader.request(file, name, display, -1, null, List.of(
                        URI.create(STARTER_URL)
                ));
            }
            return new Result(name, file);
        } catch (Throwable e) {
            Logger.warn("Failed to lookup (Neo)Forge Server Starter!", e);
        }
        return null;
    }

    record Result(String name, Path path) {};
}

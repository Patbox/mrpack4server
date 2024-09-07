package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.Logger;
import eu.pb4.mrpackserver.util.Utils;
import eu.pb4.mrpackserver.format.FabricInstallerVersion;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface FabricInstallerLookup {
    static String createName(String mcVersion, String fabricVersion, String installerVersion) {
        return Constants.DATA_FOLDER + "/server/mc-" + mcVersion + "_fab-" + fabricVersion + "_inst_" + installerVersion + ".jar";
    }

    @Nullable
    static Result download(FileDownloader downloader, Path path, String mcVersion, String fabricVersion) {
        try {
            var client = Utils.createHttpClient();
            var res = client.send(Utils.createGetRequest(URI.create(Constants.FABRIC_INSTALLER_VERSIONS)), HttpResponse.BodyHandlers.ofString());
            var versions = Utils.GSON.fromJson(res.body(), FabricInstallerVersion.TYPE);
            var version = versions.stream().filter(fabricInstallerVersion -> fabricInstallerVersion.stable).findFirst().map(x -> x.version).orElse("1.0.1");
            var name = createName(mcVersion, fabricVersion, version);
            var file = path.resolve(name);
            if (!Files.exists(file)) {
                Logger.info("Downloading Fabric Installer %s for Loader %s and Minecraft %s.", version, fabricVersion, mcVersion);
                Files.createDirectories(file.getParent());
                downloader.request(file, name, -1, null, List.of(
                        URI.create("https://meta.fabricmc.net/v2/versions/loader/" + mcVersion + "/" + fabricVersion + "/" + version + "/server/jar")
                ));
            }
            return new Result(version, name, file);
        } catch (Throwable e) {
            Logger.warn("Failed to lookup Fabric installer!", e);
        }
        return null;
    }

    record Result(String loaderVersion, String name, Path path) {};
}

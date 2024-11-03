package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.Logger;
import eu.pb4.mrpackserver.util.Utils;
import eu.pb4.mrpackserver.format.FabricInstallerVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface FabricInstallerLookup {
    static String createName(String mcVersion, String fabricVersion, String installerVersion) {
        return Constants.DATA_FOLDER + "/server/mc-" + mcVersion + "_fab-" + fabricVersion + "_inst_" + installerVersion + ".jar";
    }

    static String createName(String installerVersion) {
        return Constants.DATA_FOLDER + "/server/fabric_installer_" + installerVersion + ".jar";
    }

    @Nullable
    static Result downloadGeneric(FileDownloader downloader, Path path) {
        try {
            var version = getInstallerVersion();
            var name = createName(version);
            var file = path.resolve(name);
            if (!Files.exists(file)) {
                var display = "Fabric Installer " + version;
                Files.createDirectories(file.getParent());
                downloader.request(file, name, display, -1, null, List.of(
                        URI.create("https://maven.fabricmc.net/net/fabricmc/fabric-installer/" + version + "/fabric-installer-" + version + ".jar")
                ));
            }
            return new Result(version, name, file);
        } catch (Throwable e) {
            Logger.warn("Failed to lookup Fabric installer!", e);
        }
        return null;
    }

    @Nullable
    static Result download(FileDownloader downloader, Path path, String mcVersion, String fabricVersion) {
        try {
            var version = getInstallerVersion();
            var name = createName(mcVersion, fabricVersion, version);
            var file = path.resolve(name);
            if (!Files.exists(file)) {
                var display = "Fabric Installer " + version + " for Loader " + fabricVersion + " and Minecraft " + mcVersion;

                Files.createDirectories(file.getParent());
                downloader.request(file, name, display, -1, null, List.of(
                        URI.create("https://meta.fabricmc.net/v2/versions/loader/" + mcVersion + "/" + fabricVersion + "/" + version + "/server/jar")
                ));
            }
            return new Result(version, name, file);
        } catch (Throwable e) {
            Logger.warn("Failed to lookup Fabric installer!", e);
        }
        return null;
    }

    static String getInstallerVersion() throws IOException, InterruptedException {
        var client = Utils.createHttpClient();
        var res = client.send(Utils.createGetRequest(URI.create(Constants.FABRIC_INSTALLER_VERSIONS)), HttpResponse.BodyHandlers.ofString());
        var versions = FabricInstallerVersion.read(res.body());
        return versions.stream().filter(fabricInstallerVersion -> fabricInstallerVersion.stable).findFirst().map(x -> x.version).orElse("1.0.1");
    }

    record Result(String loaderVersion, String name, Path path) {};
}

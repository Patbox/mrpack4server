package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.format.VanillaVersionData;
import eu.pb4.mrpackserver.format.VanillaVersionList;
import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.Logger;
import eu.pb4.mrpackserver.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface VanillaInstallerLookup {
    String VERSION_LIST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    static String createName(String mcVersion) {
        return Constants.DATA_FOLDER + "/server/vanilla_" + mcVersion + ".jar";
    }

    @Nullable
    static Result download(FileDownloader downloader, Path path, String mcVersion) {
        try {
            var display = "Minecraft Vanilla " + mcVersion;
            var client = Utils.createHttpClient();
            var res = client.send(Utils.createGetRequest(URI.create(VERSION_LIST)), HttpResponse.BodyHandlers.ofString());
            var versions = Utils.GSON.fromJson(res.body(), VanillaVersionList.class);
            var version = versions.versions.stream().filter(x -> x.id.equals(mcVersion)).findFirst();
            if (version.isEmpty()) {
                Logger.error("Failed to find %s!", display);
                return null;
            }
            var res2 = client.send(Utils.createGetRequest(URI.create(VERSION_LIST)), HttpResponse.BodyHandlers.ofString());
            var versionData = Utils.GSON.fromJson(res2.body(), VanillaVersionData.class).downloads.get("server");
            if (versionData == null) {
                Logger.error("Failed to find server file for %s!", display);
                return null;
            }

            var name = createName(mcVersion);
            var file = path.resolve(name);
            if (!Files.exists(file)) {
                Logger.info("Requesting download for %s.", display);
                Files.createDirectories(file.getParent());
                downloader.request(file, name, display, versionData.size, null, List.of(
                        URI.create(versionData.url)
                ));
            }
            return new Result(name, file);
        } catch (Throwable e) {
            Logger.warn("Failed to lookup Minecraft Vanilla!", e);
        }
        return null;
    }

    record Result(String name, Path path) {};
}

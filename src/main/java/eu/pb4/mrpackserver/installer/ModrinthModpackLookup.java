package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.format.ModrinthModpackVersion;
import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.Logger;
import eu.pb4.mrpackserver.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public interface ModrinthModpackLookup {
    @Nullable
    static Result find(String projectId, String versionId, String displayName, String displayVersion) {
        try {
            var client = Utils.createHttpClient();
            var res = client.send(Utils.createGetRequest(URI.create("https://api.modrinth.com/v2/project/" + projectId + "/version")), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                Logger.warn("Failed to lookup modpack files for %s (%s)! Got code response %s | %s", displayName, projectId, res.statusCode(), res.body());
                return null;
            }

            var versions = Utils.GSON.fromJson(res.body(), ModrinthModpackVersion.TYPE);
            var version = versions.stream().filter(x -> x.versionNumber.equals(versionId) || x.id.equals(versionId)).findFirst();
            if (version.isEmpty()) {
                Logger.error("Failed to find version %s (%s) of modpack %s (%s)!", displayVersion, versionId, displayName, projectId);
                return null;
            }

            var file = version.get().files.stream().filter(x -> x.primary).findFirst();
            if (file.isEmpty()) {
                Logger.error("Failed to find files for version %s (%s) of modpack %s (%s)!", displayVersion, versionId, displayName, projectId);
                return null;
            }

            return new Result(file.get().url, file.get().size, file.get().hashes);
        } catch (Throwable e) {
            Logger.error("Failed to lookup modpack files for %s (%s)!", displayName, projectId, e);
        }
        return null;
    }

    record Result(URI uri, long size, java.util.Map<String, String> hashes) {};
}

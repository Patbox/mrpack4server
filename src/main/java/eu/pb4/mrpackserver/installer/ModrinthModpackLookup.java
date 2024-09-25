package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.format.ModrinthModpackVersion;
import eu.pb4.mrpackserver.util.Logger;
import eu.pb4.mrpackserver.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public interface ModrinthModpackLookup {
    @Nullable
    static Result findVersion(String projectId, String displayName, String displayVersion, boolean logError, Predicate<ModrinthModpackVersion> versionPredicate) {
        var versions = getVersions(projectId, displayName);
        if (versions == null) {
            return null;
        }

        var version = versions.stream().filter(versionPredicate).findFirst();
        if (version.isEmpty()) {
            if (logError) {
                Logger.error("Failed to find version %s of modpack %s (%s)!", displayVersion, displayName, projectId);
            }
            return null;
        }

        var file = version.get().files.stream().filter(x -> x.primary).findFirst();
        if (file.isEmpty()) {
            if (logError) {
                Logger.error("Failed to find files for version %s of modpack %s (%s)!", displayVersion, displayName, projectId);
            }
            return null;
        }
        return new Result(version.get().id, version.get().versionNumber, file.get().url, file.get().size, file.get().hashes);
    }

    @Nullable
    static List<ModrinthModpackVersion> getVersions(String projectId, String displayName) {
        try {
            var client = Utils.createHttpClient();
            var res = client.send(Utils.createGetRequest(URI.create("https://api.modrinth.com/v2/project/" + projectId + "/version")), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                Logger.warn("Failed to lookup modpack files for %s (%s)! Got code response %s | %s", displayName, projectId, res.statusCode(), res.body());
                return null;
            }

            return ModrinthModpackVersion.read(res.body());
        } catch (Throwable e) {
            Logger.error("Failed to lookup modpack files for %s (%s)!", displayName, projectId, e);
            return null;
        }
    }

    record Result(String versionId, String versionNumber, URI uri, long size, java.util.Map<String, String> hashes) {};
}

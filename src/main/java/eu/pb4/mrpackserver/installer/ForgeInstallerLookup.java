package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.Logger;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.jar.JarFile;

public interface ForgeInstallerLookup {
    String MAVEN_METADATA_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    String MAVEN_ARTIFACT_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/";

    static String createName(String version) {
        return Constants.DATA_FOLDER + "/server/forge_installer_" + version + ".jar";
    }

    @Nullable
    static Result download(FileDownloader downloader, Path path, String mcVersion, String version) {
        try {
            var coordinate = resolveCoordinate(mcVersion, version);
            if (coordinate == null) {
                throw new IllegalStateException("Forge " + mcVersion + "-" + version + " is not published in the Forge Maven metadata");
            }

            var name = createName(mcVersion + "-" + version);
            var file = path.resolve(name);
            if (isValidInstaller(file)) {
                return new Result(name, file, createServerJarName(coordinate));
            }

            Files.deleteIfExists(file);
            var display = "Forge Server Installer " + mcVersion + "-" + version;
            Files.createDirectories(file.getParent());
            var artifact = "forge-" + coordinate + "-installer.jar";
            downloader.requestJar(file, name, display, -1, null, List.of(
                    URI.create(MAVEN_ARTIFACT_URL + coordinate + "/" + artifact)
            ));
            return new Result(name, file, createServerJarName(coordinate));
        } catch (Throwable e) {
            Logger.warn("Failed to lookup Forge Server Installer installer!", e);
        }
        return null;
    }

    static String createServerJarName(String coordinate) {
        return "forge-" + coordinate + "-universal.jar";
    }

    static String resolveCoordinate(String mcVersion, String version, List<String> publishedVersions) {
        var base = mcVersion + "-" + version;
        if (publishedVersions.contains(base)) {
            return base;
        }
        var legacy = base + "-" + mcVersion;
        return publishedVersions.contains(legacy) ? legacy : null;
    }

    @Nullable
    static String resolveCoordinate(String mcVersion, String version) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(MAVEN_METADATA_URL))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "mrpack4server")
                .GET()
                .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }

        var matcher = Pattern.compile("<version>\\s*([^<]+?)\\s*</version>").matcher(response.body());
        var versions = new java.util.ArrayList<String>();
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }
        return resolveCoordinate(mcVersion, version, versions);
    }

    static boolean isValidInstaller(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try (var ignored = new JarFile(file.toFile())) {
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    record Result(String name, Path path, String serverJarName) {};
}

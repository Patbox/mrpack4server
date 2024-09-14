package eu.pb4.mrpackserver;

import eu.pb4.mrpackserver.format.ModpackInfo;
import eu.pb4.mrpackserver.util.Logger;
import eu.pb4.mrpackserver.util.Utils;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class Create {
    public static void main(String[] args) throws Throwable {
        var argMap = new HashMap<String, String>();

        for (int i = 0; i < args.length; i++) {
            var arg = args[i];
            if (arg.startsWith("--")) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    argMap.put(arg.substring(2), args[i+1]);
                    i++;
                }
            }
        }
        Logger.info("Creating jar with bundled modpack-info.");

        var runPath = Paths.get("");
        ModpackInfo info;
        if (argMap.containsKey("project_id")) {
            info = new ModpackInfo();
            info.versionId = argMap.getOrDefault("version_id", ";;release");
            info.projectId = argMap.get("project_id");
            info.displayName = argMap.get("display_name");
            info.displayVersion = argMap.get("display_version");
            info.url = argMap.containsKey("url") ? URI.create(argMap.get("url")) : null;
            if (info.url != null) {
                info.size = argMap.containsKey("size") ? Long.parseLong(argMap.get("size")) : null;
                info.sha512 = argMap.get("sha512");
            }
            if (argMap.containsKey("whitelist") || argMap.containsKey("whitelisted_domains")) {
                info.whitelistedDomains.addAll(List.of(argMap.getOrDefault("whitelist", argMap.getOrDefault("whitelisted_domains", "")).split(",")));
            }
        } else {
            info = Utils.resolveModpackInfo(runPath);
            if (info == null) {
                Logger.error("Couldn't find 'modpack-info.json'!");
                return;
            }
        }
        Logger.info("== Modpack Info ==");
        Logger.info("project_id: %s", info.projectId);
        Logger.info("version_id: %s", info.versionId);
        Logger.info("display_name: %s", Objects.requireNonNullElse(info.displayName, "<not set>"));
        Logger.info("display_version: %s", Objects.requireNonNullElse(info.displayVersion, "<not set>"));
        if (info.url != null) {
            Logger.info("url: %s", info.url);
            Logger.info("sha512: %s", Objects.requireNonNullElse(info.sha512, "<not set>"));
            Logger.info("size: %s", Objects.requireNonNullElse(info.size, "<not set>"));
        }
        Logger.info("whitelisted_domains: %s", String.join(", ", info.whitelistedDomains));
        Logger.info("== Modpack Info ==");

        Logger.info("Creating jar file.");
        var inputJarPath = Paths.get(Create.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var outName = argMap.getOrDefault("out", "server.jar");
        var outputJarPath = runPath.resolve(outName);
        Files.deleteIfExists(outputJarPath);
        Files.copy(inputJarPath, outputJarPath);

        try (var fs = FileSystems.newFileSystem(outputJarPath)) {
            Files.writeString(fs.getPath("modpack-info.json"), Utils.GSON.toJson(info));
        }

        Logger.info("Done! Bundled file exists as '%s'", outName);
    }
}

package eu.pb4.mrpackserver;

import eu.pb4.mrpackserver.format.InstanceInfo;
import eu.pb4.mrpackserver.installer.ModrinthModpackLookup;
import eu.pb4.mrpackserver.launch.Launcher;
import eu.pb4.mrpackserver.launch.LegacyExitBlocker;
import eu.pb4.mrpackserver.util.*;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

public class Main {
    public static boolean isLaunched = false;
    public static void main(String[] args) throws Throwable {
        System.setProperty("log4j2.formatMsgNoLookups", "true");

        boolean noGui = false;
        for (var arg : args) {
            if (arg.equals("nogui") || arg.equals("--nogui")) {
                noGui = true;
                break;
            }
        }

        if (!noGui) {
            noGui = GraphicsEnvironment.isHeadless();
        }

        var runPath = Paths.get("");
        var modpackInfo = Utils.resolveModpackInfo(runPath);

        if (!noGui) {
            InstallerGui.setup(modpackInfo);
        }

        if (modpackInfo == null) {
            Logger.info("Couldn't find modpack definition! Creating a new one...");
            Utils.configureModpack(runPath);

            modpackInfo = Utils.resolveModpackInfo(runPath);
            if (modpackInfo == null) {
                Logger.error("Couldn't find modpack definition! Exiting...");
                return;
            }
        }

        var instanceData = runPath.resolve(Constants.DATA_FOLDER);
        var instanceDataPath = instanceData.resolve("instance.json");
        var instanceInfo = new InstanceInfo();
        if (Files.exists(instanceDataPath)) {
            instanceInfo = InstanceInfo.read(Files.readString(instanceDataPath));
        }

        try {
            boolean runLogic = true;

            if (modpackInfo.versionId.startsWith(";;")) {
                Logger.info("Checking for updates for %s...", modpackInfo.getDisplayName());

                var type = modpackInfo.versionId.substring(2);
                var finalInstanceInfo = instanceInfo;
                var version = ModrinthModpackLookup.findVersion(modpackInfo.getVersionListUrl(), modpackInfo.projectId, modpackInfo.getDisplayName(), type, false,
                        x -> x.versionType.equals(type) && (finalInstanceInfo.versionId.isEmpty() || FlexVerComparator.compare(finalInstanceInfo.versionId, x.versionNumber) < 0));

                if (version == null) {
                    if (instanceInfo.versionId.isEmpty()) {
                        Logger.error("Failed to find %s version of %s! Quitting...", type, modpackInfo.getDisplayName());
                        return;
                    }
                    runLogic = false;
                } else {
                    modpackInfo.versionId = version.versionNumber();
                    modpackInfo.url = version.uri();
                    modpackInfo.size = version.size();
                    modpackInfo.sha512 = version.hashes().get(Constants.MODRINTH_HASH);
                }
            }

            if (runLogic && (!modpackInfo.projectId.equals(instanceInfo.projectId) || !modpackInfo.versionId.equals(instanceInfo.versionId) || instanceInfo.runnablePath.isEmpty())) {
                Files.createDirectories(instanceData);
                var newInstance = Utils.checkAndSetupModpack(modpackInfo, instanceInfo, runPath, instanceData);
                if (newInstance != null) {
                    Files.deleteIfExists(instanceDataPath);
                    Files.writeString(instanceDataPath, Utils.GSON_MAIN.toJson(newInstance.info()));
                    instanceInfo = newInstance.info();

                    LegacyExitBlocker.run(newInstance.installer());

                    Logger.info("Installation of %s (%s) finished!", modpackInfo.getDisplayName(), modpackInfo.getDisplayVersion());
                } else if (!instanceInfo.runnablePath.isEmpty()) {
                    Logger.error("Failed to install modpack! Quitting...");
                    return;
                } else {
                    return;
                }
            }
        } catch (Throwable e) {
            Logger.error("Exception occurred while installing modpack!", e);
            return;
        }

        if (instanceInfo.runnablePath.isEmpty()) {
            Logger.warn("Server was installed successfully, but there is no server launcher defined!");
            Logger.warn("This means the platform used by this modpack isn't supported!");
            Logger.warn("Refer to platforms installation guide for more information!");
            return;
        }

        isLaunched = true;

        if (!noGui && instanceInfo.dependencies.get(Constants.FORGE) != null && FlexVerComparator.compare(instanceInfo.dependencies.get(Constants.MINECRAFT), "1.17") < 0) {
            if (InstallerGui.instance != null) {
                InstallerGui.instance.handleForgeFix();
            }
        }

        if (instanceInfo.forceSystemClasspath) {
            Launcher.launchFinalInject(runPath.resolve(instanceInfo.runnablePath), args);
        } else {
            Launcher.launchFinal(Objects.requireNonNull(Launcher.getFromPath(runPath.resolve(instanceInfo.runnablePath))), (x) -> null, args);
        }
    }
}
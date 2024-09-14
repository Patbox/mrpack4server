package eu.pb4.mrpackserver;

import eu.pb4.mrpackserver.format.InstanceInfo;
import eu.pb4.mrpackserver.installer.ModrinthModpackLookup;
import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.FlexVerComparator;
import eu.pb4.mrpackserver.util.Logger;
import eu.pb4.mrpackserver.util.Utils;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Throwable {
        var runPath = Paths.get("");

        var modpackInfo = Utils.resolveModpackInfo(runPath);

        if (modpackInfo == null) {
            Logger.error("Couldn't find modpack definition!");
            return;
        }

        var instanceData = runPath.resolve(Constants.DATA_FOLDER);
        var instanceDataPath = instanceData.resolve("instance.json");
        var instanceInfo = new InstanceInfo();
        if (Files.exists(instanceDataPath)) {
            instanceInfo = Utils.GSON.fromJson(Files.readString(instanceDataPath), InstanceInfo.class);
        }

        try {
            boolean runLogic = true;

            if (modpackInfo.versionId.startsWith(";;")) {
                var type = modpackInfo.versionId.substring(2);
                var finalInstanceInfo = instanceInfo;
                var version = ModrinthModpackLookup.findVersion(modpackInfo.projectId, modpackInfo.getDisplayName(), type, false,
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
                    modpackInfo.sha512 = version.hashes().get(Constants.HASH);
                }
            }

            if (runLogic && (!modpackInfo.projectId.equals(instanceInfo.projectId) || !modpackInfo.versionId.equals(instanceInfo.versionId) || instanceInfo.runnablePath.isEmpty())) {
                Files.createDirectories(instanceData);
                var newInstance = Utils.checkAndSetupModpack(modpackInfo, instanceInfo, runPath, instanceData);
                if (newInstance != null) {
                    Files.deleteIfExists(instanceDataPath);
                    Files.writeString(instanceDataPath, Utils.GSON.toJson(newInstance.info()));
                    instanceInfo = newInstance.info();

                    newInstance.installer().run();

                    Logger.info("Installation of %s (%s) finished! Starting the server...", modpackInfo.getDisplayName(), modpackInfo.getDisplayVersion());
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
            return;
        }

        Utils.launchJar(runPath.resolve(instanceInfo.runnablePath), args);
    }
}
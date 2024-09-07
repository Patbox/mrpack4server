package eu.pb4.mrpackserver;

import eu.pb4.mrpackserver.format.InstanceInfo;
import eu.pb4.mrpackserver.util.Constants;
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

        if (!modpackInfo.projectId.equals(instanceInfo.projectId) || !modpackInfo.versionId.equals(instanceInfo.versionId) || instanceInfo.runnablePath.isEmpty()) {
            Files.createDirectories(instanceData);
            var newInstance = Utils.checkAndSetupModpack(modpackInfo, instanceInfo, runPath, instanceData);
            if (newInstance != null) {
                Files.deleteIfExists(instanceDataPath);
                Files.writeString(instanceDataPath, Utils.GSON.toJson(newInstance));
                instanceInfo = newInstance;
            } else if (!instanceInfo.runnablePath.isEmpty()) {
                Logger.error("Failed to install modpack! Quitting...");
            } else {
                return;
            }
        }

        if (instanceInfo.runnablePath.isEmpty()) {
            Logger.warn("Server was installed successfully, but there is no server launcher defined!");
            return;
        }

        Utils.start(runPath.resolve(instanceInfo.runnablePath), args);
    }
}
package eu.pb4.mrpackserver.format;

import eu.pb4.mrpackserver.util.Utils;

import java.util.HashMap;
import java.util.Map;

public class InstanceInfo {
    public String projectId = "";
    public String versionId = "";
    public String runnablePath = "";

    public boolean forceSystemClasspath = false;

    public Map<String, String> dependencies = new HashMap<>();

    public static InstanceInfo read(String s) {
        return Utils.GSON_MAIN.fromJson(s, InstanceInfo.class);
    }
}

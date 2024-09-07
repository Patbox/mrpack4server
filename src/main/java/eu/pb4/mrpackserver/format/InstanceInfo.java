package eu.pb4.mrpackserver.format;

import java.util.HashMap;
import java.util.Map;

public class InstanceInfo {
    public String projectId = "";
    public String versionId = "";
    public String runnablePath = "";
    public Map<String, String> dependencies = new HashMap<>();
}

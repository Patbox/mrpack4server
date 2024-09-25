package eu.pb4.mrpackserver.format;

import eu.pb4.mrpackserver.util.Utils;

import java.util.List;
import java.util.Map;

public class VanillaVersionList {
    public Map<String, String> latest;
    public List<Version> versions = List.of();

    public static VanillaVersionList read(String s) {
        return Utils.GSON.fromJson(s, VanillaVersionList.class);
    }
    public static class Version {
        public String id = "";
        public String type = "";
        public String url = "";
    }
}

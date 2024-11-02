package eu.pb4.mrpackserver.format;

import eu.pb4.mrpackserver.util.Utils;

import java.util.Map;

public class VanillaVersionData {
    public Map<String, File> downloads = Map.of();

    public static VanillaVersionData read(String s) {
        return Utils.GSON_MAIN.fromJson(s, VanillaVersionData.class);
    }

    public static class File {
        public String sha1 = "";
        public long size = -1;
        public String url;
    }
}

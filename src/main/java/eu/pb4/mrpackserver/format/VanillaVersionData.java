package eu.pb4.mrpackserver.format;

import java.util.Map;

public class VanillaVersionData {
    public Map<String, File> downloads = Map.of();


    public static class File {
        public String sha1 = "";
        public long size = -1;
        public String url;
    }
}

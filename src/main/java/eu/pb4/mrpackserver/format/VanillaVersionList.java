package eu.pb4.mrpackserver.format;

import java.util.List;
import java.util.Map;

public class VanillaVersionList {
    public Map<String, String> latest;
    public List<Version> versions = List.of();


    public static class Version {
        public String id = "";
        public String type = "";
        public String url = "";
    }
}

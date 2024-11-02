package eu.pb4.mrpackserver.format;

import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.Utils;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

public class ModpackIndex {
    public int formatVersion = 1;
    public String game = Constants.MINECRAFT;
    public String versionId = "";
    public String name = "";
    public String summary = "";
    public List<FileEntry> files = List.of();
    public HashMap<String, String> dependencies = new HashMap<>();

    public static ModpackIndex read(String s) {
        return Utils.GSON_MAIN.fromJson(s, ModpackIndex.class);
    }
    public static class FileEntry {
        public String path = "";
        public HashMap<String, String> hashes = new HashMap<>();
        public HashMap<String, String> env = new HashMap<>();
        public List<URI> downloads = List.of();
        public long fileSize = 0;
    }
}

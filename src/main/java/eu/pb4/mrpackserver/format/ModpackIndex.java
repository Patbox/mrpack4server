package eu.pb4.mrpackserver.format;

import eu.pb4.mrpackserver.util.Constants;

import java.net.URI;
import java.net.URL;
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
    public static class FileEntry {
        public String path = "";
        public HashMap<String, String> hashes = new HashMap<>();
        public HashMap<String, String> env = new HashMap<>();
        public List<URI> downloads = List.of();
        public long fileSize = 0;
    }
}

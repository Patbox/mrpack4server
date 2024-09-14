package eu.pb4.mrpackserver.format;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModrinthModpackVersion {
    public static final TypeToken<List<ModrinthModpackVersion>> TYPE = new TypeToken<>() {};

    public String name = "";
    @SerializedName("version_number")
    public String versionNumber = "";

    @SerializedName("version_type")
    public String versionType = "";
    public String id = "";

    public List<File> files = List.of();

    public static class File {
        public Map<String, String> hashes = new HashMap<>();
        public long size = -1;

        public URI url;
        public boolean primary = false;
    }
}

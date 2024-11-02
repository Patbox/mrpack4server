package eu.pb4.mrpackserver.format;

import com.google.gson.annotations.SerializedName;
import eu.pb4.mrpackserver.util.Utils;

import java.util.List;

public class ModrinthSearchData {
    @SerializedName("hits")
    public List<Project> hits = List.of();

    public static ModrinthSearchData read(String s) {
        return Utils.GSON_MAIN.fromJson(s, ModrinthSearchData.class);
    }


    public static class Project {
        @SerializedName("slug")
        public String slug = "";
        @SerializedName("title")
        public String title = "";

        @SerializedName("author")
        public String author = "";
    }
}

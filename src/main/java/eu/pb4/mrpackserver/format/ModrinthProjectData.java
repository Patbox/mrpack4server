package eu.pb4.mrpackserver.format;

import com.google.gson.annotations.SerializedName;
import eu.pb4.mrpackserver.util.Utils;

public class ModrinthProjectData {
    @SerializedName("slug")
    public String slug = "";
    @SerializedName("title")
    public String title = "";
    @SerializedName("project_type")
    public String projectType = "";

    @SerializedName("description")
    public String description = "";

    public static ModrinthProjectData read(String s) {
        return Utils.GSON_MAIN.fromJson(s, ModrinthProjectData.class);
    }
}

package eu.pb4.mrpackserver.format;

import com.google.gson.annotations.SerializedName;
import eu.pb4.mrpackserver.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ModpackInfo {
    @Nullable
    @SerializedName("display_name")
    public String displayName;
    @Nullable
    @SerializedName("display_version")
    public String displayVersion;
    @SerializedName("project_id")
    public String projectId = "";
    @SerializedName("version_id")
    public String versionId = "";
    @SerializedName("whitelisted_domains")
    public List<String> whitelistedDomains = new ArrayList<>();
    @Nullable
    public String sha512 = null;
    @Nullable
    public URI url = null;
    @Nullable
    public Long size = null;

    public boolean isValid() {
        return !this.projectId.isBlank() && !this.versionId.isBlank();
    }

    public String getDisplayName() {
        return this.displayName != null ? this.displayName : this.projectId;
    }

    public String getDisplayVersion() {
        return this.displayVersion != null ? this.displayVersion : this.versionId;
    }

    public static ModpackInfo read(String s) {
        return Utils.GSON_MAIN.fromJson(s, ModpackInfo.class);
    }

    public String toJson() {
        return Utils.GSON_PRETTY.toJson(this);
    }
}

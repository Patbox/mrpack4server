package eu.pb4.mrpackserver.util;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record DownloadMeta(Reason reason, String version, String loader, @Nullable String dependentVersionId) {
    public enum Reason {
        STANDALONE,
        DEPENDENCY,
        MODPACK,
        UPDATE
    }

    public String toString() {
        var obj = new JsonObject();
        obj.addProperty("reason", this.reason.name().toLowerCase(Locale.ROOT));
        obj.addProperty("game_version", this.version);
        obj.addProperty("loader", this.loader);
        if (this.dependentVersionId != null && !this.dependentVersionId.isEmpty()) {
            obj.addProperty("dependent_on", this.dependentVersionId);
        }
        return obj.toString();
    }
}

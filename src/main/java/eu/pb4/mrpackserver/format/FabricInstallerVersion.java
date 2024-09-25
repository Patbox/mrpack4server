package eu.pb4.mrpackserver.format;

import com.google.gson.reflect.TypeToken;
import eu.pb4.mrpackserver.util.Utils;

import java.util.List;

public class FabricInstallerVersion {
    private static final TypeToken<List<FabricInstallerVersion>> TYPE = new TypeToken<>() {};
    public String url = "";
    public String maven = "";
    public String version = "";
    public boolean stable = false;

    public static List<FabricInstallerVersion> read(String s) {
        return Utils.GSON.fromJson(s, TYPE);
    }
}

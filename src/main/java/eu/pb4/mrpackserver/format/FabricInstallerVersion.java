package eu.pb4.mrpackserver.format;

import com.google.gson.reflect.TypeToken;

import java.util.List;

public class FabricInstallerVersion {
    public static final TypeToken<List<FabricInstallerVersion>> TYPE = new TypeToken<>() {};
    public String url = "";
    public String maven = "";
    public String version = "";
    public boolean stable = false;
}

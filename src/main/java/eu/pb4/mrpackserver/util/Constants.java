package eu.pb4.mrpackserver.util;

import java.util.List;
import java.util.Set;

public class Constants {
    public static final String FABRIC = "fabric-loader";
    public static final String QUILT = "quilt-loader";
    public static final String FORGE = "forge";
    public static final String NEOFORGE = "neoforge";
    public static final String MINECRAFT = "minecraft";
    public static final String HASH = "sha512";


    public static final String USER_AGENT;
    public static final String DATA_FOLDER = ".mrpack4server";

    public static final String FABRIC_INSTALLER_VERSIONS = "https://meta.fabricmc.net/v2/versions/installer";
    public static final List<String> OVERWRITES = List.of("overrides", "server_overrides");
    public static final List<String> NON_OVERWRITABLE = List.of("server.properties", "world", DATA_FOLDER);

    public static final Set<String> WHITELISTED_URLS = Set.of(
            "cdn.modrinth.com",
            "github.com",
            "raw.githubusercontent.com",
            "gitlab.com"
    );
    public static final String LOG_PREFIX = "[mrpack4server] ";
    public static final String LOG_WARN_PREFIX = "[mrpack4server | WARN] ";
    public static final String LOG_ERROR_PREFIX = "[mrpack4server | ERROR] ";

    static {
        var x = Constants.class.getPackage();
        USER_AGENT = x.getImplementationTitle() + " v" + x.getImplementationVersion() + "(" + x.getImplementationVendor() + ")";
    }
}

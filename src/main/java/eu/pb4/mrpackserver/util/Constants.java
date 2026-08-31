package eu.pb4.mrpackserver.util;

import eu.pb4.mrpackserver.format.ModpackInfo;

import java.util.List;
import java.util.Set;

public class Constants {
    public static final String FABRIC = "fabric-loader";
    public static final String QUILT = "quilt-loader";
    public static final String FORGE = "forge";
    public static final String NEOFORGE = "neoforge";
    public static final String MINECRAFT = "minecraft";

    public static final List<String> MOD_LOADERS = List.of(FABRIC, QUILT, FORGE, NEOFORGE);

    public static final String MODRINTH_HASH = "sha512";
    public static final String DEFAULT_HASH = "SHA-512";

    public static final String USER_AGENT;
    public static final int DOWNLOAD_PARRALEL_CLIENTS = Integer.parseInt(System.getProperty("mrpack4server.download.clients", "5"));
    public static final int DOWNLOAD_UPDATE_TIME = Integer.parseInt(System.getProperty("mrpack4server.download.update_time", "1500"));
    public static final int DOWNLOAD_CHUNK_SIZE = Integer.parseInt(System.getProperty("mrpack4server.download.chunk_size", "512"));
    public static final int DOWNLOAD_TIMEOUT = Integer.parseInt(System.getProperty("mrpack4server.download.timeout", "30"));
    public static final String DATA_FOLDER = ".mrpack4server";
    public static final String CUSTOM_NON_OVERWRITABLE_LIST = "lockedpaths.txt";

    public static final String FABRIC_INSTALLER_VERSIONS = "https://meta.fabricmc.net/v2/versions/installer";
    public static final String MODRINTH_API = "https://api.modrinth.com/v2";
    public static final String MODRINTH_API_VERSIONS = MODRINTH_API + "/project/{PROJECT_ID}/version";
    public static final List<String> OVERWRITES = List.of("/overrides", "/server-overrides");
    public static final List<String> DEFAULT_NON_OVERWRITABLE = List.of(
            "server.properties",
            "world",
            "whitelist.json",
            "banned-ips.json",
            "banned-players.json",
            "ops.json",
            DATA_FOLDER,
            CUSTOM_NON_OVERWRITABLE_LIST
    );

    public static final Set<String> DEFAULT_WHITELISTED_URLS = Set.of(
            "cdn.modrinth.com",
            "github.com",
            "raw.githubusercontent.com",
            "gitlab.com"
    );
    public static final String LOG_PREFIX = "[mrpack4server] ";
    public static final String LOG_PREFIX_SMALL = "";
    public static final String LOG_WARN_PREFIX = "[mrpack4server | WARN] ";
    public static final String LOG_WARN_PREFIX_SMALL = "[WARN] ";
    public static final String LOG_ERROR_PREFIX = "[mrpack4server | ERROR] ";
    public static final String LOG_ERROR_PREFIX_SMALL = "[ERROR] ";
    public static final int SEARCH_QUERY_MAX_SIZE = 20;

    static {
        String extraFlavor = "";
        ModpackInfo modpackInfo;
        try {
            modpackInfo = Utils.resolveModpackInfoInternal();

            if (modpackInfo != null) {
                if (modpackInfo.internalFlavor != null) {
                    extraFlavor = " / " + modpackInfo.internalFlavor;
                } else if (modpackInfo.isValid()) {
                    extraFlavor = " / conf: " + modpackInfo.getDisplayName() + " ver: " + modpackInfo.getDisplayVersion();
                }
            }
        } catch (Throwable throwable) {
            // ignored
        }

        var x = Constants.class.getPackage();
        USER_AGENT = x.getImplementationTitle() + " v" + x.getImplementationVersion() + " (" + x.getImplementationVendor() + extraFlavor + ")";
    }
}

# mrpack4server
mrpack4server is a "server launcher" that allows you to easily install and run any modpack from Modrinth
(or one that's exported in `.mrpack` format) as a Minecraft Server on your local machine or any hosting provider that supports custom jars.
This tool doesn't require any additional arguments and can work as any other server jar (like vanilla provided one).

## Features:
- For any users, usable standalone, for setup of any modpack by defining a single file.
- For modpack makers, allowing quick server setup by having to just download and run a single file.
- Automatically downloads required mrpack files and any mods / external assets defined by modpack.
- Automatically downloads and starts the server/modloader files, without requiring renaming of jars, supporting Fabric Loader, 
Quilt Loader, Forge and NeoForge.
If used with modpack for other unsupported platforms, it will still install everything, but won't be able to launch.

## Usage:
The file is run just like any other Minecraft server (`java -jar mrpack4server.jar`) and will use / pass
through any arguments given to it. When used on its own, it looks in 3 places for modpack definition
with provided order:
- `modpack-info.json` within jar itself, useful for modpack makers. See below for definition,
- `modpack-info.json` within server's root directory, for users to simple setup,
- `local.mrpack` within server's root directory, making it directly use provided mrpack file instead of 
pulling it from Modrinth.

If neither of these is found, it will ask you to either provide link, project id or name of the modpack you want to install
and then the version of it (suggesting latest one), which will be used to create local `modpack-info.json` file. 
You can then either update target version by editing it or by removing the file and rerunning the initial setup.

Default/main jar only supports Java 21 (`mrpack4server-X.Y.Z.jar`). If you want to run it on older Java version you can use:
- `mrpack4server-X.Y.Z-jvm8.jar` for Java 8 and above,
- `mrpack4server-X.Y.Z-jvm16.jar` for Java 16 and above,
- `mrpack4server-X.Y.Z-jvm17.jar` for Java 17 and above.

These versions however don't override requirements of Minecraft/Loader itself. 
For example, you still need Java to use Java 17 or above to run Minecraft 1.20.1 and Java 8 (but not newer!) to run Forge 1.16.5.

You can create bundled variant by hand or by running `java -cp mrpack4server.jar eu.pb4.mrpackserver.Create`.
By default, without any arguments, it will copy currently provided `modpack-info.json` file, but you can also set it with arguments (`--arg value`),
where it mirrors all arguments from `modpack-info.json` (aka `--project_id my_modpack --version_id 1.2.3` will create jar with these defined).
Additionally, you can use the `--out` argument to set output file path, by default being set to `--out server.jar`.

### `modpack-info.json` format:
`modpack-info.json` is a regular json file without support for comments. Ones provided below are purely
to make easier to describe things.
```json5
{
  // (Optional) Display name, used to display as information while starting / download files.
  "display_name": "My Modpack",
  // (Optional) Display version, used to display as information while starting / download files.
  "display_version": "1.0.0 for 1.21.1",
  // Project id used on Modrinth and locally, identifying modpack as unique. Can use slug or ID
  "project_id": "my_modpack_by_patbox",
  // Version id used on Modrinth and locally, identifying used version. Can be a version number, version id or prefixed version type.
  // As version type, you can set it to ";;release", ";;beta" or ";;alpha", making it download latest version with highest
  // version number! For most use cases, I would recommend not using this functionality, unless you are 100% modpack's version is consistent
  // and non-hard breaking. For stability, you should use version numbers directly.
  "version_id": "1.0.0",
  // (Optional) Overrides url used to download the modpack, can download it from anywhere. 
  "url": "https://files.pb4.eu/modpacks/my_modpack.mrpack",
  // (Optional) Size of the file downloaded from "url", in bytes. It's not required even with "url" used.
  "size": 1000,
  // (Optional) Value of sha512 hash for file downloaded from "url", used for validation. It's not required even with "url" used.
  "sha512": 1000,
  // (Optional) Additional list of whitelisted domains, only useful for modpacks hosted outside Modrinth.
  "whitelisted_domains": [
    "files.pb4.eu" // Note it's just a domain, no protocol/ports/paths.
  ],
  // (Optional) Allows to url used for requesting list of available versions, used by auto-updating feature (the ;; versions).
  "version_list_url": "https://api.modrinth.com/v2/project/{PROJECT_ID}/versions"
}
```

Examples:
- Installing Adrenaserver version 1.7.0+1.21.1.fabric.
```json
{
  "project_id": "adrenaserver",
  "version_id": "1.7.0+1.21.1.fabric"
}
```
- Installing Cobblemon Official Modpack v1.5.2 (using id's copied from website).
```json
{
  "project_id": "5FFgwNNP",
  "version_id": "bpaivauC"
}
```

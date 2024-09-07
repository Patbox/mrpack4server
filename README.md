# mrpack4server
mrpack4server is a "server launcher" that allows you to easily load and run any modpack from Modrinth
(or just using `.mrpack` format) as a Minecraft Server. This tool doesn't require any additional arguments
and can work as any other server jar (like vanilla provided one).

## Features:
- For any users, usable standalone, for setup of any modpack by defining a single file.
- For modpack makers, allowing quick server setup by having to just download and run a single file.
- Automatically downloads required mrpack files and any mods / external assets defined by modpack.
- (Fabric Loader only) Automatically downloads and starts the server, without requiring swapping of jars.
If used with modpack for other platforms, it will still install everything, but won't be able to launch.

## Usage:
The file is run just like any other Minecraft server (`java -jar mrpack4server.jar`) and will use / pass
through any arguments given to it. When used on its own, it looks in 3 places for modpack definition
with provided order:
- `modpack-info.json` within jar itself, useful for modpack makers. See below for definition,
- `modpack-info.json` within server's root directory, for users to simple setup,
- `local.mrpack` within server's root directory, making it directly use provided mrpack file instead of 
pulling it from Modrinth.


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
  // Version id used on Modrinth and locally, identifying used version. Can be a version number or version id.  
  "version_id": "1.0.0",
  // (Optional) Overrides url used to download the modpack, can download it from anywhere. 
  "url": "https://files.pb4.eu/modpacks/my_modpack.mrpack",
  // (Optional) Additional list of whitelisted domains, only useful for modpacks hosted outside Modrinth.
  "whitelisted_domains": [
    "files.pb4.eu" // Note it's just a domain, no protocol/ports/paths.
  ]
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

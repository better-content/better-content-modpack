# Local toolchain inventory

## Scope

This is the single user-managed tool inventory for the `/home/dev` Better Content workspace.
Installed SDKs/tools are operating inputs, not disposable dependency caches. Use repository Gradle
wrappers and pinned hashes; a global Gradle installation does not replace them.

| Tool | Resolved version | Installation/location | Activation/reproduction |
|---|---|---|---|
| Java | Temurin `17.0.16-tem` | SDKMAN, `$HOME/.sdkman/candidates/java/17.0.16-tem` | `source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java 17.0.16-tem` |
| Kotlin | `2.2.21` | SDKMAN, `$HOME/.sdkman/candidates/kotlin/2.2.21` | `source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use kotlin 2.2.21` |
| Packwiz | `v0.0.0-20260906154125-ef87d964f8cb` | Go install, `$HOME/.local/bin/packwiz` | `GOBIN="$HOME/.local/bin" go install github.com/packwiz/packwiz@v0.0.0-20260906154125-ef87d964f8cb` |
| Vineflower | `1.10.1` | `$HOME/.local/share/tools/vineflower.jar`; launcher `$HOME/.local/bin/vineflower` | Launcher uses SDKMAN current Java |
| Codex CLI | `0.163.0` | npm user prefix `$HOME/.local`; launcher `$HOME/.local/bin/codex` | `npm install -g --prefix "$HOME/.local" @openai/codex@0.163.0`; lane launcher keeps temporary files under `$HOME/.tmp/codex`, defaults `CODEX_HOME` to `$HOME/.local/share/codex-adhoc`; use `--no-daemon` |
| Pi coding agent | `1.3.0` | npm bundle `$HOME/.local/share/worklane/pi-bundle`; `$HOME/.local/bin/pi` resolves through `$HOME/.local/lib/node_modules/@earendil-works/pi-coding-agent` | `npm install --prefix "$HOME/.local/share/worklane/pi-bundle" --no-save --package-lock=false --ignore-scripts @earendil-works/pi-coding-agent@1.3.0`; verify with `zsh -lic 'command -v pi && pi --version'` |
| Flite narrator runtime | Debian `libflite1` `2.2-7`, amd64 | `$HOME/.local/share/minecraft-native/libflite1-2.2-7` | Extract the pinned package below without installation/system writes; pass its `usr/lib/x86_64-linux-gnu` directory through `LD_LIBRARY_PATH` |

`$HOME/.local/bin/kotlin` and `kotlinc` resolve through SDKMAN's current candidate, so cleanup does
not need to download Kotlin. Tool installation and fresh-login verification conventions belong
in the global lane guide. Keep this inventory current after SDK changes.

## Native narrator package

Flite is extracted from [Debian's libflite1 package](https://deb.debian.org/debian/pool/main/f/flite/libflite1_2.2-7_amd64.deb),
SHA-256 `5945190970be7d4a1f71fb6f7e3e340ba2675e770605df0754051d617a6c5ee6`, using
`dpkg-deb --extract` into the inventory location. Within its library directory, relative aliases
`libflite.so -> libflite.so.1` and `libflite_cmu_us_kal16.so -> libflite_cmu_us_kal16.so.1`
allow Mojang's pinned `text2speech` JNA binding to resolve the native libraries. This is a local
runtime SDK, not a system package installation. Native initialization is separate from playback
or gameplay-audio acceptance.

## Related guidance

- [Workspace policy](policies/workspace.md)
- [Dependency/provider bootstrap](custom-mod-workspace.md)
- [Disposable caches](policies/generated-data.md)

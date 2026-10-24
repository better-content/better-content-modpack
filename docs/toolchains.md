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

## Nonshipping native Forge API SDK

Compat's sixth `native-forge-api` profile uses Forge **47.4.13** / Java 17; the production pack uses
Forge **47.4.22**. These are explicit, separate qualified roles, not interchangeable version claims.
The owning wrapper and `gradle/native-forge-api-fixture.gradle` derive the current private worker's
Minecraft 1.20.1 official mappings through ordinary Forge compilation. Forge `installertools 1.4.4`
(`SRG_TO_MCP`) is a pinned build dependency, not a new global executable or host installation.

`prepare_native_forge_api_sdk.py` binds the tool SHA-256
`3de0f0899c060b6f5aa79d4d9d79063ca9029cfefa19e792cb222782f754184d` and mapping ZIP SHA-256
`84e2278aeeabd496194dbe2fa08031be58bad277d7e3aee036b79d9222d8cbc7`, recursively maps the exact
resource-corrected FFAPI root plus nested modules and Moonlight, and retains originals, snapshots,
outputs, receipts and failed attempts under the current Gradle home's
`bettercontent-local-sdk/native-forge-api-v1/`. Lazy file dependencies wait for this producer; no
configuration-time classpath resolution or global/historical MCP-cache fallback is supported.

Mapped runtime roots are FFAPI SHA-256
`447b9e285717e66e95924e190a13f5a2079b969af70ed4c6577d40cad9423351` and Moonlight SHA-256
`ec130f96cb65ce97d600880127322656021bd5c6e60a976de9eb9403e5f3d5a3`. Only that profile loads these
whole mapped roots. Extracted base/interaction/resource modules are compile-only, never duplicate
runtime mods. Raw production archives and the existing correction recipes remain unchanged. These
local-only SDKs are operating inputs to actual integration tests, not public redistribution grants
or reusable historical runtime acceptance.

## Related guidance

- [Workspace policy](policies/workspace.md)
- [Dependency/provider bootstrap](custom-mod-workspace.md)
- [Disposable caches](policies/generated-data.md)

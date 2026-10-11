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

`$HOME/.local/bin/kotlin` and `kotlinc` resolve through SDKMAN's current candidate, so cleanup does
not need to download Kotlin. Tool installation and fresh-login verification conventions belong
in the global lane guide. Keep this inventory current after SDK changes.

## Related guidance

- [Workspace policy](policies/workspace.md)
- [Dependency/provider bootstrap](custom-mod-workspace.md)
- [Disposable caches](policies/generated-data.md)

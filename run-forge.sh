#!/usr/bin/env sh
# Raw Forge diagnostic launcher. Normal server operation must use run.sh.
set -eu
[ "${BC_WLM_SUPERVISED:-}" = 1 ] || {
  printf 'direct run-forge.sh startup is blocked; use ./run.sh so World Lifecycle Manager can supervise the server\n' >&2
  exit 1
}
[ -n "${BC_JAVA:-}" ] && [ -x "$BC_JAVA" ] || {
  printf 'supervised Forge startup failed: BC_JAVA is not an executable Java 17 path\n' >&2
  exit 1
}
exec "$BC_JAVA" @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.4.22/unix_args.txt "$@"

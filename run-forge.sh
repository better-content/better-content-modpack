#!/usr/bin/env sh
# Forge launcher invoked by run.sh. Direct startup remains blocked.
set -eu
[ "${BC_PACK_LAUNCH:-}" = 1 ] || {
  printf 'direct run-forge.sh startup is blocked; use ./run.sh\n' >&2
  exit 1
}
[ -n "${BC_JAVA:-}" ] && [ -x "$BC_JAVA" ] || {
  printf 'Forge startup failed: BC_JAVA is not an executable Java 17 path\n' >&2
  exit 1
}
exec "$BC_JAVA" @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.4.22/unix_args.txt "$@"

#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
if [[ -n "${BC_JAVA:-}" ]]; then
  JAVA_CANDIDATE="$BC_JAVA"
elif [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA_CANDIDATE="$JAVA_HOME/bin/java"
else
  JAVA_CANDIDATE="$(command -v java 2>/dev/null || true)"
fi
[[ -n "$JAVA_CANDIDATE" && -x "$JAVA_CANDIDATE" ]] || {
  printf 'server startup failed: Java was not found; set BC_JAVA or JAVA_HOME to a Java 17 installation\n' >&2
  exit 1
}
BC_JAVA="$(readlink -f -- "$JAVA_CANDIDATE")"
JAVA_MAJOR="$("$BC_JAVA" -version 2>&1 | awk -F'[\".]' '/version/ { if ($2 == "1") print $3; else print $2; exit }')"
[[ "$JAVA_MAJOR" == 17 ]] || {
  printf 'server startup failed: Java 17 is required; %s reports major version %s\n' "$BC_JAVA" "${JAVA_MAJOR:-unknown}" >&2
  exit 1
}
export BC_JAVA
if [[ "$#" -eq 0 ]]; then
  set -- nogui
fi
exec "$SCRIPT_DIR/better-world-management-server.sh" "$@"

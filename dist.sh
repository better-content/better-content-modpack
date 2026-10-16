#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if (($# != 0)) && ! { (($# == 2)) && [[ "$1" == --untested-suffix && "$2" =~ ^[a-z]$ ]]; }; then
  printf 'dist failed: output is fixed at %s/dist; usage: ./dist.sh [--untested-suffix a..z]\n' "$ROOT" >&2
  exit 1
fi
exec "$ROOT/package.sh" dist "$@"

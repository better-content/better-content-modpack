#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MC_VERSION=1.20.1
FORGE_VERSION=47.4.13
FORGE_COORD="$MC_VERSION-$FORGE_VERSION"
fail() { printf 'package failed: %s\n' "$*" >&2; exit 1; }

copy_content() {
  local target="$1" path
  mkdir -p "$target"
  for path in config defaultconfigs datapacks defaultresources globalresources kubejs mods resourcepacks shaderpacks tacz options.txt; do
    [ ! -e "$ROOT/$path" ] || cp -a "$ROOT/$path" "$target/"
  done
}

resolve_artifacts() {
  local cache_root="${BC_PACKAGE_ARTIFACT_CACHE:-$HOME/.cache/bc/packwiz-downloads}"
  local manifest_root="${3:-$ROOT}"
  python3 - "$manifest_root" "$1" "$2" "$cache_root" <<'PY'
import fcntl, fnmatch, hashlib, os, pathlib, shutil, sys, tempfile, tomllib, urllib.request
root, target, side, cache_root = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3], pathlib.Path(sys.argv[4]).expanduser()
client_only = ('ambientsounds*','bettergrassify*','configured*','controlling*','DistantHorizons*','embeddium*','entityculling*','hold-my-items*','mouse-tweaks*','no-more-popups*','no-recipe-book*','oculus*','presence-footsteps*','shoulder-surfing*','sound-physics*','the-one-probe*','true-darkness*','darkness*')
stats = {'hits': 0, 'downloads': 0, 'uncached': 0}

def new_hasher(algorithm, manifest):
    try:
        return hashlib.new(algorithm)
    except ValueError as error:
        raise SystemExit(f"unsupported hash algorithm for {manifest}: {algorithm}") from error

def file_digest(path, algorithm, manifest):
    digest = new_hasher(algorithm, manifest)
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()

def download(url, destination, algorithm=None, manifest=None):
    digest = new_hasher(algorithm, manifest) if algorithm else None
    request = urllib.request.Request(url, headers={'User-Agent':'better-content-packager/1.0'})
    with urllib.request.urlopen(request, timeout=120) as response, destination.open('wb') as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
            if digest:
                digest.update(chunk)
    return digest.hexdigest() if digest else None

def materialize(source, destination):
    if destination.exists() or destination.is_symlink():
        destination.unlink()
    try:
        os.link(source, destination)
    except OSError:
        if destination.exists() or destination.is_symlink():
            destination.unlink()
        shutil.copy2(source, destination)

manifest_files = []
for folder in ('mods','resourcepacks','shaderpacks','tacz'):
    manifest_files.extend(sorted((root / folder).glob('*.pw.toml')))
classified_paths = set(manifest_files)
unclassified = sorted(path for path in root.rglob('*.pw.toml') if path not in classified_paths)
if unclassified:
    raise SystemExit("unclassified Packwiz manifests: " + ', '.join(str(path) for path in unclassified))

for manifest in manifest_files:
        data = tomllib.loads(manifest.read_text())
        if data.get('side', 'both') not in ('both', side):
            continue
        name = data.get('filename', '')
        if not name or pathlib.PurePath(name).name != name:
            raise SystemExit(f"invalid artifact filename for {manifest}: {name!r}")
        if side == 'server' and any(fnmatch.fnmatch(name.lower(), pattern.lower()) for pattern in client_only):
            continue
        download_data = data.get('download', {})
        url = download_data.get('url')
        if not url and download_data.get('mode') == 'metadata:curseforge':
            cf = data.get('update', {}).get('curseforge', {})
            url = f"https://www.curseforge.com/api/v1/mods/{cf['project-id']}/files/{cf['file-id']}/download"
        if not url:
            raise SystemExit(f"manifest has no resolvable download URL: {manifest}")
        destination = target / folder / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        algorithm = download_data.get('hash-format', '').replace('-', '').lower()
        expected = download_data.get('hash', '').lower()
        if algorithm and expected:
            cache = cache_root / folder / name
            cache.parent.mkdir(parents=True, exist_ok=True)
            lock_path = cache.with_name(f'.{cache.name}.lock')
            with lock_path.open('a+b') as lock:
                fcntl.flock(lock, fcntl.LOCK_EX)
                if cache.is_file() and file_digest(cache, algorithm, manifest) == expected:
                    stats['hits'] += 1
                else:
                    descriptor, temporary_name = tempfile.mkstemp(prefix=f'.{cache.name}.', suffix='.tmp', dir=cache.parent)
                    os.close(descriptor)
                    temporary = pathlib.Path(temporary_name)
                    try:
                        actual = download(url, temporary, algorithm, manifest)
                        if actual != expected:
                            raise SystemExit(f"hash mismatch for {manifest}: expected {expected}, got {actual}")
                        os.replace(temporary, cache)
                        stats['downloads'] += 1
                    finally:
                        temporary.unlink(missing_ok=True)
            materialize(cache, destination)
        else:
            temporary = destination.with_name(f'.{destination.name}.tmp')
            temporary.unlink(missing_ok=True)
            download(url, temporary)
            os.replace(temporary, destination)
            stats['uncached'] += 1
        if not destination.is_file():
            raise SystemExit(f"resolved artifact is missing beside manifest {manifest}: {destination}")
        if not algorithm or not expected:
            raise SystemExit(f"manifest must declare a digest: {manifest}")
        actual = file_digest(destination, algorithm, manifest)
        if actual != expected:
            raise SystemExit(f"resolved artifact hash mismatch for {manifest}: expected {expected}, got {actual}")

print(f"artifact cache: side={side} hits={stats['hits']} downloads={stats['downloads']} uncached={stats['uncached']}")
PY
}

stage_side() {
  local side="$1" target="$2"
  copy_content "$target"
  if [ "$side" = server ] && [ -d "$ROOT/server-config/config-overrides" ]; then
    mkdir -p "$target/config"
    cp -a "$ROOT/server-config/config-overrides/." "$target/config/"
  fi
  resolve_artifacts "$target" "$side"
}

install_server() {
  local server="$1" accept_eula="$2" profile="$3" port="${4:-25565}" properties
  local installer="$ROOT/forge-$FORGE_COORD-installer.jar"
  [ -f "$installer" ] || fail "missing Forge installer: $installer"
  case "$profile" in
    testing) properties="$ROOT/server-config/testing.properties" ;;
    production) properties="$ROOT/server-config/production.properties" ;;
    *) fail "unknown server profile: $profile" ;;
  esac
  local server_cache="${BC_PACKAGE_SERVER_CACHE:-$HOME/.cache/bc/smoke/server}"
  if [ -f "$server_cache/libraries/net/minecraftforge/forge/$FORGE_COORD/unix_args.txt" ]; then
    cp -al "$server_cache/libraries" "$server/"
  else
    cp "$installer" "$server/"
    (cd "$server" && java -jar "$(basename "$installer")" --installServer)
  fi
  printf 'eula=%s\n' "$accept_eula" > "$server/eula.txt"
  cp "$properties" "$server/server.properties"
  sed -i -E "s/^server-port=.*/server-port=$port/" "$server/server.properties"
  if [ "$profile" = production ]; then
    cp "$ROOT/user_jvm_args.txt" "$server/user_jvm_args.txt"
  else
    printf '%s\n' '-Xms2G' '-Xmx8G' '-XX:+UseG1GC' '-Dfile.encoding=UTF-8' > "$server/user_jvm_args.txt"
  fi
  cp "$ROOT/run.sh" "$ROOT/run-forge.sh" "$ROOT/world-lifecycle-manager-server.sh" "$server/"
  chmod 0755 "$server/run.sh" "$server/run-forge.sh" "$server/world-lifecycle-manager-server.sh"
}

package_runtime() {
  (($# == 3)) || fail 'usage: package.sh runtime SERVER_DIR CLIENT_DIR PORT'
  command -v java >/dev/null || fail 'java is required'
  command -v python3 >/dev/null || fail 'python3 is required'
  stage_side server "$1"
  stage_side client "$2"
  install_server "$1" true testing "$3"
}

archive_server_tree() {
  local server_dir="$1" tree="$1/server-tree"
  (cd "$tree" && zip -q -r "$server_dir/better-content.zip" better-content-server)
  rm -rf -- "$tree"
}

package_dist() {
  (($# == 0)) || fail 'distribution output is fixed at the repository dist/ directory'
  for command in java packwiz python3 zip; do command -v "$command" >/dev/null || fail "$command is required"; done
  local out_root="$ROOT/dist"
  local current_version current_build next_build version version_dir_name release_dir client_dir server_dir stage
  current_version="$(awk -F ' *= *' '$1 == "version" { value=$2; gsub(/^"|"$/, "", value); print value; exit }' "$ROOT/pack.toml")"
  current_build="$(printf '%s' "$current_version" | sed -nE 's/^.*[^0-9]([0-9]+)$/\1/p')"
  [ -n "$current_build" ] || fail 'pack version must end in a numeric build number'
  next_build=$((10#$current_build + 1))
  version="$(printf '%s' "$current_version" | sed -E "s/[0-9]+$/$next_build/")"
  version_dir_name="$(printf '%s' "$version" | tr '[:upper:] ' '[:lower:]-' | tr -cd 'a-z0-9._-')"
  if [ -e "$out_root" ]; then
    find "$out_root" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  else
    mkdir -p "$out_root"
  fi
  out_root="$(cd "$out_root" && pwd)"
  release_dir="$out_root/$version_dir_name"
  [ ! -e "$release_dir" ] || fail "version directory already exists: $release_dir"
  client_dir="$release_dir/client"
  server_dir="$release_dir/server"
  stage="$server_dir/server-tree/better-content-server"
  local escaped_version
  escaped_version="$(printf '%s' "$version" | sed 's/[&|\\]/\\&/g')"
  sed -i -E "0,/^version *=/{s|^version *=.*$|version = \"$escaped_version\"|}" "$ROOT/pack.toml"
  mkdir -p "$client_dir" "$stage"
  (cd "$ROOT" && packwiz curseforge export -o "$client_dir/better-content.zip" -s client -y)
  stage_side server "$stage"
  install_server "$stage" false production 25565
  cat > "$stage/SERVER_README.txt" <<'TXT'
Better Content server distribution

This archive ships the production server profile: authenticated online mode, normal
terrain for new worlds, the standard port on all network interfaces, a 20-player cap,
10-chunk view and simulation distances, command blocks disabled, and 16-block spawn
protection. Edit server.properties after extraction for deployment-specific settings.
No player identity state is preloaded: the archive contains no user cache, operator,
allowlist, ban-list, world, player-data, advancement, statistics, or UUID-mapping files.
Authenticated accounts establish their own profiles when they first join.

The packaged JVM baseline reserves 4 GiB and permits up to 16 GiB. Adjust
user_jvm_args.txt for the host's available memory. Set eula=true only after accepting
Mojang's EULA. This archive is packaging output and carries no validation,
verification, compatibility, or runtime-health claim.
TXT
  archive_server_tree "$server_dir"
  printf 'version: %s\nclient: %s\nserver: %s\n' "$version" "$client_dir/better-content.zip" "$server_dir/better-content.zip"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  case "${1:-}" in
    runtime) shift; package_runtime "$@" ;;
    resolve)
      shift
      (($# == 3)) || fail 'usage: package.sh resolve MANIFEST_ROOT TARGET SIDE'
      resolve_artifacts "$2" "$3" "$1"
      ;;
    dist) shift; package_dist "$@" ;;
    *) fail 'usage: package.sh <runtime SERVER_DIR CLIENT_DIR PORT|resolve MANIFEST_ROOT TARGET SIDE|dist>' ;;
  esac
fi

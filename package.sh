#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MC_VERSION=1.20.1
FORGE_VERSION=47.4.22
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
client_only = ('ambientsounds*','bettergrassify*','configured*','controlling*','DistantHorizons*','embeddium*','entityculling*','hold-my-items*','mouse-tweaks*','no-more-popups*','no-recipe-book*','oculus*','presence-footsteps*','shoulder-surfing*','sound-physics*','true-darkness*','darkness*')
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
    manifest_files.extend((folder, manifest) for manifest in sorted((root / folder).glob('*.pw.toml')))
classified_paths = {manifest for _, manifest in manifest_files}
# Retained task evidence is not a production input, matching .packwizignore.
ignored_folders = {'build', 'dist', 'generated', '.gradle', '.git', 'workspace_artifacts'}
unclassified = []
for directory, folders, files in os.walk(root):
    if pathlib.Path(directory) == root:
        folders[:] = [folder for folder in folders if folder not in ignored_folders]
    unclassified.extend(
        pathlib.Path(directory) / name for name in files
        if name.endswith('.pw.toml') and pathlib.Path(directory) / name not in classified_paths
    )
unclassified.sort()
if unclassified:
    raise SystemExit("unclassified Packwiz manifests: " + ', '.join(str(path) for path in unclassified))

for folder, manifest in manifest_files:
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

# Prepared bytes are qualified by the caller, not by this input-assembly step.
# Interface: apply_runtime_overrides tree|client-zip TARGET
apply_runtime_overrides() {
  if [ -e "$ROOT/config/better-content-runtime-overrides-v2.json" ]; then
    _apply_runtime_selection config/better-content-runtime-overrides-v2.json \
      .worklane/runtime-overrides-v2/current.path "${BC_PACKAGE_RUNTIME_OVERRIDES:-}" "$@" || return $?
  else
    _apply_runtime_selection config/better-content-runtime-overrides.json \
      .worklane/runtime-overrides/current.path "${BC_PACKAGE_RUNTIME_OVERRIDES:-}" "$@" || return $?
  fi
  _apply_runtime_selection config/better-content-interface-runtime-overrides.json \
    .worklane/interface-overrides/current.path "${BC_PACKAGE_INTERFACE_OVERRIDES:-}" "$@" || return $?
  _apply_runtime_selection config/better-content-startup-runtime-overrides.json \
    .worklane/startup-overrides/current.path "${BC_PACKAGE_STARTUP_OVERRIDES:-}" "$@"
}

# Internal interface: POLICY RELATIVE_POINTER PREPARED_ARG tree|client-zip TARGET
_apply_runtime_selection() {
  [ -e "$ROOT/$1" ] || return 0
  python3 - "$ROOT" "$4" "$5" "$3" "$2" <<'PY'
import json, os, pathlib, shutil, stat, sys, tempfile, zipfile

root, mode, target, prepared = pathlib.Path(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3]), sys.argv[4]

def fail(message):
    raise ValueError(message)

def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            fail(f'duplicate JSON key: {key}')
        result[key] = value
    return result

def load_json(data):
    return json.loads(data, object_pairs_hook=unique_object)

def basename(value, suffix):
    if (not isinstance(value, str) or not value.endswith(suffix)
            or value in ('.', '..') or '/' in value or '\\' in value
            or any(ord(c) < 32 for c in value) or ':' in value):
        fail(f'unsafe {suffix} basename: {value!r}')
    return value

def safe_path(path):
    if not path.is_absolute() or '..' in path.parts:
        fail(f'expected absolute non-escaping path: {path}')
    for part in (path, *path.parents):
        if part.is_symlink():
            fail(f'symlink path is not a packaging input: {part}')
    return path

try:
    if not prepared:
        pointer = safe_path(root / sys.argv[5])
        prepared = pointer.read_text().strip()
    source = safe_path(pathlib.Path(prepared))
    selection = safe_path(source / 'selection.json')
    plan = load_json(selection.read_text())
    if (not isinstance(plan, dict) or set(plan) != {'schema', 'artifacts'}
            or plan['schema'] != 'bc.package_runtime_overrides.v1'
            or not isinstance(plan['artifacts'], list) or not plan['artifacts']):
        fail('invalid runtime override selection schema')
    artifacts = plan['artifacts']
    names, manifests, identities = set(), set(), set()
    for entry in artifacts:
        if not isinstance(entry, dict) or set(entry) != {'input', 'output', 'manifest', 'project_id', 'file_id'}:
            fail('invalid runtime override artifact fields')
        original = basename(entry['input'], '.jar')
        output = basename(entry['output'], '.jar')
        manifest = basename(entry['manifest'], '.pw.toml')
        if original in names or output in names or original == output or manifest in manifests:
            fail('duplicate or ambiguous runtime override names')
        names.update((original, output))
        manifests.add(manifest)
        pair = (entry['project_id'], entry['file_id'])
        if any(type(n) is not int or n <= 0 for n in pair) or pair in identities:
            fail('duplicate or invalid CurseForge identity')
        identities.add(pair)
        jar = safe_path(source / output)
        if not jar.is_file():
            fail(f'missing prepared runtime override: {jar}')
    target = safe_path(target.absolute())
    if mode == 'tree':
        mods = safe_path(target / 'mods')
        # Preflight all destinations before changing this generated tree.
        for entry in artifacts:
            for key in ('input', 'output', 'manifest'):
                path = safe_path(mods / entry[key])
                if path.exists() and not path.is_file():
                    fail(f'not a regular generated mod file: {path}')
            if (mods / entry['output']).exists():
                fail(f'corrected output already exists: {entry["output"]}')
        mods.mkdir(parents=True, exist_ok=True)
        for entry in artifacts:
            (mods / entry['input']).unlink(missing_ok=True)
            (mods / entry['manifest']).unlink(missing_ok=True)
            shutil.copy2(source / entry['output'], mods / entry['output'])
    elif mode == 'client-zip':
        with zipfile.ZipFile(target) as archive:
            infos = archive.infolist()
            seen = set()
            for info in infos:
                name = info.filename
                parts = name.rstrip('/').split('/')
                if (name in seen or name.startswith('/') or '\\' in name or ':' in name
                        or any(p in ('', '.', '..') for p in parts)
                        or any(ord(c) < 32 for c in name)
                        or stat.S_ISLNK(info.external_attr >> 16)):
                    fail(f'unsafe or duplicate client ZIP entry: {name!r}')
                seen.add(name)
            if 'manifest.json' not in seen:
                fail('client ZIP has no manifest.json')
            manifest = load_json(archive.read('manifest.json'))
            if not isinstance(manifest, dict) or not isinstance(manifest.get('files'), list):
                fail('invalid client manifest files')
            overrides = manifest.get('overrides')
            if (not isinstance(overrides, str) or overrides.startswith('/') or '\\' in overrides
                    or ':' in overrides or any(ord(c) < 32 for c in overrides)
                    or any(p in ('', '.', '..') for p in overrides.split('/'))):
                fail('unsafe client overrides root')
            found = set()
            files = []
            for item in manifest['files']:
                if not isinstance(item, dict):
                    fail('invalid CurseForge manifest entry')
                pair = (item.get('projectID'), item.get('fileID'))
                if any(type(n) is not int or n <= 0 for n in pair):
                    fail('invalid CurseForge manifest identity')
                if pair in found:
                    fail('duplicate CurseForge manifest identity')
                found.add(pair)
                if pair not in identities:
                    files.append(item)
            if not identities <= found:
                fail('client manifest missing selected original CurseForge identity')
            manifest['files'] = files
            removed = {f'{overrides}/mods/{entry[key]}' for entry in artifacts for key in ('input', 'manifest')}
            added = {f'{overrides}/mods/{entry["output"]}': source / entry['output'] for entry in artifacts}
            if seen & added.keys():
                fail('corrected output already exists in client ZIP')
            # A file cannot also be a parent directory of another entry, including
            # the new corrected files. Do not produce ambiguous launcher inputs.
            file_names = {info.filename for info in infos if not info.is_dir()}
            for name in seen | added.keys():
                if any(str(parent) in file_names for parent in pathlib.PurePosixPath(name).parents if str(parent) != '.'):
                    fail(f'ambiguous client ZIP path: {name!r}')
            if any(name + '/' in seen for name in added):
                fail('corrected output conflicts with a client ZIP directory')
            descriptor, temporary_name = tempfile.mkstemp(prefix=f'.{target.name}.', suffix='.tmp', dir=target.parent)
            os.close(descriptor)
            temporary = pathlib.Path(temporary_name)
            try:
                with zipfile.ZipFile(temporary, 'w') as output:
                    output.comment = archive.comment
                    for info in infos:
                        if info.filename not in removed:
                            data = (json.dumps(manifest, indent=2) + '\n').encode() if info.filename == 'manifest.json' else archive.read(info)
                            output.writestr(info, data)
                    for name, jar in added.items():
                        output.write(jar, name, compress_type=zipfile.ZIP_DEFLATED)
                os.replace(temporary, target)
            finally:
                temporary.unlink(missing_ok=True)
    else:
        fail(f'unknown runtime override assembly mode: {mode}')
except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as error:
    raise SystemExit(f'package failed: runtime overrides: {error}') from error
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
  apply_runtime_overrides tree "$target"
}

install_server() {
  local server="$1" accept_eula="$2" profile="$3" port="${4:-25565}" properties
  local installer="$ROOT/forge-$FORGE_COORD-installer.jar"
  python3 -B "$ROOT/scripts/prepare_forge_installer.py" || fail "could not prepare pinned Forge installer: $installer"
  [ -f "$installer" ] || fail "missing Forge installer after preparation: $installer"
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
  cp "$ROOT/run.sh" "$ROOT/run-forge.sh" "$ROOT/better-world-management-server.sh" "$server/"
  chmod 0755 "$server/run.sh" "$server/run-forge.sh" "$server/better-world-management-server.sh"
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
  local untested_suffix=""
  if (($# != 0)); then
    (($# == 2)) && [[ "$1" == --untested-suffix && "$2" =~ ^[a-z]$ ]] || fail 'usage: dist.sh [--untested-suffix a..z]'
    untested_suffix="$2"
  fi
  for command in java packwiz python3 zip; do command -v "$command" >/dev/null || fail "$command is required"; done
  local out_root="$ROOT/dist"
  local current_version current_build next_build version version_dir_name release_dir client_dir server_dir stage
  current_version="$(awk -F ' *= *' '$1 == "version" { value=$2; gsub(/^"|"$/, "", value); print value; exit }' "$ROOT/pack.toml")"
  [[ "$current_version" =~ ^Build\ ([0-9]+)([a-z]?)$ ]] || fail 'pack version must be Build N or Build Na'
  current_build="${BASH_REMATCH[1]}"
  local current_suffix="${BASH_REMATCH[2]}"
  if [ -n "$untested_suffix" ]; then
    [[ "$untested_suffix" > "$current_suffix" ]] || fail 'untested suffix must advance the current suffix'
    version="Build $current_build$untested_suffix"
  else
    next_build=$((10#$current_build + 1))
    version="Build $next_build"
  fi
  version_dir_name="$(printf '%s' "$version" | tr '[:upper:] ' '[:lower:]-' | tr -cd 'a-z0-9._-')"
  # Keep the previous complete pair until a new complete pair exists; maintenance
  # owns disposal and active-consumer protection for superseded distributions.
  mkdir -p "$out_root"
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
  apply_runtime_overrides client-zip "$client_dir/better-content.zip"
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
  [ -z "$untested_suffix" ] || printf 'UNTESTED lettered build; no prior test verdict transfers to these archives.\n'
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

#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
STATE_DIR="$SCRIPT_DIR/.world_lifecycle_manager"
LEGACY_STATE_DIR="$SCRIPT_DIR/.prestige"
CONTROL_DIR="$STATE_DIR/control"
ARCHIVE_DIR="$STATE_DIR/archives"
TRANSACTION_DIR="$STATE_DIR/transactions"
LINEAGE_FILE="$STATE_DIR/lineage-v5.tsv"
RESET_FILE="$CONTROL_DIR/reset-request-v5.tsv"
STAGED_FILE="$CONTROL_DIR/staged-request-v5.tsv"
DRAFT_FILE="$CONTROL_DIR/draft-v5.tsv"
SUCCESSOR_FILE="$CONTROL_DIR/successor-request-v5.tsv"
HEALTH_FILE="$CONTROL_DIR/health-result-v5.tsv"
SHUTDOWN_FILE="$CONTROL_DIR/shutdown-request-v5.tsv"
ACTIVE_PROCESS_FILE="$CONTROL_DIR/active-successor-process-v2.tsv"
PERK_DRAFT_FILE="$CONTROL_DIR/perk-draft-v2.tsv"
PERK_STAGED_FILE="$CONTROL_DIR/staged-perks-v2.tsv"
PERK_RESET_FILE="$CONTROL_DIR/reset-perks-v2.tsv"
PERK_HEALTH_FILE="$CONTROL_DIR/perk-health-v3.tsv"
ACTIVE_PERKS_FILE="$STATE_DIR/perks-v2.tsv"
BIOME_CONFIG="$SCRIPT_DIR/config/world_lifecycle_manager-biomes.txt"
MAX_ATTEMPTS=8
HEALTH_TIMEOUT_SECONDS="${PRESTIGE_HEALTH_TIMEOUT_SECONDS:-300}"
HEALTH_STABILITY_SECONDS="${PRESTIGE_HEALTH_STABILITY_SECONDS:-10}"
MIN_FREE_RESERVE_BYTES="${PRESTIGE_MIN_FREE_RESERVE_BYTES:-1073741824}"
SUPERVISOR_LOG="$SCRIPT_DIR/logs/world-lifecycle-manager-supervisor.log"

mkdir -p -- "$SCRIPT_DIR/logs"
[[ -d "$SCRIPT_DIR/logs" && ! -L "$SCRIPT_DIR/logs" && ! -L "$SUPERVISOR_LOG" ]] || {
  printf 'prestige supervisor failed: unsafe logs path\n' >&2
  exit 1
}
: >> "$SUPERVISOR_LOG"
event() {
  local level="$1"; shift
  local line
  line="$(date -u +%Y-%m-%dT%H:%M:%SZ) [$level] $*"
  printf 'prestige supervisor: %s\n' "$line" >&2
  printf '%s\n' "$line" >> "$SUPERVISOR_LOG"
}
die() { event ERROR "$*"; exit 1; }

for command in awk basename cat chmod cmp cp cut date df du find flock mkdir mkfifo mv od readlink rm sha256sum sleep stat sync tr xargs zip; do
  command -v "$command" >/dev/null 2>&1 || die "missing required command: $command"
done
[[ -x "$SCRIPT_DIR/run-forge.sh" ]] || die "missing executable Forge launcher: $SCRIPT_DIR/run-forge.sh"
[[ ! -L "$SCRIPT_DIR/run-forge.sh" && ! -L "$SCRIPT_DIR/server.properties" ]] || die "launcher and server.properties must not be symlinks"
[[ "$HEALTH_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || die "PRESTIGE_HEALTH_TIMEOUT_SECONDS must be positive"
[[ "$HEALTH_STABILITY_SECONDS" =~ ^[0-9]+$ ]] || die "PRESTIGE_HEALTH_STABILITY_SECONDS must be non-negative"
[[ "$MIN_FREE_RESERVE_BYTES" =~ ^[1-9][0-9]*$ ]] || die "PRESTIGE_MIN_FREE_RESERVE_BYTES must be positive"
JAVA="${BC_JAVA:-}"
[[ -n "$JAVA" && -x "$JAVA" ]] || die "BC_JAVA is not an executable; start the server with ./run.sh"
JAVA="$(readlink -f -- "$JAVA")"
JAVA_MAJOR="$("$JAVA" -version 2>&1 | awk -F'[".]' '/version/ { if ($2 == "1") print $3; else print $2; exit }')"
[[ "$JAVA_MAJOR" == 17 ]] || die "World Lifecycle Manager requires Java 17, found ${JAVA_MAJOR:-unknown}"
export BC_JAVA="$JAVA" BC_WLM_SUPERVISED=1
event INFO "startup java=$JAVA pid=$$"
shopt -s nullglob
PRESTIGE_JARS=("$SCRIPT_DIR"/mods/world-lifecycle-manager-*.jar)
shopt -u nullglob
[[ "${#PRESTIGE_JARS[@]}" -eq 1 && -f "${PRESTIGE_JARS[0]}" && ! -L "${PRESTIGE_JARS[0]}" ]] \
  || die "expected exactly one regular mods/world-lifecycle-manager-*.jar"
PRESTIGE_JAR="${PRESTIGE_JARS[0]}"

if [[ "${1:-}" == verify-archive ]]; then
  [[ "$#" -eq 4 ]] || die "usage: world-lifecycle-manager-server.sh verify-archive ARCHIVE LINEAGE_ID TRANSACTION_ID"
  event INFO "verifying archive path=$2 lineage=$3 transaction=$4"
  exec "$JAVA" -cp "$PRESTIGE_JAR" com.bettercontent.worldlifecyclemanager.PrestigeArchiveVerifier verify "$2" "$3" "$4"
fi

[[ ! -e "$LEGACY_STATE_DIR" ]] || die "legacy .prestige state is unsupported; move or remove it before starting"
[[ ! -L "$STATE_DIR" ]] || die ".world_lifecycle_manager must not be a symlink"
mkdir -p -- "$CONTROL_DIR" "$ARCHIVE_DIR" "$TRANSACTION_DIR"
for directory in "$STATE_DIR" "$CONTROL_DIR" "$ARCHIVE_DIR" "$TRANSACTION_DIR"; do
  [[ -d "$directory" && ! -L "$directory" ]] || die "unsafe World Lifecycle Manager state directory: $directory"
done
for legacy_path in \
  "$STATE_DIR"/lineage-v{1,2,3,4}.tsv "$STATE_DIR/perks-v1.tsv" \
  "$CONTROL_DIR"/{draft,staged-request,reset-request,successor-request,health-result,shutdown-request}-v{1,2,3,4}.tsv \
  "$CONTROL_DIR"/{perk-draft,staged-perks,reset-perks}-v1.tsv \
  "$CONTROL_DIR"/perk-health-v{1,2}.tsv "$CONTROL_DIR/active-successor-process-v1.tsv"; do
  [[ ! -e "$legacy_path" ]] \
    || die "legacy World Lifecycle Manager state is unsupported; archive or move .world_lifecycle_manager before starting v5"
done
exec 9>"$STATE_DIR/supervisor.lock"
flock -n 9 || die "another prestige supervisor owns $STATE_DIR/supervisor.lock"

declare -a CONTRACT_LINES=()
load_contract() {
  local path="$1" magic="$2" count="$3"
  [[ -f "$path" && ! -L "$path" ]] || die "contract is not a regular file: $path"
  mapfile -t CONTRACT_LINES < "$path"
  [[ "${#CONTRACT_LINES[@]}" -eq "$count" ]] || die "contract has the wrong line count: $path"
  [[ "${CONTRACT_LINES[0]}" == "$magic" ]] || die "contract has an unsupported version: $path"
}

contract_value() {
  local index="$1" key="$2" line="${CONTRACT_LINES[$1]}" prefix="${2}"$'\t'
  [[ "$line" == "$prefix"* ]] || die "contract field $index is not $key"
  local value="${line#"$prefix"}"
  [[ -n "$value" && "$value" != *$'\t'* && "$value" != *$'\r'* ]] || die "contract field $key is blank or malformed"
  printf '%s' "$value"
}

validate_id() { [[ "$2" =~ ^[a-z0-9][a-z0-9_-]{0,63}$ ]] || die "$1 is outside the identifier contract: $2"; }
validate_world_name() { [[ "$1" =~ ^[A-Za-z0-9._-]{1,128}$ && "$1" != "." && "$1" != ".." ]] || die "unsupported level-name: $1"; }
validate_signed_long() { [[ "$2" =~ ^-?[0-9]+$ ]] || die "$1 is not a signed integer: $2"; }
validate_biome() { [[ "$1" =~ ^[a-z0-9_.-]+:[a-z0-9_./-]+$ ]] || die "biome is not a resource location: $1"; }

declare -A ALLOWED_BIOMES=()
load_allowed_biomes() {
  [[ -f "$BIOME_CONFIG" && ! -L "$BIOME_CONFIG" ]] || die "biome preference allowlist is not a regular file: $BIOME_CONFIG"
  local line
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -n "$line" && "$line" != \#* ]] || continue
    validate_biome "$line"
    [[ -z "${ALLOWED_BIOMES[$line]+present}" ]] || die "biome preference allowlist contains a duplicate: $line"
    ALLOWED_BIOMES["$line"]=1
  done < "$BIOME_CONFIG"
  (( ${#ALLOWED_BIOMES[@]} > 0 )) || die "biome preference allowlist is empty"
}

atomic_write() {
  local target="$1"; shift
  local partial="${target}.partial"
  [[ ! -e "$partial" ]] || die "partial contract already exists: $partial"
  (umask 077; printf '%s\n' "$@" > "$partial")
  sync -f -- "$partial"
  mv -T -- "$partial" "$target"
  sync -f -- "$(dirname -- "$target")"
}

ensure_lineage() {
  if [[ ! -e "$LINEAGE_FILE" ]]; then
    local entropy
    entropy="$(printf '%s:%s:%s' "$(date +%s%N)" "$$" "$SCRIPT_DIR" | sha256sum | cut -c1-32)"
    atomic_write "$LINEAGE_FILE" 'BC_PRESTIGE_LINEAGE_V5' \
      "lineage"$'\t'"lineage-$entropy" "total_prestiges"$'\t''0' "generation"$'\t''0'
  fi
  load_contract "$LINEAGE_FILE" 'BC_PRESTIGE_LINEAGE_V5' 4
  LINEAGE_ID="$(contract_value 1 lineage)"
  TOTAL_PRESTIGES="$(contract_value 2 total_prestiges)"
  GENERATION="$(contract_value 3 generation)"
  validate_id 'lineage ID' "$LINEAGE_ID"
  [[ "$TOTAL_PRESTIGES" =~ ^[0-9]+$ && "$GENERATION" =~ ^[0-9]+$ ]] \
    || die "lineage counters are invalid"
  (( GENERATION == TOTAL_PRESTIGES )) || die "lineage counters violate invariants"
}

server_property() {
  local key="$1" properties="$SCRIPT_DIR/server.properties" value
  [[ -f "$properties" && ! -L "$properties" ]] || die "missing regular server.properties"
  value="$(awk -F= -v key="$key" '$1 == key { count++; value=substr($0,index($0,"=")+1) } END { if(count==1) print value; else exit 1 }' "$properties")" \
    || die "server.properties must contain exactly one $key entry"
  printf '%s' "$value"
}

set_server_seed() {
  local seed="$1" properties="$SCRIPT_DIR/server.properties" partial="$CONTROL_DIR/server.properties.partial"
  rm -f -- "$partial"
  awk -v seed="$seed" 'BEGIN{found=0} /^level-seed=/{if(found++)exit 40;print "level-seed="seed;next}{print} END{if(!found)print "level-seed="seed}' \
    "$properties" > "$partial" || { rm -f -- "$partial"; die "could not update level-seed"; }
  mv -T -- "$partial" "$properties"
}

random_seed() {
  local seed
  seed="$(od -An -N8 -t d8 /dev/urandom | tr -d '[:space:]')"
  validate_signed_long 'random seed' "$seed"
  printf '%s' "$seed"
}

parse_reset() {
  load_contract "$RESET_FILE" 'BC_PRESTIGE_RESET_V5' 11
  [[ "$(contract_value 1 state)" == committed ]] || die "reset request is not committed"
  REQUEST_LINEAGE="$(contract_value 2 lineage)"
  REQUEST_BASE_GENERATION="$(contract_value 3 base_generation)"
  REQUEST_TRANSACTION="$(contract_value 4 transaction)"
  REQUEST_WORLD="$(contract_value 5 world)"
  REQUEST_OLD_SEED="$(contract_value 6 old_seed)"
  REQUEST_BIOME_1="$(contract_value 7 biome_1)"
  REQUEST_BIOME_2="$(contract_value 8 biome_2)"
  REQUEST_BIOME_3="$(contract_value 9 biome_3)"
  [[ "$(contract_value 10 seed_mode)" == random ]] || die "seed mode is not random"
  validate_id 'request lineage ID' "$REQUEST_LINEAGE"; validate_id 'transaction ID' "$REQUEST_TRANSACTION"
  validate_world_name "$REQUEST_WORLD"; validate_signed_long 'old seed' "$REQUEST_OLD_SEED"; validate_biome "$REQUEST_BIOME_1"
  [[ "$REQUEST_BIOME_2" == - ]] || validate_biome "$REQUEST_BIOME_2"
  [[ "$REQUEST_BIOME_3" == - ]] || validate_biome "$REQUEST_BIOME_3"
  [[ "$REQUEST_BIOME_2" != - || "$REQUEST_BIOME_3" == - ]] || die "biome preferences must be contiguous"
  [[ "$REQUEST_BIOME_1" != "$REQUEST_BIOME_2" && "$REQUEST_BIOME_1" != "$REQUEST_BIOME_3" \
      && ( "$REQUEST_BIOME_2" == - || "$REQUEST_BIOME_2" != "$REQUEST_BIOME_3" ) ]] || die "biome preferences must be unique"
  [[ -n "${ALLOWED_BIOMES[$REQUEST_BIOME_1]+present}" ]] || die "primary biome is not in the configured allowlist"
  [[ "$REQUEST_BIOME_2" == - || -n "${ALLOWED_BIOMES[$REQUEST_BIOME_2]+present}" ]] || die "secondary biome is not in the configured allowlist"
  [[ "$REQUEST_BIOME_3" == - || -n "${ALLOWED_BIOMES[$REQUEST_BIOME_3]+present}" ]] || die "tertiary biome is not in the configured allowlist"
  [[ "$REQUEST_BASE_GENERATION" =~ ^[0-9]+$ ]] || die "request base generation is invalid"
  [[ "$REQUEST_LINEAGE" == "$LINEAGE_ID" ]] || die "reset request lineage does not match durable lineage"
}

parse_reset_perks() {
  load_contract "$PERK_RESET_FILE" 'BC_PRESTIGE_RESET_PERKS_V2' 9
  PERK_LINEAGE="$(contract_value 1 lineage)"
  PERK_BASE_GENERATION="$(contract_value 2 base_generation)"
  PERK_TARGET_GENERATION="$(contract_value 3 target_generation)"
  PERK_TRANSACTION="$(contract_value 4 transaction)"
  RESET_PERKS="$(contract_value 5 perks)"
  PERK_BIOME_1="$(contract_value 6 biome_1)"
  PERK_BIOME_2="$(contract_value 7 biome_2)"
  PERK_BIOME_3="$(contract_value 8 biome_3)"
  [[ "$PERK_LINEAGE" == "$REQUEST_LINEAGE" && "$PERK_BASE_GENERATION" == "$REQUEST_BASE_GENERATION" \
      && "$PERK_TRANSACTION" == "$REQUEST_TRANSACTION" ]] || die "committed perk snapshot identity does not match reset"
  [[ "$PERK_BASE_GENERATION" =~ ^[0-9]+$ && "$PERK_TARGET_GENERATION" =~ ^[0-9]+$ \
      && "$PERK_BASE_GENERATION" -lt 9223372036854775807 \
      && "$PERK_TARGET_GENERATION" -eq $((PERK_BASE_GENERATION+1)) ]] || die "committed perk snapshot generation is invalid"
  [[ "$PERK_BIOME_1" == "$REQUEST_BIOME_1" && "$PERK_BIOME_2" == "$REQUEST_BIOME_2" \
      && "$PERK_BIOME_3" == "$REQUEST_BIOME_3" ]] || die "committed perk biome preferences do not match reset"

  declare -gA RESET_PERK_SET=()
  local id known budget class_perk advanced
  local -a perk_ids=()
  if [[ "$RESET_PERKS" != - ]]; then
    IFS=',' read -r -a perk_ids <<<"$RESET_PERKS"
    for id in "${perk_ids[@]}"; do
      known=false
      case "$id" in
        biome_selection|\
        class_wayfinder|class_field_cook|class_rail_scout|class_flood_runner|class_market_runner|class_trail_wrangler|\
        embark_budget_i|embark_budget_ii|embark_budget_iii|embark_budget_iv|schematicannon_start) known=true ;;
      esac
      [[ "$known" == true && -z "${RESET_PERK_SET[$id]+present}" ]] || die "committed perk list is unknown or duplicated: $id"
      RESET_PERK_SET["$id"]=1
    done
  fi
  budget="$PERK_TARGET_GENERATION"; (( budget <= 12 )) || budget=12
  (( ${#RESET_PERK_SET[@]} <= budget )) || die "committed perk list exceeds its prestige-point budget"
  for class_perk in class_wayfinder class_field_cook class_rail_scout class_flood_runner class_market_runner class_trail_wrangler; do
    if [[ -n "${RESET_PERK_SET[$class_perk]+present}" ]]; then
      [[ -n "${RESET_PERK_SET[biome_selection]+present}" ]] || die "$class_perk lacks Biome Selection"
    fi
  done
  for advanced in embark_budget_i embark_budget_ii embark_budget_iii embark_budget_iv schematicannon_start; do
    if [[ -n "${RESET_PERK_SET[$advanced]+present}" ]]; then
      for class_perk in class_wayfinder class_field_cook class_rail_scout class_flood_runner class_market_runner class_trail_wrangler; do
        [[ -n "${RESET_PERK_SET[$class_perk]+present}" ]] || die "$advanced lacks all six class perks"
      done
    fi
  done
  [[ -z "${RESET_PERK_SET[embark_budget_ii]+present}" || -n "${RESET_PERK_SET[embark_budget_i]+present}" ]] \
    || die "Embark Budget II lacks Embark Budget I"
  [[ -z "${RESET_PERK_SET[embark_budget_iii]+present}" || -n "${RESET_PERK_SET[embark_budget_ii]+present}" ]] \
    || die "Embark Budget III lacks Embark Budget II"
  [[ -z "${RESET_PERK_SET[embark_budget_iv]+present}" || -n "${RESET_PERK_SET[embark_budget_iii]+present}" ]] \
    || die "Embark Budget IV lacks Embark Budget III"
  [[ -z "${RESET_PERK_SET[schematicannon_start]+present}" || ( -n "${RESET_PERK_SET[embark_budget_iv]+present}" && ${#RESET_PERK_SET[@]} -eq 12 ) ]] \
    || die "Schematicannon Start lacks the complete 12-perk graph"
}

write_phase() {
  local next="$1" current="${CURRENT_PHASE:-}"
  case "$current:$next" in
    :request-recorded|request-recorded:world-staged|world-staged:archive-verified|archive-verified:attempt-1-prepared|\
    attempt-[1-8]-prepared:attempt-[1-8]-prepared|attempt-[1-8]-prepared:attempt-[1-8]-running|\
    attempt-1-prepared:attempt-2-prepared|attempt-2-prepared:attempt-3-prepared|attempt-3-prepared:attempt-4-prepared|\
    attempt-4-prepared:attempt-5-prepared|attempt-5-prepared:attempt-6-prepared|attempt-6-prepared:attempt-7-prepared|attempt-7-prepared:attempt-8-prepared|\
    attempt-1-running:attempt-2-prepared|attempt-2-running:attempt-3-prepared|attempt-3-running:attempt-4-prepared|\
    attempt-4-running:attempt-5-prepared|attempt-5-running:attempt-6-prepared|attempt-6-running:attempt-7-prepared|attempt-7-running:attempt-8-prepared|\
    attempt-[1-8]-running:health-verified|health-verified:health-verified|health-verified:lineage-committed) ;;
    world-staged:rolled-back) ;;
    attempt-[1-8]-running:rolled-back|attempt-[1-8]-prepared:rolled-back)
      [[ "$current" == "attempt-$MAX_ATTEMPTS-running" || "$current" == "attempt-$MAX_ATTEMPTS-prepared" ]] \
        || die "rollback attempted before the final authorized successor attempt" ;;
    *) die "illegal transaction phase transition: ${current:-none} -> $next" ;;
  esac
  rm -f -- "$PHASE_FILE.partial"
  atomic_write "$PHASE_FILE" 'BC_PRESTIGE_TRANSACTION_PHASE_V2' \
    "transaction"$'\t'"$REQUEST_TRANSACTION" "phase"$'\t'"$next"
  CURRENT_PHASE="$next"
  event INFO "transaction=$REQUEST_TRANSACTION phase=$next"
  if [[ "${PRESTIGE_TEST_INTERRUPT_AT:-}" == "$next" ]]; then
    event WARN "transaction=$REQUEST_TRANSACTION test interruption at phase=$next"
    exit 86
  fi
}

read_phase() {
  load_contract "$PHASE_FILE" 'BC_PRESTIGE_TRANSACTION_PHASE_V2' 3
  [[ "$(contract_value 1 transaction)" == "$REQUEST_TRANSACTION" ]] || die "transaction phase identity mismatch"
  CURRENT_PHASE="$(contract_value 2 phase)"
  [[ "$CURRENT_PHASE" =~ ^(request-recorded|world-staged|archive-verified|health-verified|lineage-committed|rolled-back|attempt-[1-8]-(prepared|running))$ ]] \
    || die "unknown transaction phase: $CURRENT_PHASE"
}

generate_archive_manifest() {
  local world="$1" output="$2"
  event INFO "transaction=$REQUEST_TRANSACTION generating archive manifest world=$world"
  "$JAVA" -cp "$PRESTIGE_JAR" com.bettercontent.worldlifecyclemanager.PrestigeArchiveVerifier manifest \
    "$world" "$output" "$REQUEST_LINEAGE" "$REQUEST_TRANSACTION" \
    || die "archive manifest generation failed for transaction $REQUEST_TRANSACTION"
}

verify_archive_against_world() {
  local archive="$1" source_manifest="$2"
  event INFO "transaction=$REQUEST_TRANSACTION verifying archive=$archive"
  "$JAVA" -cp "$PRESTIGE_JAR" com.bettercontent.worldlifecyclemanager.PrestigeArchiveVerifier verify-against \
    "$archive" "$source_manifest" "$REQUEST_LINEAGE" "$REQUEST_TRANSACTION" \
    || die "archive failed strict production verification"
}

create_verified_archive() {
  local input="$1" final="$2" partial="${2%.zip}.partial.zip" manifest="$1/world-lifecycle-manager-archive-manifest-v1.tsv" digest
  [[ ! -L "$input" && ! -L "$ARCHIVE_DIR" ]] || die "unsafe archive path"
  [[ -f "$manifest" && ! -L "$manifest" ]] || generate_archive_manifest "$input/world" "$manifest"
  if [[ -f "$final" ]]; then
    [[ ! -L "$final" ]] || die "published archive is a symlink"
    verify_archive_against_world "$final" "$manifest"
    chmod 0444 -- "$final"
    sync -f -- "$final"
    ensure_archive_checksum "$final"
    return
  fi
  [[ ! -e "${final}.sha256" ]] || die "archive checksum evidence exists without its immutable archive"
  if [[ -e "$partial" ]]; then
    if [[ -f "$partial" && ! -L "$partial" ]] && "$JAVA" -cp "$PRESTIGE_JAR" \
        com.bettercontent.worldlifecyclemanager.PrestigeArchiveVerifier verify-against \
        "$partial" "$manifest" "$REQUEST_LINEAGE" "$REQUEST_TRANSACTION" >/dev/null; then
      event INFO "transaction=$REQUEST_TRANSACTION resuming verified partial archive"
    else
      local rejected
      rejected="$TRANSACTION_ROOT/rejected-archive-partial-$(date +%s%N).zip"
      mv -T -- "$partial" "$rejected"
      sync -f -- "$TRANSACTION_ROOT"
    fi
  fi
  if [[ ! -f "$partial" ]]; then
    event INFO "transaction=$REQUEST_TRANSACTION creating archive=$partial"
    (cd -- "$input" && { printf '%s\0' world-lifecycle-manager-archive-manifest-v1.tsv; find world -type f -print0; } | xargs -0 zip -q "$partial")
    sync -f -- "$partial"
  fi
  verify_archive_against_world "$partial" "$manifest"
  chmod 0444 -- "$partial"
  sync -f -- "$partial"
  mv -T -- "$partial" "$final"
  sync -f -- "$ARCHIVE_DIR"
  digest="$(sha256sum -- "$final" | cut -d' ' -f1)"
  atomic_write "${final}.sha256" "$digest  $(basename -- "$final")"
  event INFO "transaction=$REQUEST_TRANSACTION published archive=$final sha256=$digest"
}

ensure_archive_checksum() {
  local archive="$1" evidence="${1}.sha256" digest expected
  digest="$(sha256sum -- "$archive" | cut -d' ' -f1)"
  if [[ ! -e "$evidence" ]]; then
    atomic_write "$evidence" "$digest  $(basename -- "$archive")"
    return
  fi
  [[ -f "$evidence" && ! -L "$evidence" ]] || die "archive checksum evidence is unsafe"
  mapfile -t CONTRACT_LINES < "$evidence"
  [[ "${#CONTRACT_LINES[@]}" -eq 1 ]] || die "archive checksum evidence is malformed"
  expected="$digest  $(basename -- "$archive")"
  [[ "${CONTRACT_LINES[0]}" == "$expected" ]] || die "archive checksum evidence does not match the immutable archive"
}

write_successor_request() {
  local attempt="$1" seed="$2"
  rm -f -- "$SUCCESSOR_FILE.partial"
  atomic_write "$SUCCESSOR_FILE" 'BC_PRESTIGE_SUCCESSOR_V5' \
    "lineage"$'\t'"$REQUEST_LINEAGE" "base_generation"$'\t'"$BASE_GENERATION" \
    "target_generation"$'\t'"$TARGET_GENERATION" "transaction"$'\t'"$REQUEST_TRANSACTION" \
    "successor_seed"$'\t'"$seed" "biome_1"$'\t'"$REQUEST_BIOME_1" \
    "biome_2"$'\t'"$REQUEST_BIOME_2" "biome_3"$'\t'"$REQUEST_BIOME_3" "attempt"$'\t'"$attempt"
}

health_is_valid() {
  local expected_attempt="$1" expected_seed="$2" actual_biome resolved_biome
  [[ -f "$HEALTH_FILE" && ! -L "$HEALTH_FILE" ]] || return 1
  mapfile -t CONTRACT_LINES < "$HEALTH_FILE"
  [[ "${#CONTRACT_LINES[@]}" -eq 17 && "${CONTRACT_LINES[0]}" == BC_PRESTIGE_HEALTH_V5 ]] || return 1
  [[ "${CONTRACT_LINES[1]}" == "lineage"$'\t'"$REQUEST_LINEAGE" && "${CONTRACT_LINES[2]}" == "base_generation"$'\t'"$BASE_GENERATION" ]] || return 1
  [[ "${CONTRACT_LINES[3]}" == "target_generation"$'\t'"$TARGET_GENERATION" && "${CONTRACT_LINES[4]}" == "transaction"$'\t'"$REQUEST_TRANSACTION" ]] || return 1
  [[ "${CONTRACT_LINES[5]}" == "successor_seed"$'\t'"$expected_seed" && "${CONTRACT_LINES[6]}" == "actual_seed"$'\t'"$expected_seed" ]] || return 1
  [[ "${CONTRACT_LINES[7]}" == "requested_biome_1"$'\t'"$REQUEST_BIOME_1" \
      && "${CONTRACT_LINES[8]}" == "requested_biome_2"$'\t'"$REQUEST_BIOME_2" \
      && "${CONTRACT_LINES[9]}" == "requested_biome_3"$'\t'"$REQUEST_BIOME_3" \
      && "${CONTRACT_LINES[10]}" == resolved_biome$'\t'* \
      && "${CONTRACT_LINES[11]}" == actual_biome$'\t'* ]] || return 1
  resolved_biome="${CONTRACT_LINES[10]#resolved_biome$'\t'}"
  actual_biome="${CONTRACT_LINES[11]#actual_biome$'\t'}"
  [[ "$actual_biome" =~ ^[a-z0-9_.-]+:[a-z0-9_./-]+$ ]] || return 1
  [[ "$resolved_biome" == "$REQUEST_BIOME_1" || ( "$REQUEST_BIOME_2" != - && "$resolved_biome" == "$REQUEST_BIOME_2" ) \
      || ( "$REQUEST_BIOME_3" != - && "$resolved_biome" == "$REQUEST_BIOME_3" ) ]] || return 1
  [[ "$actual_biome" == "$resolved_biome" ]] || return 1
  [[ "${CONTRACT_LINES[12]}" == "attempt"$'\t'"$expected_attempt" && "${CONTRACT_LINES[13]}" == "world"$'\t'"$REQUEST_WORLD" ]] || return 1
  [[ "${CONTRACT_LINES[14]}" == "level_dat"$'\t''true' && "${CONTRACT_LINES[15]}" == "fresh_players"$'\t''true' && "${CONTRACT_LINES[16]}" == "status"$'\t''healthy' ]] || return 1
  [[ -f "$SCRIPT_DIR/$REQUEST_WORLD/level.dat" ]] || return 1

  [[ -f "$PERK_HEALTH_FILE" && ! -L "$PERK_HEALTH_FILE" ]] || return 1
  mapfile -t CONTRACT_LINES < "$PERK_HEALTH_FILE"
  [[ "${#CONTRACT_LINES[@]}" -eq 10 && "${CONTRACT_LINES[0]}" == BC_PRESTIGE_PERK_HEALTH_V3 ]] || return 1
  [[ "${CONTRACT_LINES[1]}" == "lineage"$'\t'"$REQUEST_LINEAGE" && "${CONTRACT_LINES[2]}" == "base_generation"$'\t'"$BASE_GENERATION" ]] || return 1
  [[ "${CONTRACT_LINES[3]}" == "target_generation"$'\t'"$TARGET_GENERATION" && "${CONTRACT_LINES[4]}" == "transaction"$'\t'"$REQUEST_TRANSACTION" ]] || return 1
  [[ "${CONTRACT_LINES[5]}" == "attempt"$'\t'"$expected_attempt" \
      && "${CONTRACT_LINES[6]}" == "resolved_biome"$'\t'"$resolved_biome" ]] || return 1
  [[ "${CONTRACT_LINES[7]}" =~ ^spawn_x$'\t'-?[0-9]+$ \
      && "${CONTRACT_LINES[8]}" =~ ^spawn_y$'\t'-?[0-9]+$ \
      && "${CONTRACT_LINES[9]}" =~ ^spawn_z$'\t'-?[0-9]+$ ]] || return 1
}

request_successor_shutdown() {
  local transaction="$1"
  rm -f -- "$SHUTDOWN_FILE" "$SHUTDOWN_FILE.partial"
  atomic_write "$SHUTDOWN_FILE" 'BC_PRESTIGE_SHUTDOWN_V5' "transaction"$'\t'"$transaction"
}

stop_console_relay() {
  if [[ -n "${CONSOLE_RELAY_PID:-}" ]]; then kill "$CONSOLE_RELAY_PID" 2>/dev/null || true; wait "$CONSOLE_RELAY_PID" 2>/dev/null || true; CONSOLE_RELAY_PID=''; fi
  exec 8>&- || true
}
trap stop_console_relay EXIT

process_start_ticks() {
  local pid="$1" raw rest
  [[ -r "/proc/$pid/stat" ]] || return 1
  raw="$(<"/proc/$pid/stat")"
  rest="${raw##*) }"
  awk '{print $20}' <<<"$rest"
}

load_process_contract() {
  local path="$1"
  load_contract "$path" 'BC_PRESTIGE_ACTIVE_SUCCESSOR_V2' 6
  PROCESS_PID="$(contract_value 1 pid)"
  PROCESS_START="$(contract_value 2 start_ticks)"
  PROCESS_LINEAGE="$(contract_value 3 lineage)"
  PROCESS_TRANSACTION="$(contract_value 4 transaction)"
  PROCESS_ATTEMPT="$(contract_value 5 attempt)"
  [[ "$PROCESS_PID" =~ ^[1-9][0-9]*$ && "$PROCESS_START" =~ ^[1-9][0-9]*$ && "$PROCESS_ATTEMPT" =~ ^[1-8]$ ]] \
    || die "successor process contract is malformed"
  validate_id 'successor process lineage ID' "$PROCESS_LINEAGE"
  validate_id 'successor process transaction ID' "$PROCESS_TRANSACTION"
}

process_contract_is_live() {
  local path="$1" actual raw rest state
  [[ -f "$path" && ! -L "$path" ]] || return 1
  load_process_contract "$path"
  kill -0 "$PROCESS_PID" 2>/dev/null || return 1
  actual="$(process_start_ticks "$PROCESS_PID" 2>/dev/null || true)"
  [[ "$actual" == "$PROCESS_START" ]] || return 1
  raw="$(cat -- "/proc/$PROCESS_PID/stat" 2>/dev/null)" || return 1
  rest="${raw##*) }"
  state="$(awk '{print $1}' <<<"$rest")"
  [[ "$state" != Z ]]
}

stop_failed_server() {
  local path="$ACTIVE_PROCESS_FILE"
  process_contract_is_live "$path" || return 0
  request_successor_shutdown "$PROCESS_TRANSACTION"
  local deadline=$((SECONDS+30))
  while process_contract_is_live "$path" && (( SECONDS < deadline )); do sleep 1; done
  if process_contract_is_live "$path"; then kill -TERM "$PROCESS_PID" 2>/dev/null || true; sleep 5; fi
  if process_contract_is_live "$path"; then kill -KILL "$PROCESS_PID" 2>/dev/null || true; fi
  wait "$PROCESS_PID" 2>/dev/null || true
}

clear_active_process() {
  rm -f -- "$ACTIVE_PROCESS_FILE" "$ACTIVE_PROCESS_FILE.partial" "$SHUTDOWN_FILE" "$SHUTDOWN_FILE.partial"
  sync -f -- "$CONTROL_DIR"
}

reconcile_active_process() {
  [[ -e "$ACTIVE_PROCESS_FILE" ]] || return 0
  if process_contract_is_live "$ACTIVE_PROCESS_FILE"; then
    event WARN "stopping orphaned successor process pid=$PROCESS_PID transaction=$PROCESS_TRANSACTION"
    stop_failed_server
  fi
  clear_active_process
}

restart_supervisor() {
  flock -u 9
  exec "$SCRIPT_DIR/world-lifecycle-manager-server.sh" "$@"
}

test_interrupt() {
  local boundary="$1"
  if [[ "${PRESTIGE_TEST_INTERRUPT_AT:-}" == "$boundary" ]]; then
    event WARN "test interruption at boundary=$boundary"
    exit 86
  fi
}

handle_signal() {
  trap - INT TERM HUP
  stop_console_relay
  if [[ -f "$ACTIVE_PROCESS_FILE" ]]; then stop_failed_server; clear_active_process; fi
  exit 130
}
trap handle_signal INT TERM HUP

commit_lineage() {
  ensure_lineage
  if (( TOTAL_PRESTIGES == TARGET_TOTAL && GENERATION == TARGET_GENERATION )); then
    return
  fi
  (( TOTAL_PRESTIGES == BASE_TOTAL && GENERATION == BASE_GENERATION )) \
    || die "durable lineage changed outside the active transaction"
  rm -f -- "$LINEAGE_FILE.partial"
  atomic_write "$LINEAGE_FILE" 'BC_PRESTIGE_LINEAGE_V5' \
    "lineage"$'\t'"$LINEAGE_ID" "total_prestiges"$'\t'"$TARGET_TOTAL" \
    "generation"$'\t'"$TARGET_GENERATION"
}

commit_active_perks() {
  local desired=(
    'BC_PRESTIGE_PERKS_V2'
    "lineage"$'\t'"$REQUEST_LINEAGE"
    "generation"$'\t'"$TARGET_GENERATION"
    "perks"$'\t'"$RESET_PERKS"
  )
  if [[ -e "$ACTIVE_PERKS_FILE" ]]; then
    [[ -f "$ACTIVE_PERKS_FILE" && ! -L "$ACTIVE_PERKS_FILE" ]] || die "active perk state is unsafe"
    mapfile -t CONTRACT_LINES < "$ACTIVE_PERKS_FILE"
    if [[ "${#CONTRACT_LINES[@]}" -eq 4 \
        && "${CONTRACT_LINES[0]}" == "${desired[0]}" \
        && "${CONTRACT_LINES[1]}" == "${desired[1]}" \
        && "${CONTRACT_LINES[2]}" == "${desired[2]}" \
        && "${CONTRACT_LINES[3]}" == "${desired[3]}" ]]; then
      return
    fi
    [[ "${#CONTRACT_LINES[@]}" -eq 4 && "${CONTRACT_LINES[0]}" == BC_PRESTIGE_PERKS_V2 \
        && "${CONTRACT_LINES[1]}" == "lineage"$'\t'"$REQUEST_LINEAGE" \
        && "${CONTRACT_LINES[2]}" == "generation"$'\t'"$BASE_GENERATION" \
        && "${CONTRACT_LINES[3]}" == perks$'\t'* ]] \
      || die "active perk state changed outside the active transaction"
  fi
  rm -f -- "$ACTIVE_PERKS_FILE.partial"
  atomic_write "$ACTIVE_PERKS_FILE" "${desired[@]}"
}

load_transaction_lineage() {
  load_contract "$TRANSACTION_ROOT/lineage-before-v5.tsv" 'BC_PRESTIGE_LINEAGE_V5' 4
  [[ "$(contract_value 1 lineage)" == "$REQUEST_LINEAGE" ]] || die "transaction lineage evidence has the wrong identity"
  BASE_TOTAL="$(contract_value 2 total_prestiges)"
  BASE_GENERATION="$(contract_value 3 generation)"
  [[ "$BASE_TOTAL" =~ ^[0-9]+$ && "$BASE_GENERATION" =~ ^[0-9]+$ ]] \
    || die "transaction lineage counters are invalid"
  (( BASE_GENERATION == BASE_TOTAL )) || die "transaction lineage evidence violates invariants"
  (( BASE_GENERATION == REQUEST_BASE_GENERATION )) || die "reset generation differs from transaction evidence"
  (( BASE_TOTAL < 9223372036854775807 )) || die "lineage counter cannot be incremented"
  TARGET_TOTAL=$((BASE_TOTAL+1)); TARGET_GENERATION=$((BASE_GENERATION+1))
}

cleanup_committed_transaction() {
  rm -f -- "$SUCCESSOR_FILE" "$HEALTH_FILE" "$PERK_HEALTH_FILE" "$SHUTDOWN_FILE" \
    "$STAGED_FILE" "$DRAFT_FILE" "$PERK_STAGED_FILE" "$PERK_DRAFT_FILE"
  if [[ -d "$ARCHIVE_INPUT/world" ]]; then rm -rf -- "$ARCHIVE_INPUT/world"; fi
  sync -f -- "$ARCHIVE_INPUT"
  sync -f -- "$CONTROL_DIR"
  [[ -d "$ACTIVE_WORLD" && ! -L "$ACTIVE_WORLD" ]] || die "committed lineage lacks canonical successor world"
  test_interrupt committed-cleanup-before-reset-release
  rm -f -- "$RESET_FILE" "$RESET_FILE.partial" "$PERK_RESET_FILE" "$PERK_RESET_FILE.partial"
  sync -f -- "$CONTROL_DIR"
}

cleanup_rolled_back_transaction() {
  rm -f -- "$SUCCESSOR_FILE" "$HEALTH_FILE" "$PERK_HEALTH_FILE" "$SHUTDOWN_FILE" \
    "$STAGED_FILE" "$DRAFT_FILE" "$PERK_STAGED_FILE" "$PERK_DRAFT_FILE"
  sync -f -- "$CONTROL_DIR"
  [[ -d "$ACTIVE_WORLD" && ! -L "$ACTIVE_WORLD" ]] || die "rolled-back transaction lacks the restored canonical world"
  test_interrupt rolled-back-cleanup-before-reset-release
  rm -f -- "$RESET_FILE" "$RESET_FILE.partial" "$PERK_RESET_FILE" "$PERK_RESET_FILE.partial"
  sync -f -- "$CONTROL_DIR"
}

rollback_archive_failure() {
  event ERROR "transaction=$REQUEST_TRANSACTION archive failed before successor launch; restoring source world"
  stop_failed_server
  clear_active_process
  if [[ -d "$ACTIVE_WORLD" ]]; then
    mv -T -- "$ACTIVE_WORLD" "$TRANSACTION_ROOT/unexpected-world-before-archive-rollback-$(date +%s%N)"
  fi
  cp -- "$TRANSACTION_ROOT/server.properties.before" "$SCRIPT_DIR/server.properties"
  [[ -d "$ARCHIVE_INPUT/world" && ! -L "$ARCHIVE_INPUT/world" ]] \
    || die "transaction=$REQUEST_TRANSACTION archive rollback cannot find the staged source world"
  mv -T -- "$ARCHIVE_INPUT/world" "$ACTIVE_WORLD"
  sync -f -- "$SCRIPT_DIR"
  write_phase rolled-back
  cleanup_rolled_back_transaction
  event WARN "transaction=$REQUEST_TRANSACTION source world restored after archive failure"
  restart_supervisor "$@"
}

require_uninhabited_candidate() {
  local world="$1" directory evidence
  [[ -d "$world" ]] || return 0
  evidence="$world/data/world_lifecycle_manager/successor-candidate-inhabited-v1.tsv"
  [[ ! -e "$evidence" && ! -L "$evidence" ]] \
    || die "successor candidate has a login marker ($evidence); refusing seed rotation or rollback"
  for directory in playerdata stats advancements; do
    [[ -d "$world/$directory" ]] || continue
    evidence="$(find "$world/$directory" -mindepth 1 -maxdepth 1 -type f -print -quit)"
    [[ -z "$evidence" ]] || die "successor candidate has player evidence ($evidence); refusing seed rotation or rollback"
  done
}

run_successor_attempt() {
  local attempt="$1" seed="$2"; shift 2
  rm -f -- "$HEALTH_FILE" "$HEALTH_FILE.partial" "$PERK_HEALTH_FILE" "$PERK_HEALTH_FILE.partial" \
    "$SHUTDOWN_FILE" "$SHUTDOWN_FILE.partial"
  write_successor_request "$attempt" "$seed"; set_server_seed "$seed"; write_phase "attempt-$attempt-prepared"
  event INFO "transaction=$REQUEST_TRANSACTION launching successor attempt=$attempt/$MAX_ATTEMPTS seed=$seed biomes=$REQUEST_BIOME_1,$REQUEST_BIOME_2,$REQUEST_BIOME_3"
  local fifo="$CONTROL_DIR/successor-console-$REQUEST_TRANSACTION-$attempt.fifo"
  rm -f -- "$fifo"; mkfifo -m 600 -- "$fifo"; exec 8<>"$fifo"; rm -f -- "$fifo"
  rm -f -- "$ACTIVE_PROCESS_FILE" "$ACTIVE_PROCESS_FILE.partial"
  (
    child_pid="$BASHPID"
    child_start="$(process_start_ticks "$child_pid")"
    atomic_write "$ACTIVE_PROCESS_FILE" 'BC_PRESTIGE_ACTIVE_SUCCESSOR_V2' \
      "pid"$'\t'"$child_pid" "start_ticks"$'\t'"$child_start" \
      "lineage"$'\t'"$REQUEST_LINEAGE" "transaction"$'\t'"$REQUEST_TRANSACTION" "attempt"$'\t'"$attempt"
    exec "$SCRIPT_DIR/run-forge.sh" "$@"
  ) <&8 9>&- & SUCCESSOR_PID=$!
  local handshake_deadline=$((SECONDS+10))
  while [[ ! -f "$ACTIVE_PROCESS_FILE" ]] && kill -0 "$SUCCESSOR_PID" 2>/dev/null && (( SECONDS < handshake_deadline )); do sleep 1; done
  process_contract_is_live "$ACTIVE_PROCESS_FILE" || { wait "$SUCCESSOR_PID" 2>/dev/null || true; return 1; }
  [[ "$PROCESS_PID" == "$SUCCESSOR_PID" && "$PROCESS_ATTEMPT" == "$attempt" ]] || die "successor launch handshake identity mismatch"
  write_phase "attempt-$attempt-running"
  # The relay may outlive an intentionally interrupted supervisor. Never let it inherit the
  # advisory-lock descriptor or a recovery supervisor cannot acquire the lock and reconcile it.
  cat <&0 >&8 9>&- & CONSOLE_RELAY_PID=$!
  local deadline=$((SECONDS+HEALTH_TIMEOUT_SECONDS))
  while process_contract_is_live "$ACTIVE_PROCESS_FILE" && (( SECONDS < deadline )); do
    if health_is_valid "$attempt" "$seed"; then
      local stable=$((SECONDS+HEALTH_STABILITY_SECONDS))
      while process_contract_is_live "$ACTIVE_PROCESS_FILE" && (( SECONDS < stable )); do sleep 1; done
      process_contract_is_live "$ACTIVE_PROCESS_FILE" || return 1
      return 0
    fi
    sleep 1
  done
  return 1
}

finalize_success() {
  cp -- "$SUCCESSOR_FILE" "$TRANSACTION_ROOT/successor-request-v5.tsv"
  cp -- "$HEALTH_FILE" "$TRANSACTION_ROOT/health-result-v5.tsv"
  cp -- "$PERK_HEALTH_FILE" "$TRANSACTION_ROOT/perk-health-v3.tsv"
  sync -f -- "$TRANSACTION_ROOT/successor-request-v5.tsv"
  sync -f -- "$TRANSACTION_ROOT/health-result-v5.tsv"
  sync -f -- "$TRANSACTION_ROOT/perk-health-v3.tsv"
  sync -f -- "$TRANSACTION_ROOT"
  write_phase health-verified
  verify_archive_against_world "$FINAL_ARCHIVE" "$ARCHIVE_INPUT/world-lifecycle-manager-archive-manifest-v1.tsv"
  chmod 0444 -- "$FINAL_ARCHIVE"
  sync -f -- "$FINAL_ARCHIVE"
  ensure_archive_checksum "$FINAL_ARCHIVE"
  commit_lineage
  if [[ "${PRESTIGE_TEST_INTERRUPT_AT:-}" == lineage-written ]]; then
    event WARN "transaction=$REQUEST_TRANSACTION test interruption at lineage-written"
    exit 86
  fi
  commit_active_perks
  write_phase lineage-committed
  cleanup_committed_transaction
  event INFO "transaction=$REQUEST_TRANSACTION committed; successor world is active"
}

load_allowed_biomes
ensure_lineage
reconcile_active_process
if [[ ! -e "$RESET_FILE" ]]; then
  event INFO "launching current world under lifecycle supervision"
  set +e; "$SCRIPT_DIR/run-forge.sh" "$@"; INITIAL_EXIT=$?; set -e
  event INFO "current world Forge process exited code=$INITIAL_EXIT reset_pending=$([[ -e "$RESET_FILE" ]] && printf yes || printf no)"
  [[ -e "$RESET_FILE" ]] || exit "$INITIAL_EXIT"
fi
parse_reset
parse_reset_perks
ACTIVE_WORLD_NAME="$(server_property level-name)"; validate_world_name "$ACTIVE_WORLD_NAME"
[[ "$ACTIVE_WORLD_NAME" == "$REQUEST_WORLD" ]] || die "reset world does not match level-name"
ACTIVE_WORLD="$SCRIPT_DIR/$ACTIVE_WORLD_NAME"
TRANSACTION_ROOT="$TRANSACTION_DIR/$REQUEST_TRANSACTION"
ARCHIVE_INPUT="$TRANSACTION_ROOT/archive-input"
PHASE_FILE="$TRANSACTION_ROOT/phase-v2.tsv"

if [[ ! -e "$TRANSACTION_ROOT" ]]; then
  mkdir -p -- "$ARCHIVE_INPUT"
  cp -- "$SCRIPT_DIR/server.properties" "$TRANSACTION_ROOT/server.properties.before"
  cp -- "$RESET_FILE" "$TRANSACTION_ROOT/reset-request-v5.tsv"
  cp -- "$PERK_RESET_FILE" "$TRANSACTION_ROOT/reset-perks-v2.tsv"
  cp -- "$LINEAGE_FILE" "$TRANSACTION_ROOT/lineage-before-v5.tsv"
  sync -f -- "$TRANSACTION_ROOT"
  write_phase request-recorded
else
  [[ -d "$TRANSACTION_ROOT" && ! -L "$TRANSACTION_ROOT" && -d "$ARCHIVE_INPUT" && ! -L "$ARCHIVE_INPUT" ]] \
    || die "existing transaction paths are unsafe"
  [[ -f "$TRANSACTION_ROOT/reset-request-v5.tsv" ]] || die "existing transaction lacks reset evidence"
  [[ -f "$TRANSACTION_ROOT/reset-perks-v2.tsv" ]] || die "existing transaction lacks committed perk evidence"
  [[ -f "$TRANSACTION_ROOT/lineage-before-v5.tsv" ]] || die "existing transaction lacks lineage evidence"
  cmp -s -- "$RESET_FILE" "$TRANSACTION_ROOT/reset-request-v5.tsv" || die "existing transaction reset identity changed"
  cmp -s -- "$PERK_RESET_FILE" "$TRANSACTION_ROOT/reset-perks-v2.tsv" || die "existing transaction perk identity changed"
  read_phase
fi
load_transaction_lineage
FINAL_ARCHIVE="$ARCHIVE_DIR/${LINEAGE_ID}-p$(printf '%06d' "$TARGET_TOTAL")-${REQUEST_TRANSACTION}.zip"
if (( TOTAL_PRESTIGES == TARGET_TOTAL && GENERATION == TARGET_GENERATION )); then
  [[ "$CURRENT_PHASE" == health-verified || "$CURRENT_PHASE" == lineage-committed ]] \
    || die "durable lineage advanced before health verification"
elif (( TOTAL_PRESTIGES != BASE_TOTAL || GENERATION != BASE_GENERATION )); then
  die "reset request generation is stale"
fi

if [[ "$CURRENT_PHASE" == lineage-committed ]]; then
  ensure_lineage
  (( TOTAL_PRESTIGES == TARGET_TOTAL && GENERATION == TARGET_GENERATION )) || die "committed transaction lineage counters are inconsistent"
  stop_failed_server
  clear_active_process
  verify_archive_against_world "$FINAL_ARCHIVE" "$ARCHIVE_INPUT/world-lifecycle-manager-archive-manifest-v1.tsv"
  chmod 0444 -- "$FINAL_ARCHIVE"
  sync -f -- "$FINAL_ARCHIVE"
  ensure_archive_checksum "$FINAL_ARCHIVE"
  commit_active_perks
  cleanup_committed_transaction
  restart_supervisor "$@"
fi

if [[ "$CURRENT_PHASE" == rolled-back ]]; then
  ensure_lineage
  (( TOTAL_PRESTIGES == BASE_TOTAL && GENERATION == BASE_GENERATION )) || die "rolled-back transaction changed lineage counters"
  [[ -d "$ACTIVE_WORLD" && ! -L "$ACTIVE_WORLD" ]] || die "rolled-back transaction lacks the restored canonical world"
  clear_active_process
  cleanup_rolled_back_transaction
  restart_supervisor "$@"
fi

if [[ "$CURRENT_PHASE" == health-verified ]]; then
  cmp -s -- "$SUCCESSOR_FILE" "$TRANSACTION_ROOT/successor-request-v5.tsv" || die "persisted successor evidence changed"
  cmp -s -- "$HEALTH_FILE" "$TRANSACTION_ROOT/health-result-v5.tsv" || die "persisted health evidence changed"
  cmp -s -- "$PERK_HEALTH_FILE" "$TRANSACTION_ROOT/perk-health-v3.tsv" || die "persisted perk health evidence changed"
  load_contract "$SUCCESSOR_FILE" 'BC_PRESTIGE_SUCCESSOR_V5' 10
  RECOVER_SEED="$(contract_value 5 successor_seed)"; RECOVER_ATTEMPT="$(contract_value 9 attempt)"
  health_is_valid "$RECOVER_ATTEMPT" "$RECOVER_SEED" || die "persisted health-verified phase no longer validates"
  finalize_success
  stop_failed_server
  clear_active_process
  restart_supervisor "$@"
fi

preflight_reset_capacity() {
  local world="$1" world_bytes reserve required available_kib available
  [[ -d "$world" && ! -L "$world" ]] || die "active world is not a regular directory"
  [[ "$(stat -c '%d' -- "$world")" == "$(stat -c '%d' -- "$ARCHIVE_INPUT")" ]] \
    || die "active world and World Lifecycle Manager transaction state must share a filesystem"
  world_bytes="$(du -sb -- "$world" | awk '{print $1}')"
  reserve=$((world_bytes / 100))
  (( reserve >= MIN_FREE_RESERVE_BYTES )) || reserve="$MIN_FREE_RESERVE_BYTES"
  required=$((world_bytes + reserve))
  available_kib="$(df -Pk -- "$ARCHIVE_DIR" | awk 'NR==2 {print $4}')"
  [[ "$available_kib" =~ ^[0-9]+$ ]] || die "could not determine archive filesystem free space"
  available=$((available_kib * 1024))
  (( available >= required )) || die "insufficient free space for verified archive: need $required bytes, have $available"
}

if [[ ! -d "$ARCHIVE_INPUT/world" ]]; then
  [[ -d "$ACTIVE_WORLD" && ! -L "$ACTIVE_WORLD" ]] || die "no canonical or staged old world is available"
  preflight_reset_capacity "$ACTIVE_WORLD"
  mv -T -- "$ACTIVE_WORLD" "$ARCHIVE_INPUT/world"; write_phase world-staged
fi
if [[ "$CURRENT_PHASE" == request-recorded ]]; then write_phase world-staged; fi


validate_world_binding() {
  local binding="$ARCHIVE_INPUT/world/data/world_lifecycle_manager/reset-binding-v5.tsv"
  load_contract "$binding" 'BC_PRESTIGE_WORLD_BINDING_V5' 9
  [[ "$(contract_value 1 lineage)" == "$REQUEST_LINEAGE" \
      && "$(contract_value 2 base_generation)" == "$BASE_GENERATION" \
      && "$(contract_value 3 transaction)" == "$REQUEST_TRANSACTION" \
      && "$(contract_value 4 world)" == "$REQUEST_WORLD" \
      && "$(contract_value 5 old_seed)" == "$REQUEST_OLD_SEED" \
      && "$(contract_value 6 biome_1)" == "$REQUEST_BIOME_1" \
      && "$(contract_value 7 biome_2)" == "$REQUEST_BIOME_2" \
      && "$(contract_value 8 biome_3)" == "$REQUEST_BIOME_3" ]] \
    || die "staged world binding does not match the committed reset"
}
validate_world_binding

if [[ ! -f "$FINAL_ARCHIVE" ]]; then
  [[ "$CURRENT_PHASE" == world-staged ]] || die "published archive is missing after archive verification"
  if ! (create_verified_archive "$ARCHIVE_INPUT" "$FINAL_ARCHIVE"); then
    rollback_archive_failure "$@"
  fi
  write_phase archive-verified
else
  if ! (
    [[ -f "$ARCHIVE_INPUT/world-lifecycle-manager-archive-manifest-v1.tsv" ]] || generate_archive_manifest "$ARCHIVE_INPUT/world" "$ARCHIVE_INPUT/world-lifecycle-manager-archive-manifest-v1.tsv"
    verify_archive_against_world "$FINAL_ARCHIVE" "$ARCHIVE_INPUT/world-lifecycle-manager-archive-manifest-v1.tsv"
    chmod 0444 -- "$FINAL_ARCHIVE"
    sync -f -- "$FINAL_ARCHIVE"
    ensure_archive_checksum "$FINAL_ARCHIVE"
  ); then
    rollback_archive_failure "$@"
  fi
  if [[ "$CURRENT_PHASE" == world-staged ]]; then write_phase archive-verified; fi
fi

START_ATTEMPT=1
if [[ "$CURRENT_PHASE" =~ ^attempt-([1-8])-(prepared|running)$ ]]; then
  interrupted_attempt="${BASH_REMATCH[1]}"; interrupted_state="${BASH_REMATCH[2]}"
  stop_failed_server
  if [[ "$interrupted_state" == running ]]; then START_ATTEMPT=$((interrupted_attempt+1)); else START_ATTEMPT="$interrupted_attempt"; fi
fi
if [[ -d "$ACTIVE_WORLD" ]]; then
  require_uninhabited_candidate "$ACTIVE_WORLD"
  quarantine="$TRANSACTION_ROOT/interrupted-successor-$(date +%s%N)"; mv -T -- "$ACTIVE_WORLD" "$quarantine"
fi

for ((attempt=START_ATTEMPT; attempt<=MAX_ATTEMPTS; attempt++)); do
  if [[ -d "$ACTIVE_WORLD" ]]; then
    require_uninhabited_candidate "$ACTIVE_WORLD"
    mv -T -- "$ACTIVE_WORLD" "$TRANSACTION_ROOT/failed-attempt-$((attempt-1))"
  fi
  ATTEMPT_SEED="$(random_seed)"
  if run_successor_attempt "$attempt" "$ATTEMPT_SEED" "$@"; then
    finalize_success
    set +e; wait "$SUCCESSOR_PID"; EXIT_CODE=$?; set -e
    stop_console_relay; clear_active_process
    if [[ -e "$RESET_FILE" ]]; then restart_supervisor "$@"; fi
    exit "$EXIT_CODE"
  fi
  event INFO "transaction=$REQUEST_TRANSACTION successor attempt=$attempt did not pass health verification; retrying"
  if [[ -f "$HEALTH_FILE" ]]; then cp -- "$HEALTH_FILE" "$TRANSACTION_ROOT/failed-attempt-$attempt-health-v5.tsv"; fi
  if [[ -f "$PERK_HEALTH_FILE" ]]; then cp -- "$PERK_HEALTH_FILE" "$TRANSACTION_ROOT/failed-attempt-$attempt-perk-health-v3.tsv"; fi
  cp -- "$SUCCESSOR_FILE" "$TRANSACTION_ROOT/failed-attempt-$attempt-request-v5.tsv"
  stop_failed_server; stop_console_relay
  require_uninhabited_candidate "$ACTIVE_WORLD"
done

if [[ -d "$ACTIVE_WORLD" ]]; then
  require_uninhabited_candidate "$ACTIVE_WORLD"
  mv -T -- "$ACTIVE_WORLD" "$TRANSACTION_ROOT/failed-attempt-$MAX_ATTEMPTS"
fi
cp -- "$TRANSACTION_ROOT/server.properties.before" "$SCRIPT_DIR/server.properties"
mv -T -- "$ARCHIVE_INPUT/world" "$ACTIVE_WORLD"
sync -f -- "$SCRIPT_DIR"
write_phase rolled-back
event WARN "transaction=$REQUEST_TRANSACTION restored old world after $MAX_ATTEMPTS failed successor attempts"
clear_active_process
cleanup_rolled_back_transaction
restart_supervisor "$@"

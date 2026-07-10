#!/usr/bin/env bash
#
# redact-tree.sh - Redact a directory tree with Philter Router.
#
# Walks --in recursively and POSTs each file to the Philter Router API
# (POST /api/filter). The redacted result is written under --out at the same
# relative path. Philter Router selects the policy and engine per file.
#
# Resumable: a file whose output already exists is skipped (use --force to
# re-redact). Runs several files at once (--jobs). Only curl and find are needed.

set -uo pipefail

usage() {
  cat <<'EOF'
Usage: redact-tree.sh --in DIR --out DIR [options]

  --in DIR         Input directory to walk (required).
  --out DIR        Output directory for redacted files (required).
  --url URL        Philter Router base URL (default: https://localhost:8080).
  --jobs N         Files to process at once (default: 4).
  --api-key KEY    Philter API key; sent as Authorization and forwarded to Philter.
  --policy NAME    Force a policy, overriding routing.
  --context NAME   Philter context.
  --force          Re-redact even when the output already exists.
  --insecure       Skip TLS verification (for the router's self-signed cert).
  -h, --help       Show this help.
EOF
}

IN="" OUT="" URL="https://localhost:8080" JOBS=4
API_KEY="" POLICY="" CONTEXT="" FORCE="" INSECURE=""

while [ $# -gt 0 ]; do
  case "$1" in
    --in)       IN="$2"; shift 2;;
    --out)      OUT="$2"; shift 2;;
    --url)      URL="$2"; shift 2;;
    --jobs)     JOBS="$2"; shift 2;;
    --api-key)  API_KEY="$2"; shift 2;;
    --policy)   POLICY="$2"; shift 2;;
    --context)  CONTEXT="$2"; shift 2;;
    --force)    FORCE=1; shift;;
    --insecure) INSECURE=1; shift;;
    -h|--help)  usage; exit 0;;
    *) echo "Unknown option: $1" >&2; usage; exit 2;;
  esac
done

[ -n "$IN" ] && [ -n "$OUT" ] || { echo "Error: --in and --out are required." >&2; usage; exit 2; }
[ -d "$IN" ] || { echo "Error: input directory not found: $IN" >&2; exit 2; }
command -v curl >/dev/null || { echo "Error: curl is required." >&2; exit 2; }

IN="${IN%/}"; URL="${URL%/}"; OUT="${OUT%/}"

# Workers append one short line each; short appends are atomic, so the tally is safe.
STATUS_LOG="$(mktemp)"
trap 'rm -f "$STATUS_LOG"' EXIT

# Percent-encode for a query value. LC_ALL=C makes the loop iterate bytes so UTF-8 encodes correctly.
urlencode() {
  local LC_ALL=C s="$1" i c out=""
  for (( i=0; i<${#s}; i++ )); do
    c="${s:$i:1}"
    case "$c" in
      [a-zA-Z0-9.~_-]) out+="$c";;
      *) printf -v c '%%%02X' "'$c"; out+="$c";;
    esac
  done
  printf '%s' "$out"
}

redact_one() {
  local file="$1"
  local rel="${file#"$IN"/}"
  local out_file="$OUT/$rel"

  if [ -e "$out_file" ] && [ -z "$FORCE" ]; then
    printf 'SKIP\t%s\n' "$rel" >>"$STATUS_LOG"
    return
  fi

  if ! mkdir -p "$(dirname "$out_file")"; then
    printf 'FAIL\t-\t%s\n' "$rel" >>"$STATUS_LOG"
    return
  fi

  local name dir query
  name="$(basename "$rel")"
  dir="$(dirname "$rel")"
  query="filename=$(urlencode "$name")"
  [ -n "$POLICY" ]  && query="$query&p=$(urlencode "$POLICY")"
  [ -n "$CONTEXT" ] && query="$query&c=$(urlencode "$CONTEXT")"

  local -a opts=(-sS -X POST --data-binary "@$file" -o "$out_file.part" -w '%{http_code}')
  [ -n "$INSECURE" ] && opts+=(-k)
  [ -n "$API_KEY" ]  && opts+=(-H "Authorization: $API_KEY")
  [ "$dir" != "." ]  && opts+=(-H "X-Source-Directory: $dir")

  local code
  code="$(curl "${opts[@]}" "$URL/api/filter?$query")"
  if [ "$code" = "200" ]; then
    mv "$out_file.part" "$out_file"
    printf 'OK\t%s\n' "$rel" >>"$STATUS_LOG"
  else
    rm -f "$out_file.part"
    printf 'FAIL\t%s\t%s\n' "$code" "$rel" >>"$STATUS_LOG"
    echo "FAIL ($code): $rel" >&2
  fi
}

export -f redact_one urlencode
export IN OUT URL API_KEY POLICY CONTEXT FORCE INSECURE STATUS_LOG

find "$IN" -type f -print0 | xargs -0 -P "$JOBS" -I{} bash -c 'redact_one "$@"' _ {}

ok=$(grep -c '^OK'   "$STATUS_LOG" || true)
skip=$(grep -c '^SKIP' "$STATUS_LOG" || true)
fail=$(grep -c '^FAIL' "$STATUS_LOG" || true)
echo "Done. redacted=$ok skipped=$skip failed=$fail"
[ "$fail" -eq 0 ]

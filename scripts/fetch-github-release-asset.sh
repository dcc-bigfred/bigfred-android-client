#!/usr/bin/env bash
# Download a single asset from the latest GitHub release of a repo.
# Usage: ./scripts/fetch-github-release-asset.sh <owner/repo> <asset-name> <output-path>
#
# Auth (optional, needed for private repos):
#   GITHUB_TOKEN / GH_TOKEN / BIGFRED_NATIVE_TOKEN
set -euo pipefail

REPO="${1:?usage: $0 <owner/repo> <asset-name> <output-path>}"
ASSET="${2:?}"
OUT="${3:?}"

mkdir -p "$(dirname "${OUT}")"

TOKEN="${BIGFRED_NATIVE_TOKEN:-${GH_TOKEN:-${GITHUB_TOKEN:-}}}"
AUTH=()
if [[ -n "${TOKEN}" ]]; then
  AUTH=(-H "Authorization: Bearer ${TOKEN}")
fi

API="https://api.github.com/repos/${REPO}/releases/latest"
echo "Resolving latest release of ${REPO}…"
json="$(curl -fsSL "${AUTH[@]}" -H "Accept: application/vnd.github+json" "${API}")"

tag="$(printf '%s' "${json}" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tag_name",""))')"
url="$(printf '%s' "${json}" | python3 -c '
import json,sys
asset=sys.argv[1]
data=json.load(sys.stdin)
for a in data.get("assets") or []:
    if a.get("name")==asset:
        print(a.get("url") or "")
        break
' "${ASSET}")"

if [[ -z "${url}" ]]; then
  echo "error: asset '${ASSET}' not found in latest release of ${REPO} (tag=${tag:-unknown})" >&2
  echo "Available assets:" >&2
  printf '%s' "${json}" | python3 -c 'import json,sys; [print(" -",a.get("name")) for a in (json.load(sys.stdin).get("assets") or [])]' >&2
  exit 1
fi

echo "Downloading ${ASSET} from ${REPO}@${tag}"
tmp="$(mktemp)"
cleanup() { rm -f "${tmp}"; }
trap cleanup EXIT

# Asset API URL requires Accept: application/octet-stream
curl -fsSL "${AUTH[@]}" \
  -H "Accept: application/octet-stream" \
  -L \
  -o "${tmp}" \
  "${url}"

cp -f "${tmp}" "${OUT}"
chmod 755 "${OUT}"
echo "Wrote ${OUT} ($(wc -c < "${OUT}") bytes) from ${REPO}@${tag}"

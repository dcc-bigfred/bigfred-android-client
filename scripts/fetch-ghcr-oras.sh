#!/usr/bin/env bash
# Pull loco-server-android-arm64 from GHCR (ORAS) and write libloco-server.so.
# Usage: fetch-ghcr-oras.sh <image> <tag> <output-path> [fallback-tag ...]
#
# Auth (optional; needed for private packages):
#   GITHUB_TOKEN / GH_TOKEN / BIGFRED_NATIVE_TOKEN
set -euo pipefail

IMAGE="${1:?usage: $0 <image> <tag> <output-path> [fallback-tag ...]}"
TAG="${2:?}"
OUT="${3:?}"
shift 3
FALLBACK_TAGS=("$@")

TOKEN="${BIGFRED_NATIVE_TOKEN:-${GH_TOKEN:-${GITHUB_TOKEN:-}}}"
if [[ -n "${TOKEN}" ]]; then
  USER="${GITHUB_ACTOR:-oauth2}"
  echo "${TOKEN}" | oras login ghcr.io -u "${USER}" --password-stdin >/dev/null
fi

tmpdir="$(mktemp -d)"
cleanup() { rm -rf "${tmpdir}"; }
trap cleanup EXIT

pull_tag() {
  local t="$1"
  rm -rf "${tmpdir:?}"/*
  mkdir -p "${tmpdir}"
  echo "Pulling ${IMAGE}:${t}…"
  oras pull "${IMAGE}:${t}" -o "${tmpdir}"
}

if ! pull_tag "${TAG}"; then
  pulled=0
  for fb in "${FALLBACK_TAGS[@]}"; do
    if pull_tag "${fb}"; then
      pulled=1
      break
    fi
  done
  if [[ "${pulled}" -eq 0 ]]; then
    echo "error: could not pull ${IMAGE}:${TAG} (tried fallbacks: ${FALLBACK_TAGS[*]:-none})" >&2
    exit 1
  fi
fi

src="${tmpdir}/loco-server-android-arm64"
if [[ ! -f "${src}" ]]; then
  mapfile -t files < <(find "${tmpdir}" -type f ! -name 'manifest.json' ! -name 'config.json')
  if [[ ${#files[@]} -eq 1 ]]; then
    src="${files[0]}"
  else
    echo "error: expected loco-server-android-arm64 in OCI artifact, found:" >&2
    find "${tmpdir}" -type f >&2
    exit 1
  fi
fi

mkdir -p "$(dirname "${OUT}")"
cp -f "${src}" "${OUT}"
chmod 755 "${OUT}"
echo "Wrote ${OUT} ($(wc -c < "${OUT}") bytes) from ${IMAGE}:${TAG}"

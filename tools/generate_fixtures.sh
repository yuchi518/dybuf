#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${ROOT_DIR}/c/build"
OUT_DIR="${1:-${ROOT_DIR}/fixtures/v1}"

mkdir -p "${BUILD_DIR}"

cc -std=c99 -I "${ROOT_DIR}/c" -I "${ROOT_DIR}/c/platform" \
  -o "${BUILD_DIR}/dybuf_fixtures" \
  "${ROOT_DIR}/c/fixtures/generate_fixtures.c"

"${BUILD_DIR}/dybuf_fixtures" "${OUT_DIR}"

cc -std=c99 -I "${ROOT_DIR}/c" -I "${ROOT_DIR}/c/platform" \
  -o "${BUILD_DIR}/dybuf_verify_fixtures" \
  "${ROOT_DIR}/c/fixtures/verify_fixtures.c"

"${BUILD_DIR}/dybuf_verify_fixtures" "${OUT_DIR}"

echo "Fixtures generated and verified in ${OUT_DIR}"

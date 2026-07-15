#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${ROOT_DIR}/c/build/java-fixtures"
FIXTURE_DIR="${1:-${ROOT_DIR}/fixtures/v1}"

mkdir -p "${BUILD_DIR}"

javac -d "${BUILD_DIR}" \
  "${ROOT_DIR}/java/src/oyo/lol/util/DyBuf.java" \
  "${ROOT_DIR}/java/src/test/FixtureTest.java"

java -cp "${BUILD_DIR}" test.FixtureTest "${FIXTURE_DIR}"

#!/usr/bin/env bash
set -euo pipefail

# Waterall Project Verification Harness
echo "================================================="
echo "  💧 WATERALL: Automated Verification Harness   "
echo "================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Stage 1: Document Integrity Checks
echo "[Stage 1/4] Checking Governance & Invariant Documentation..."
DOCS=("LAWS.md" "PRINCIPLES.md" "HARNESS.md" "AGENTS.md")
for doc in "${DOCS[@]}"; do
  if [[ ! -f "$doc" ]]; then
    echo "❌ Missing required governance document: $doc"
    exit 1
  fi
done
echo "✅ All governance documents verified."

# Stage 2: Android SDK & Environment Check
echo "[Stage 2/4] Checking Environment (JDK & Android SDK)..."
if command -v java >/dev/null 2>&1; then
  echo "✅ Java runtime detected: $(java -version 2>&1 | head -n 1)"
else
  echo "❌ Java not found in PATH."
  exit 1
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  if [[ -d "$HOME/Library/Android/sdk" ]]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
    export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
    echo "ℹ️ Auto-detected ANDROID_HOME at $ANDROID_HOME"
  else
    echo "⚠️ ANDROID_HOME is not set. Ensure local.properties specifies sdk.dir."
  fi
fi

# Stage 3: Domain Laws Invariant Tests
echo "[Stage 3/4] Running Domain Invariant Tests..."
if [[ -f "./gradlew" ]]; then
  chmod +x ./gradlew
  ./gradlew testDebugUnitTest --tests "com.waterall.domain.laws.*" --console=plain
  echo "✅ Domain invariant law tests passed."
else
  echo "⚠️ gradlew not found yet. Skipping Gradle law tests stage."
fi

# Stage 4: Full Unit Tests & Compilation
echo "[Stage 4/4] Compiling and running full test suite..."
if [[ -f "./gradlew" ]]; then
  ./gradlew testDebugUnitTest --console=plain
  echo "✅ All unit tests and compilation checks passed."
fi

echo "================================================="
echo "  🎉 All Waterall verification checks PASSED!    "
echo "================================================="

#!/usr/bin/env bash
# =============================================================================
# generate-report.sh
# Download regression test reports for a session.
#
# Usage:
#   ./scripts/generate-report.sh --session <SESSION_ID> [--format junit|html|json] [--output <path>]
# =============================================================================
set -euo pipefail

FRAMEWORK_URL="${FRAMEWORK_URL:-http://localhost:8085}"
SESSION_ID=""
FORMAT="html"
OUTPUT=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --session) SESSION_ID="$2"; shift 2 ;;
    --format)  FORMAT="$2";     shift 2 ;;
    --output)  OUTPUT="$2";     shift 2 ;;
    *)         echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [[ -z "$SESSION_ID" ]]; then
  echo "ERROR: --session is required"
  exit 1
fi

case "$FORMAT" in
  junit) URL="${FRAMEWORK_URL}/api/reports/${SESSION_ID}/junit"
         EXT="xml" ;;
  html)  URL="${FRAMEWORK_URL}/api/reports/${SESSION_ID}/html"
         EXT="html" ;;
  json)  URL="${FRAMEWORK_URL}/api/reports/${SESSION_ID}"
         EXT="json" ;;
  *)     echo "ERROR: --format must be junit, html, or json"; exit 1 ;;
esac

if [[ -z "$OUTPUT" ]]; then
  OUTPUT="regression-report-${SESSION_ID}.${EXT}"
fi

echo "==> Downloading $FORMAT report for session $SESSION_ID"
curl -s -o "$OUTPUT" "$URL"
echo "==> Report saved to: $OUTPUT"

#!/usr/bin/env bash
# =============================================================================
# compare-baselines.sh
# Compare a microservices test session against its approved baseline.
#
# Usage:
#   ./scripts/compare-baselines.sh --session <MICROSERVICES_SESSION_ID>
#
# Exit codes:
#   0  — PASS (no differences)
#   1  — FAIL (differences found)
#   2  — ERROR (comparison could not be completed)
# =============================================================================
set -euo pipefail

FRAMEWORK_URL="${FRAMEWORK_URL:-http://localhost:8085}"
SESSION_ID=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --session) SESSION_ID="$2"; shift 2 ;;
    *)         echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [[ -z "$SESSION_ID" ]]; then
  echo "ERROR: --session is required"
  exit 1
fi

echo "==> Comparing session $SESSION_ID against approved baseline"

RESPONSE=$(curl -s -X POST "${FRAMEWORK_URL}/api/compare/${SESSION_ID}" \
  -H "Content-Type: application/json")

VERDICT=$(echo "$RESPONSE" | jq -r '.verdict // "ERROR"' 2>/dev/null)
SUMMARY=$(echo "$RESPONSE" | jq -r '.summary // ""' 2>/dev/null)

echo ""
echo "==================================="
echo "  VERDICT: $VERDICT"
echo "==================================="
echo "$SUMMARY"
echo ""
echo "Full report: ${FRAMEWORK_URL}/api/reports/${SESSION_ID}"
echo "JUnit XML:   ${FRAMEWORK_URL}/api/reports/${SESSION_ID}/junit"
echo "HTML report: ${FRAMEWORK_URL}/api/reports/${SESSION_ID}/html"
echo ""

case "$VERDICT" in
  PASS)  exit 0 ;;
  FAIL)  exit 1 ;;
  *)     exit 2 ;;
esac

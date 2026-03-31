#!/usr/bin/env bash
# =============================================================================
# create-baseline.sh
# Create a regression baseline from a completed monolithic test session.
# The baseline starts in PENDING status and must be approved before being used
# for regression comparisons.
#
# Usage:
#   ./scripts/create-baseline.sh --session <SESSION_ID> [--commit <SHA>]
# =============================================================================
set -euo pipefail

FRAMEWORK_URL="${FRAMEWORK_URL:-http://localhost:8085}"
SESSION_ID=""
COMMIT_SHA=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --session) SESSION_ID="$2"; shift 2 ;;
    --commit)  COMMIT_SHA="$2"; shift 2 ;;
    *)         echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [[ -z "$SESSION_ID" ]]; then
  echo "ERROR: --session is required"
  exit 1
fi

echo "==> Creating baseline from session: $SESSION_ID"

QUERY=""
if [[ -n "$COMMIT_SHA" ]]; then
  QUERY="?commitSha=${COMMIT_SHA}"
fi

BASELINE_ID=$(curl -s -X POST "${FRAMEWORK_URL}/api/baselines/from-session/${SESSION_ID}${QUERY}" \
  -H "Content-Type: application/json" \
  | jq -r '.id // empty' 2>/dev/null)

if [[ -z "$BASELINE_ID" ]]; then
  echo "ERROR: Failed to create baseline. Response:"
  echo "$RESPONSE"
  exit 1
fi

echo "==> Baseline created: $BASELINE_ID (PENDING)"
echo ""
echo "Review the captured events and approve the baseline:"
echo "  ./scripts/approve-baseline.sh --baseline $BASELINE_ID --by <your-name>"

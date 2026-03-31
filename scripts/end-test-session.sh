#!/usr/bin/env bash
# =============================================================================
# end-test-session.sh
# End an active CDC regression capture session.
# The framework will collect all CDC events within the session window and store them.
#
# Usage:
#   ./scripts/end-test-session.sh --session <SESSION_ID>
#
# The session will transition to CAPTURING → COMPLETED asynchronously.
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

echo "==> Ending CDC capture session: $SESSION_ID"

STATUS=$(curl -s -X POST "${FRAMEWORK_URL}/api/sessions/${SESSION_ID}/end" \
  -H "Content-Type: application/json" \
  | jq -r '.status // empty' 2>/dev/null)

echo "==> Session status: $STATUS"
echo ""
echo "CDC events are being captured asynchronously."
echo "Poll status: curl ${FRAMEWORK_URL}/api/sessions/${SESSION_ID}"
echo ""
echo "Once COMPLETED, create a baseline:"
echo "  ./scripts/create-baseline.sh --session $SESSION_ID"

#!/usr/bin/env bash
# =============================================================================
# approve-baseline.sh
# Approve a PENDING regression baseline so it can be used for comparisons.
#
# Usage:
#   ./scripts/approve-baseline.sh \
#     --baseline <BASELINE_ID> \
#     --by       <approver-name> \
#     [--notes   "Verified against sprint 42 acceptance criteria"]
# =============================================================================
set -euo pipefail

FRAMEWORK_URL="${FRAMEWORK_URL:-http://localhost:8085}"
BASELINE_ID=""
APPROVED_BY=""
NOTES=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --baseline) BASELINE_ID="$2"; shift 2 ;;
    --by)       APPROVED_BY="$2"; shift 2 ;;
    --notes)    NOTES="$2";       shift 2 ;;
    *)          echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [[ -z "$BASELINE_ID" || -z "$APPROVED_BY" ]]; then
  echo "ERROR: --baseline and --by are required"
  exit 1
fi

BODY=$(cat <<-EOF
{
  "approvedBy": "$APPROVED_BY",
  "notes":      "$NOTES"
}
EOF
)

echo "==> Approving baseline: $BASELINE_ID (by $APPROVED_BY)"

STATUS=$(curl -s -X POST "${FRAMEWORK_URL}/api/baselines/${BASELINE_ID}/approve" \
  -H "Content-Type: application/json" \
  -d "$BODY" \
  | jq -r '.status // empty' 2>/dev/null)

echo "==> Baseline status: $STATUS"
echo ""
echo "The baseline is now active and will be used for regression comparisons."

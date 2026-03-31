#!/usr/bin/env bash
# =============================================================================
# start-test-session.sh
# Start a new CDC regression capture session.
#
# Usage:
#   ./scripts/start-test-session.sh \
#     --name "book_domestic_flight_one_way" \
#     --env  MONOLITH \
#     [--tag  "sprint=42"] \
#     [--tag  "jira=AIRL-101"] \
#     [--commit "abc123"]
#
# Output: prints the Session ID which must be passed to end-test-session.sh
# =============================================================================
set -euo pipefail

FRAMEWORK_URL="${FRAMEWORK_URL:-http://localhost:8085}"
TEST_CASE_NAME=""
ENVIRONMENT="MONOLITH"
COMMIT_SHA=""
TAGS=()

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --name)    TEST_CASE_NAME="$2"; shift 2 ;;
    --env)     ENVIRONMENT="$2";    shift 2 ;;
    --commit)  COMMIT_SHA="$2";     shift 2 ;;
    --tag)     TAGS+=("$2");        shift 2 ;;
    *)         echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [[ -z "$TEST_CASE_NAME" ]]; then
  echo "ERROR: --name is required"
  exit 1
fi

# Build tags JSON object
TAGS_JSON="{"
SEP=""
for tag in "${TAGS[@]}"; do
  KEY="${tag%%=*}"
  VAL="${tag#*=}"
  TAGS_JSON+="${SEP}\"${KEY}\": \"${VAL}\""
  SEP=","
done
TAGS_JSON+="}"

# Build request body
BODY=$(cat <<-EOF
{
  "testCaseName": "$TEST_CASE_NAME",
  "environment":  "$ENVIRONMENT",
  "commitSha":    "$COMMIT_SHA",
  "tags":         $TAGS_JSON
}
EOF
)

echo "==> Starting CDC capture session for: $TEST_CASE_NAME ($ENVIRONMENT)"

SESSION_ID=$(curl -s -X POST "${FRAMEWORK_URL}/api/sessions" \
  -H "Content-Type: application/json" \
  -d "$BODY" \
  | jq -r '.id // empty' 2>/dev/null)

if [[ -z "$SESSION_ID" ]]; then
  echo "ERROR: Failed to start session. Response:"
  echo "$RESPONSE"
  exit 1
fi

echo "==> Session started: $SESSION_ID"
echo ""
echo "Run your test scenario now, then execute:"
echo "  ./scripts/end-test-session.sh --session $SESSION_ID"
echo ""
echo "$SESSION_ID"

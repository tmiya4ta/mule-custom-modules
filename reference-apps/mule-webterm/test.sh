#!/bin/bash
# Integration tests for mule-webterm
# Usage: ./test.sh [base_url] [password]
# Example: ./test.sh https://mule-webterm-znutqp.pnwfdv.jpn-e1.cloudhub.io test123

BASE_URL="${1:-http://localhost:8081}"
PASSWORD="${2:-test123}"
PASS=0
FAIL=0

green() { echo -e "\033[32m$1\033[0m"; }
red()   { echo -e "\033[31m$1\033[0m"; }

assert_eq() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    green "  PASS: $desc (=$expected)"
    ((PASS++))
  else
    red "  FAIL: $desc (expected=$expected, actual=$actual)"
    ((FAIL++))
  fi
}

assert_contains() {
  local desc="$1" needle="$2" haystack="$3"
  if echo "$haystack" | grep -q "$needle"; then
    green "  PASS: $desc (contains '$needle')"
    ((PASS++))
  else
    red "  FAIL: $desc (missing '$needle')"
    ((FAIL++))
  fi
}

echo "=== mule-webterm integration tests ==="
echo "Base URL: $BASE_URL"
echo ""

# ── Health check ──
echo "--- Health Check ---"
HEALTH=$(curl -s "$BASE_URL/health")
STATUS=$(echo "$HEALTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
assert_eq "health status" "UP" "$STATUS"

# ── HTML UI served ──
echo ""
echo "--- HTML UI ---"
HTML=$(curl -s "$BASE_URL/")
assert_contains "serves HTML" "xterm" "$HTML"
assert_contains "has exec key button" "execKeyBtn" "$HTML"

# ── Exec key not set initially ──
echo ""
echo "--- Exec Key API ---"
RESULT=$(curl -s "$BASE_URL/api/exec-key")
IS_SET=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('set',''))" 2>/dev/null)
assert_eq "exec key not set initially" "False" "$IS_SET"

# ── Generate exec key ──
RESULT=$(curl -s -X POST "$BASE_URL/api/exec-key" \
  -H 'Content-Type: application/json' \
  -d "{\"password\":\"$PASSWORD\"}")
KEY=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('key',''))" 2>/dev/null)
if [ -n "$KEY" ] && [ "$KEY" != "" ]; then
  green "  PASS: exec key generated (${KEY:0:4}...)"
  ((PASS++))
else
  red "  FAIL: exec key not generated"
  ((FAIL++))
fi

# ── Wrong password rejected ──
RESULT=$(curl -s -X POST "$BASE_URL/api/exec-key" \
  -H 'Content-Type: application/json' \
  -d '{"password":"wrongpassword"}')
ERROR=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('error',''))" 2>/dev/null)
assert_eq "wrong password rejected" "Invalid password." "$ERROR"

# ── Exec command ──
echo ""
echo "--- Exec API ---"
RESULT=$(curl -s -X POST "$BASE_URL/api/exec" \
  -H 'Content-Type: application/json' \
  -d "{\"command\":\"echo hello-test\",\"key\":\"$KEY\"}")
OUTPUT=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('output','').strip())" 2>/dev/null)
assert_eq "exec echo output" "hello-test" "$OUTPUT"

EXIT_CODE=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('exitCode',''))" 2>/dev/null)
assert_eq "exec exit code" "0" "$EXIT_CODE"

# ── Exec with invalid key ──
RESULT=$(curl -s -X POST "$BASE_URL/api/exec" \
  -H 'Content-Type: application/json' \
  -d '{"command":"echo fail","key":"invalidkey"}')
ERROR=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('error',''))" 2>/dev/null)
assert_contains "invalid key rejected" "Invalid" "$ERROR"

# ── Terminal session ──
echo ""
echo "--- Terminal Session ---"
# Init terminal
curl -s -X POST "$BASE_URL/api/term/input" \
  -H 'Content-Type: application/json' \
  -d "{\"cmd\":\"\",\"key\":\"$KEY\"}" > /dev/null

sleep 1

# Send command
curl -s -X POST "$BASE_URL/api/term/input" \
  -H 'Content-Type: application/json' \
  -d "{\"cmd\":\"echo TERM_TEST_OK\r\",\"key\":\"$KEY\"}" > /dev/null

sleep 1

# Read output
RESULT=$(curl -s -X POST "$BASE_URL/api/term/read" \
  -H 'Content-Type: application/json' \
  -d "{\"key\":\"$KEY\"}")
ALIVE=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('alive',''))" 2>/dev/null)
assert_eq "terminal alive" "True" "$ALIVE"

OUTPUT=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('output',''))" 2>/dev/null)
assert_contains "terminal output contains echo" "TERM_TEST_OK" "$OUTPUT"

# ── File list ──
echo ""
echo "--- File API ---"
RESULT=$(curl -s -X POST "$BASE_URL/api/file/list" \
  -H 'Content-Type: application/json' \
  -d "{\"path\":\"/tmp\",\"key\":\"$KEY\"}")
HAS_FILES=$(echo "$RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print('yes' if 'files' in d else 'no')" 2>/dev/null)
assert_eq "file list returns files" "yes" "$HAS_FILES"

# ── File upload + download roundtrip ──
echo ""
echo "--- File Upload/Download Roundtrip ---"
TEST_CONTENT="hello-webterm-test-$(date +%s)"
B64=$(echo -n "$TEST_CONTENT" | base64)

# Upload
RESULT=$(curl -s -X POST "$BASE_URL/api/file/upload" \
  -H 'Content-Type: application/json' \
  -d "{\"path\":\"/tmp/webterm-test.txt\",\"content\":\"$B64\",\"key\":\"$KEY\"}")
UPLOAD_OK=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('ok',''))" 2>/dev/null)
assert_eq "file upload ok" "True" "$UPLOAD_OK"

# Download
RESULT=$(curl -s -X POST "$BASE_URL/api/file/download" \
  -H 'Content-Type: application/json' \
  -d "{\"path\":\"/tmp/webterm-test.txt\",\"key\":\"$KEY\"}")
DOWNLOADED=$(echo "$RESULT" | python3 -c "import sys,json,base64; print(base64.b64decode(json.load(sys.stdin).get('content','')).decode())" 2>/dev/null)
assert_eq "download matches upload" "$TEST_CONTENT" "$DOWNLOADED"

# ── Summary ──
echo ""
echo "=============================="
echo "Results: $PASS passed, $FAIL failed"
echo "=============================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

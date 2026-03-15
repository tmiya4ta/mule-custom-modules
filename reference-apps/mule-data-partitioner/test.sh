#!/bin/bash
# Integration tests for mule-data-partitioner
# Usage: ./test.sh [base_url]
# Example: ./test.sh https://mule-data-partitioner-znutqp.pnwfdv.jpn-e1.cloudhub.io

BASE_URL="${1:-http://localhost:8081}"
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

assert_gt() {
  local desc="$1" threshold="$2" actual="$3"
  if [ "$actual" -gt "$threshold" ] 2>/dev/null; then
    green "  PASS: $desc ($actual > $threshold)"
    ((PASS++))
  else
    red "  FAIL: $desc (expected > $threshold, actual=$actual)"
    ((FAIL++))
  fi
}

echo "=== mule-data-partitioner integration tests ==="
echo "Base URL: $BASE_URL"
echo ""

# ── Health check ──
echo "--- Health Check ---"
HEALTH=$(curl -s "$BASE_URL/health")
STATUS=$(echo "$HEALTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
assert_eq "health status" "UP" "$STATUS"

# ── CSV partition (small, 1KB) ──
echo ""
echo "--- CSV Partition (100 rows, 1KB partitions) ---"
CSV_DATA=$(python3 -c "
print('id,name,email,dept,salary')
for i in range(1,101):
    print(f'{i},user_{i},user_{i}@example.com,dept_{i%10},{50000+i*100}')
")
RESULT=$(echo "$CSV_DATA" | curl -s -X POST "$BASE_URL/test/csv?partitionSize=1&sizeUnit=KB" \
  -H 'Content-Type: text/csv' --data-binary @-)
TOTAL=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalPartitions',0))" 2>/dev/null)
assert_gt "CSV partitions created" 1 "$TOTAL"

# Verify total lines = 100 data + N headers
TOTAL_LINES=$(echo "$RESULT" | python3 -c "
import sys,json
d=json.load(sys.stdin)
total=sum(p[0]['lines'] for p in d['partitions'])
print(total)
" 2>/dev/null)
EXPECTED_LINES=$((100 + TOTAL))  # 100 data + 1 header per partition
assert_eq "CSV total lines (data+headers)" "$EXPECTED_LINES" "$TOTAL_LINES"

# ── CSV single partition (all fits) ──
echo ""
echo "--- CSV Single Partition (100 rows, 10KB partition) ---"
RESULT=$(echo "$CSV_DATA" | curl -s -X POST "$BASE_URL/test/csv?partitionSize=10&sizeUnit=KB" \
  -H 'Content-Type: text/csv' --data-binary @-)
TOTAL=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalPartitions',0))" 2>/dev/null)
assert_eq "all data fits in 1 partition" "1" "$TOTAL"

# ── JSON partition (small, 1KB) ──
echo ""
echo "--- JSON Partition (50 objects, 1KB partitions) ---"
JSON_DATA=$(python3 -c "
import json
data=[{'id':i,'name':f'user_{i}','email':f'user_{i}@example.com','dept':f'dept_{i%10}','salary':50000+i*100} for i in range(50)]
print(json.dumps(data))
")
RESULT=$(echo "$JSON_DATA" | curl -s -X POST "$BASE_URL/test/json?partitionSize=1&sizeUnit=KB" \
  -H 'Content-Type: application/json' --data-binary @-)
TOTAL=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalPartitions',0))" 2>/dev/null)
assert_gt "JSON partitions created" 1 "$TOTAL"

# Verify total items = 50
TOTAL_ITEMS=$(echo "$RESULT" | python3 -c "
import sys,json
d=json.load(sys.stdin)
total=sum(p[0]['items'] for p in d['partitions'])
print(total)
" 2>/dev/null)
assert_eq "JSON total items" "50" "$TOTAL_ITEMS"

# ── CSV large partition size (verify partition size boundary) ──
echo ""
echo "--- CSV Partition Size Boundary (100 rows, 500B partitions) ---"
RESULT=$(echo "$CSV_DATA" | curl -s -X POST "$BASE_URL/test/csv?partitionSize=500&sizeUnit=KB" \
  -H 'Content-Type: text/csv' --data-binary @-)
TOTAL=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalPartitions',0))" 2>/dev/null)
assert_eq "500KB partition fits all" "1" "$TOTAL"

# ── JSON single partition ──
echo ""
echo "--- JSON Single Partition (50 objects, 100KB partition) ---"
RESULT=$(echo "$JSON_DATA" | curl -s -X POST "$BASE_URL/test/json?partitionSize=100&sizeUnit=KB" \
  -H 'Content-Type: application/json' --data-binary @-)
TOTAL=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalPartitions',0))" 2>/dev/null)
assert_eq "100KB partition fits all JSON" "1" "$TOTAL"

# ── Summary ──
echo ""
echo "=============================="
echo "Results: $PASS passed, $FAIL failed"
echo "=============================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

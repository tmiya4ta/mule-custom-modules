#!/bin/bash
# Integration tests for mule-jmx-metrics
# Usage: ./test.sh [base_url]
# Example: ./test.sh https://mule-jmx-metrics-znutqp.pnwfdv.jpn-e1.cloudhub.io

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

assert_exists() {
  local desc="$1" value="$2"
  if [ -n "$value" ] && [ "$value" != "None" ] && [ "$value" != "null" ] && [ "$value" != "" ]; then
    green "  PASS: $desc (=$value)"
    ((PASS++))
  else
    red "  FAIL: $desc (missing or null)"
    ((FAIL++))
  fi
}

echo "=== mule-jmx-metrics integration tests ==="
echo "Base URL: $BASE_URL"
echo ""

# ── Health check ──
echo "--- Health Check ---"
HEALTH=$(curl -s "$BASE_URL/health")
STATUS=$(echo "$HEALTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
assert_eq "health status" "UP" "$STATUS"

# ── All metrics ──
echo ""
echo "--- /metrics (all) ---"
METRICS=$(curl -s "$BASE_URL/metrics")

TIMESTAMP=$(echo "$METRICS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('timestamp',0))" 2>/dev/null)
assert_gt "timestamp present" 0 "$TIMESTAMP"

# OS
OS_NAME=$(echo "$METRICS" | python3 -c "import sys,json; print(json.load(sys.stdin)['os']['name'])" 2>/dev/null)
assert_exists "os.name" "$OS_NAME"

PROCS=$(echo "$METRICS" | python3 -c "import sys,json; print(json.load(sys.stdin)['os']['availableProcessors'])" 2>/dev/null)
assert_gt "os.availableProcessors" 0 "$PROCS"

# Memory
HEAP_USED=$(echo "$METRICS" | python3 -c "import sys,json; print(json.load(sys.stdin)['memory']['heap']['used'])" 2>/dev/null)
assert_gt "memory.heap.used" 0 "$HEAP_USED"

HEAP_MAX=$(echo "$METRICS" | python3 -c "import sys,json; print(json.load(sys.stdin)['memory']['heap']['max'])" 2>/dev/null)
assert_gt "memory.heap.max" 0 "$HEAP_MAX"

# Threads
THREAD_COUNT=$(echo "$METRICS" | python3 -c "import sys,json; print(json.load(sys.stdin)['threads']['threadCount'])" 2>/dev/null)
assert_gt "threads.threadCount" 0 "$THREAD_COUNT"

# GC
GC_COUNT=$(echo "$METRICS" | python3 -c "import sys,json; gc=json.load(sys.stdin)['gc']; print(len(gc))" 2>/dev/null)
assert_gt "gc collectors present" 0 "$GC_COUNT"

# Runtime
VM_NAME=$(echo "$METRICS" | python3 -c "import sys,json; print(json.load(sys.stdin)['runtime']['vmName'])" 2>/dev/null)
assert_exists "runtime.vmName" "$VM_NAME"

UPTIME=$(echo "$METRICS" | python3 -c "import sys,json; print(json.load(sys.stdin)['runtime']['uptime'])" 2>/dev/null)
assert_gt "runtime.uptime" 0 "$UPTIME"

# ── CPU endpoint ──
echo ""
echo "--- /metrics/cpu ---"
CPU=$(curl -s "$BASE_URL/metrics/cpu")
LOAD=$(echo "$CPU" | python3 -c "import sys,json; print(json.load(sys.stdin).get('systemLoadAverage',-1))" 2>/dev/null)
assert_exists "cpu.systemLoadAverage" "$LOAD"

CPU_LOAD=$(echo "$CPU" | python3 -c "import sys,json; v=json.load(sys.stdin).get('processCpuLoad',-1); print('yes' if v >= 0 else 'no')" 2>/dev/null)
assert_eq "cpu.processCpuLoad >= 0" "yes" "$CPU_LOAD"

# ── Memory endpoint ──
echo ""
echo "--- /metrics/memory ---"
MEM=$(curl -s "$BASE_URL/metrics/memory")
POOLS=$(echo "$MEM" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('memoryPools',[])))" 2>/dev/null)
assert_gt "memoryPools count" 0 "$POOLS"

# ── GC endpoint ──
echo ""
echo "--- /metrics/gc ---"
GC=$(curl -s "$BASE_URL/metrics/gc")
GC_NAME=$(echo "$GC" | python3 -c "import sys,json; print(json.load(sys.stdin)[0].get('name',''))" 2>/dev/null)
assert_exists "gc[0].name" "$GC_NAME"

# ── Threads endpoint ──
echo ""
echo "--- /metrics/threads ---"
THREADS=$(curl -s "$BASE_URL/metrics/threads")
STATES=$(echo "$THREADS" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('states',{})))" 2>/dev/null)
assert_gt "thread states count" 0 "$STATES"

# ── Summary ──
echo ""
echo "=============================="
echo "Results: $PASS passed, $FAIL failed"
echo "=============================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

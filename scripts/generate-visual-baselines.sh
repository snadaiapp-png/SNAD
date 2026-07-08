#!/bin/bash
# SNAD Visual Regression — generate baselines + verify comparison
set -u
cd /home/z/my-project/apps/web

# Kill any existing server
pkill -f "next start" 2>/dev/null
sleep 1

# Start the production server
echo "=== Starting Next.js production server on port 3001 ==="
NEXT_TELEMETRY_DISABLED=1 NODE_ENV=production nohup ./node_modules/.bin/next start -H 127.0.0.1 -p 3001 > /tmp/snad-visual-server.log 2>&1 &
SERVER_PID=$!
echo "Server PID: $SERVER_PID"

# Wait for readiness
READY=0
for i in $(seq 1 20); do
  if curl --fail --silent http://127.0.0.1:3001/ > /dev/null 2>&1; then
    echo "Server ready after $((i*3))s"
    READY=1
    break
  fi
  sleep 3
done

if [ "$READY" = "0" ]; then
  echo "FATAL: Server did not become ready"
  kill $SERVER_PID 2>/dev/null
  exit 1
fi

# Clean previous baselines and test results
rm -rf test-results e2e/__screenshots__ e2e/*.spec.ts-snapshots

# Generate baselines for ar-rtl-light project only (single project for speed)
echo ""
echo "=== Generating visual baselines (ar-rtl-light, 10 tests) ==="
npx playwright test e2e/visual-regression.spec.ts \
  --project=ar-rtl-light \
  --update-snapshots \
  --reporter=line 2>&1 | tail -15
BASELINE_EXIT=$?

echo ""
echo "=== Baseline generation exit: $BASELINE_EXIT ==="

# Find baselines
echo ""
echo "=== Baseline files created ==="
find e2e -name "*.png" 2>/dev/null | head -15
BASELINE_COUNT=$(find e2e -name "*.png" 2>/dev/null | wc -l)
echo "Baseline count: $BASELINE_COUNT"

if [ "$BASELINE_COUNT" -gt 0 ]; then
  # Run comparison (should PASS against fresh baselines)
  echo ""
  echo "=== Running comparison (should PASS) ==="
  npx playwright test e2e/visual-regression.spec.ts \
    --project=ar-rtl-light \
    --reporter=line 2>&1 | tail -10
  COMPARE_EXIT=$?
  echo ""
  echo "=== Comparison exit: $COMPARE_EXIT ==="
fi

# Kill server
kill $SERVER_PID 2>/dev/null
wait $SERVER_PID 2>/dev/null

echo ""
echo "=== SUMMARY ==="
echo "Baselines generated: $BASELINE_COUNT"
echo "Baseline gen exit: $BASELINE_EXIT"
echo "Comparison exit: ${COMPARE_EXIT:-N/A}"

#!/bin/bash
# SNAD Playwright — start server, run all 6 projects, capture results, stop server
set -u
cd /home/z/my-project/apps/web

# Kill any existing server
pkill -f "next start" 2>/dev/null
sleep 1

# Start the production server
echo "=== Starting Next.js production server on port 3001 ==="
NEXT_TELEMETRY_DISABLED=1 NODE_ENV=production nohup ./node_modules/.bin/next start -H 127.0.0.1 -p 3001 > /tmp/snad-playwright-server.log 2>&1 &
SERVER_PID=$!
echo "Server PID: $SERVER_PID"

# Wait for readiness (up to 30s)
READY=0
for i in $(seq 1 30); do
  if curl --fail --silent http://127.0.0.1:3001/ > /dev/null 2>&1; then
    echo "Server ready after ${i}s"
    READY=1
    break
  fi
  sleep 1
done

if [ "$READY" = "0" ]; then
  echo "FATAL: Server did not become ready within 30s"
  cat /tmp/snad-playwright-server.log | tail -20
  kill $SERVER_PID 2>/dev/null
  exit 1
fi

echo "Server HTTP status: $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3001/)"

# Run all 6 Playwright projects
echo ""
echo "=== Running Playwright (6 projects × 8 tests = 48 tests) ==="
npx playwright test --reporter=line 2>&1 | tee /tmp/snad-playwright-results.txt
PLAYWRIGHT_EXIT=${PIPESTATUS[0]}

echo ""
echo "=== Playwright exit: $PLAYWRIGHT_EXIT ==="

# Extract summary
echo ""
echo "=== SUMMARY ==="
grep -E "passed|failed" /tmp/snad-playwright-results.txt | tail -3

# Kill server
kill $SERVER_PID 2>/dev/null
wait $SERVER_PID 2>/dev/null

exit $PLAYWRIGHT_EXIT

#!/bin/bash
# Run Playwright tests and save output
cd /home/z/my-project/apps/web
echo "=== Server status ==="
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://127.0.0.1:3001/
echo ""
echo "=== Running Playwright (ar-rtl-light only, 9 tests) ==="
npx playwright test --project=ar-rtl-light --reporter=line 2>&1
PLAYWRIGHT_EXIT=$?
echo ""
echo "=== Playwright exit: $PLAYWRIGHT_EXIT ==="
exit $PLAYWRIGHT_EXIT

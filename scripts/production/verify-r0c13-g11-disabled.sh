#!/usr/bin/env bash
set -euo pipefail

: "${PRODUCTION_BASE_URL:?PRODUCTION_BASE_URL is required}"
: "${CONTROL_PLANE_ADMIN_EMAIL:?CONTROL_PLANE_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_ADMIN_PASSWORD:?CONTROL_PLANE_ADMIN_PASSWORD is required}"
: "${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}"

WORK_DIR="${RUNNER_TEMP:-/tmp}/r0c13-g11"
mkdir -p "$WORK_DIR"

request() {
  local method="$1" url="$2" output="$3"
  shift 3
  curl --silent --show-error --location --max-time 60     --request "$method" "$url" --output "$output" --write-out '%{http_code}' "$@" || true
}

login_payload="$(jq -n   --arg address "$CONTROL_PLANE_ADMIN_EMAIL"   --arg pass "$CONTROL_PLANE_ADMIN_PASSWORD"   --arg tenant "$CONTROL_PLANE_TENANT_ID"   '{email:$address,password:$pass,tenantId:$tenant}')"

login_status="$(request POST "$PRODUCTION_BASE_URL/api/v1/auth/login" "$WORK_DIR/login.json"   --header 'Content-Type: application/json'   --data "$login_payload")"
test "$login_status" = "200" || {
  echo "::error::G11 control-plane login failed with HTTP $login_status"
  exit 1
}

token="$(jq -r '.accessToken // empty' "$WORK_DIR/login.json")"
test -n "$token" || { echo "::error::G11 login returned no access token"; exit 1; }
echo "::add-mask::$token"

readiness_status="$(request GET "$PRODUCTION_BASE_URL/api/v1/executive/billing/provider/readiness"   "$WORK_DIR/provider-readiness.json"   --header "Authorization: Bearer $token"   --header 'Accept: application/json')"
test "$readiness_status" = "200" || {
  echo "::error::Provider readiness returned HTTP $readiness_status"
  exit 1
}

jq -e '
  .providerCode == "DISABLED" and
  .mode == "DISABLED" and
  .liveCollectionEnabled == false
' "$WORK_DIR/provider-readiness.json" >/dev/null || {
  echo "::error::Production billing provider is not fail-closed DISABLED"
  jq '{providerCode,mode,liveCollectionEnabled}' "$WORK_DIR/provider-readiness.json" || true
  exit 1
}

cat > "$WORK_DIR/unsigned-webhook.json" <<'JSON'
{"eventId":"g11-unsigned-negative-smoke","eventType":"payment.succeeded","paymentRef":"g11-none"}
JSON

webhook_status="$(request POST "$PRODUCTION_BASE_URL/api/v1/billing/provider/webhook"   "$WORK_DIR/unsigned-webhook-response.json"   --header 'Content-Type: application/json'   --data @"$WORK_DIR/unsigned-webhook.json")"

case "$webhook_status" in
  401|400|422|503) ;;
  *)
    echo "::error::Unsigned provider webhook was not rejected; HTTP $webhook_status"
    cat "$WORK_DIR/unsigned-webhook-response.json" || true
    exit 1
    ;;
esac

if [ "$webhook_status" = "503" ]; then
  jq -e '.code == "PROVIDER_DISABLED"' "$WORK_DIR/unsigned-webhook-response.json" >/dev/null || {
    echo "::error::DISABLED webhook rejection did not return PROVIDER_DISABLED"
    exit 1
  }
fi

# Exact-source negative proof: there must be no externally exposed billing route
# capable of creating a provider customer, payment intent, charge, or refund.
api_root="apps/sanad-platform/src/main/java/com/sanad/platform/subscription/billing/api"
if grep -R -n -E '@(Post|Put|Patch)Mapping.*(charge|payment.?intent|provider.?customer|refund)' "$api_root"; then
  echo "::error::Externally exposed live payment mutation route detected in R0C13 billing API"
  exit 1
fi

# Provider implementation itself must fail closed for every external payment operation.
disabled_provider="apps/sanad-platform/src/main/java/com/sanad/platform/subscription/billing/infrastructure/DisabledBillingPaymentProvider.java"
for op in ensureProviderCustomer createPaymentIntent queryPaymentState requestRefund verifyAndParseEvent; do
  grep -q "$op" "$disabled_provider" || {
    echo "::error::Disabled provider contract missing operation: $op"
    exit 1
  }
done
count="$(grep -c 'throw disabled();' "$disabled_provider")"
test "$count" -ge 5 || {
  echo "::error::Disabled provider does not fail closed for all external operations"
  exit 1
}

jq -n   --arg providerMode "$(jq -r '.mode' "$WORK_DIR/provider-readiness.json")"   --arg providerCode "$(jq -r '.providerCode' "$WORK_DIR/provider-readiness.json")"   --arg webhookStatus "$webhook_status"   '{
    result:"PASS",
    providerMode:$providerMode,
    providerCode:$providerCode,
    liveCollectionEnabled:false,
    unsignedWebhookRejected:true,
    unsignedWebhookStatus:($webhookStatus|tonumber),
    liveChargePath:"INOPERABLE",
    proof:"runtime-disabled + no exposed mutation route + disabled provider fail-closed"
  }' > "$WORK_DIR/r0c13-g11-runtime-evidence.json"

echo "R13-G11 provider/runtime negative smoke: PASS"

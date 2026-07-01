import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const baseUrl = (__ENV.CRM_BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const token = __ENV.CRM_BEARER_TOKEN || "";
const writeEnabled = (__ENV.CRM_WRITE_ENABLED || "false") === "true";
const configuredVus = Number(__ENV.CRM_LOAD_VUS || 100);
const configuredDuration = __ENV.CRM_LOAD_DURATION || "5m";

const tenantLeakRate = new Rate("crm_tenant_leak_detected");
const crmRequestFailures = new Counter("crm_request_failures");
const crmReadLatency = new Trend("crm_read_latency", true);

const scenarios = {
  health: {
    executor: "constant-arrival-rate",
    exec: "health",
    rate: Math.max(10, configuredVus),
    timeUnit: "1s",
    duration: configuredDuration,
    preAllocatedVUs: Math.max(20, Math.ceil(configuredVus / 2)),
    maxVUs: Math.max(100, configuredVus * 2),
    gracefulStop: "15s",
  },
};

if (token) {
  scenarios.crmRead = {
    executor: "ramping-vus",
    exec: "crmRead",
    startTime: "5s",
    stages: [
      { duration: "30s", target: Math.max(10, Math.ceil(configuredVus / 4)) },
      { duration: configuredDuration, target: configuredVus },
      { duration: "30s", target: 0 },
    ],
    gracefulRampDown: "15s",
  };
}

export const options = {
  discardResponseBodies: true,
  scenarios,
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<750", "p(99)<1500"],
    "http_req_duration{endpoint:health}": ["p(95)<250"],
    "http_req_duration{endpoint:crm_accounts}": ["p(95)<500"],
    crm_tenant_leak_detected: ["rate==0"],
    crm_request_failures: ["count<10"],
  },
};

export function health() {
  const response = http.get(`${baseUrl}/actuator/health/readiness`, {
    tags: { endpoint: "health" },
    timeout: "5s",
  });

  const valid = check(response, {
    "readiness returns 200": (result) => result.status === 200,
  });
  if (!valid) crmRequestFailures.add(1);
  sleep(0.05);
}

export function crmRead() {
  if (!token) return;

  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: "application/json",
    "X-SNAD-Client": "k6-readiness",
  };

  const response = http.get(`${baseUrl}/api/v1/crm/accounts`, {
    headers,
    tags: { endpoint: "crm_accounts" },
    timeout: "10s",
  });

  crmReadLatency.add(response.timings.duration);
  const valid = check(response, {
    "CRM list returns 200": (result) => result.status === 200,
    "CRM list is JSON": (result) => String(result.headers["Content-Type"] || "").includes("application/json"),
  });
  if (!valid) crmRequestFailures.add(1);

  const echoedTenant = response.headers["X-SNAD-Tenant-Id"];
  const expectedTenant = __ENV.CRM_EXPECTED_TENANT_ID;
  tenantLeakRate.add(Boolean(expectedTenant && echoedTenant && echoedTenant !== expectedTenant));

  if (writeEnabled) {
    const payload = JSON.stringify({
      displayName: `Load Account ${__VU}-${__ITER}`,
      accountType: "PROSPECT",
      primaryCurrencyCode: "SAR",
      preferredLocale: "ar-SA",
      timeZone: "Asia/Riyadh",
      source: "K6_READINESS",
    });

    const writeResponse = http.post(`${baseUrl}/api/v1/crm/accounts`, payload, {
      headers: { ...headers, "Content-Type": "application/json" },
      tags: { endpoint: "crm_account_create" },
      timeout: "10s",
    });

    const writeValid = check(writeResponse, {
      "CRM create returns 201": (result) => result.status === 201,
    });
    if (!writeValid) crmRequestFailures.add(1);
  }

  sleep(0.2);
}

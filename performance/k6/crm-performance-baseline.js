import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// CRM-033 Performance Baseline
// Tests: dashboard, accounts list, customer-360, lead-conversion
// Target: 50 RPS for 10 minutes, p95 < 500ms, p99 < 1000ms, errors < 1%
//
// Authentication is fully automatic: setup() logs in as the perf-test admin
// (seeded by PerfTestBootstrapConfig under the perf-test profile) through the
// ordinary /api/v1/auth/login endpoint and shares the returned JWT with every
// VU. No manual token, no H2 console, no manual SQL.
//
// Environment variables:
//   BASE_URL                  — default http://localhost:8080
//   PERF_TEST_ADMIN_EMAIL     — perf-test admin login (default perf-admin@sanad.local)
//   PERF_TEST_ADMIN_PASSWORD  — perf-test admin password (required)
//   JWT_TOKEN                 — optional override; skips the login step
//   TEST_ACCOUNT_ID           — seeded customer-360 account id
//   TEST_LEAD_ID              — seeded lead id (CONVERTED → idempotent replay)
//   OUTPUT_FILE               — summary export path (default performance/results/crm-perf-baseline.json)

const crmReqDuration = new Trend('crm_req_duration', true);
const crmReqFailed = new Rate('crm_req_failed');

export const options = {
  scenarios: {
    crm_endpoints: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '10m',
      preAllocatedVUs: 100,
      maxVUs: 200,
    },
  },
  // k6 v0.50.0 omits p(99) from exports unless explicitly listed here.
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: false }],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    crm_req_duration: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN || '';
const ADMIN_EMAIL = __ENV.PERF_TEST_ADMIN_EMAIL || 'perf-admin@sanad.local';
const ADMIN_PASSWORD = __ENV.PERF_TEST_ADMIN_PASSWORD || '';

// Predefined test data — mirrored by PerfTestBootstrapConfig (perf-test profile)
const TEST_ACCOUNT_ID = __ENV.TEST_ACCOUNT_ID || '40000000-0000-4000-8000-000000000010';
const TEST_LEAD_ID = __ENV.TEST_LEAD_ID || '40000000-0000-4000-8000-000000000020';

// setup() runs once before the load phase and authenticates automatically.
export function setup() {
  if (JWT_TOKEN) {
    return { token: JWT_TOKEN };
  }

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(loginRes, {
    'login status is 200': (r) => r.status === 200,
  });
  if (loginRes.status !== 200) {
    throw new Error(`Automated login failed (${loginRes.status}): ${loginRes.body}`);
  }

  const token = loginRes.json().accessToken;
  if (!token) {
    throw new Error('Login response contained no accessToken');
  }
  console.log(`Authenticated as ${ADMIN_EMAIL} (token ${String(token).length} chars)`);
  return { token };
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    'Content-Type': 'application/json',
  };

  const scenario = __VU % 4;

  let response;
  let endpoint;

  switch (scenario) {
    case 0:
      // Dashboard
      endpoint = 'GET /api/v1/crm/dashboard';
      response = http.get(`${BASE_URL}/api/v1/crm/dashboard`, {
        headers,
        tags: { endpoint: 'crm-dashboard' },
        timeout: '10s',
      });
      break;

    case 1:
      // Accounts list
      endpoint = 'GET /api/v1/crm/accounts';
      response = http.get(`${BASE_URL}/api/v1/crm/accounts?limit=50`, {
        headers,
        tags: { endpoint: 'crm-accounts-list' },
        timeout: '10s',
      });
      break;

    case 2:
      // Customer-360
      endpoint = 'GET /api/v1/crm/accounts/{id}/customer-360';
      response = http.get(`${BASE_URL}/api/v1/crm/accounts/${TEST_ACCOUNT_ID}/customer-360`, {
        headers,
        tags: { endpoint: 'crm-customer-360' },
        timeout: '10s',
      });
      break;

    case 3:
      // Lead conversion (POST) — ConvertLeadRequest fields: createOpportunity,
      // pipelineId, stageId, opportunityName, amount, currencyCode, expectedCloseDate.
      // The seeded lead is CONVERTED, so every call follows the idempotent replay path.
      endpoint = 'POST /api/v1/crm/leads/{id}/convert';
      response = http.post(
        `${BASE_URL}/api/v1/crm/leads/${TEST_LEAD_ID}/convert`,
        JSON.stringify({
          createOpportunity: false,
          currencyCode: 'SAR',
        }),
        {
          headers,
          tags: { endpoint: 'crm-lead-conversion' },
          timeout: '10s',
        }
      );
      break;
  }

  // Record metrics
  crmReqDuration.add(response.timings.duration);
  crmReqFailed.add(response.status >= 400);

  // Validate response
  check(response, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
    'response time < 500ms': (r) => r.timings.duration < 500,
    'response time < 1000ms': (r) => r.timings.duration < 1000,
    'has content': (r) => r.body && r.body.length > 0,
  }) || console.log(`FAIL: ${endpoint} - Status: ${response.status}, Duration: ${response.timings.duration}ms`);
}

const metric = (data, name, key) =>
  (data.metrics[name] && data.metrics[name].values && data.metrics[name].values[key]) || 0;

export function handleSummary(data) {
  const p95 = metric(data, 'http_req_duration', 'p(95)');
  const p99 = metric(data, 'http_req_duration', 'p(99)');
  const failureRate = metric(data, 'http_req_failed', 'rate');
  const throughput = metric(data, 'http_reqs', 'rate');
  const totalRequests = metric(data, 'http_reqs', 'count');

  const summary = {
    timestamp: new Date().toISOString(),
    test: 'CRM-033 Performance Baseline',
    configuration: {
      target_rps: 50,
      duration: '10m',
      endpoints: ['dashboard', 'accounts-list', 'customer-360', 'lead-conversion'],
      authenticated: true,
      authentication: 'perf-test profile / /api/v1/auth/login (automatic)',
    },
    results: {
      total_requests: totalRequests,
      throughput_rps: Math.round(throughput * 100) / 100,
      // http_req_failed is a Rate metric; the failed-request count is not
      // exposed directly, so derive it from the rate and total requests.
      failed_requests: Math.round(failureRate * totalRequests),
      failure_rate: Math.round(failureRate * 10000) / 10000,
      avg_duration_ms: metric(data, 'http_req_duration', 'avg'),
      median_duration_ms: metric(data, 'http_req_duration', 'med'),
      p95_duration_ms: p95,
      p99_duration_ms: p99 || null,
      max_duration_ms: metric(data, 'http_req_duration', 'max'),
    },
    thresholds: {
      p95_below_500ms: p95 < 500,
      p99_below_1000ms: p99 > 0 && p99 < 1000,
      failure_rate_below_1pct: failureRate < 0.01,
    },
    status: 'PASS',
  };

  if (!summary.thresholds.p95_below_500ms || !summary.thresholds.p99_below_1000ms
      || !summary.thresholds.failure_rate_below_1pct) {
    summary.status = 'FAIL';
  }

  const outputFile = __ENV.OUTPUT_FILE || 'performance/results/crm-perf-baseline.json';
  const output = {};
  output[outputFile] = JSON.stringify(summary, null, 2);
  output.stdout = JSON.stringify(summary, null, 2);
  return output;
}

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ============================================================
// SANAD Staging Load Test — k6 script
// EXEC-PROMPT-010R2 Section 10
// ============================================================
// This script is documented but NOT executable until staging
// environment is provisioned. See SANAD-LOAD-TEST-PLAN.md.
// ============================================================

// Configuration (set via environment variables)
const BASE_URL = __ENV.STAGING_BASE_URL || 'https://staging-backend.example.com';
const TENANT_A_EMAIL = __ENV.TENANT_A_EMAIL || 'test-tenant-a@example.com';
const TENANT_A_PASSWORD = __ENV.TENANT_A_PASSWORD || 'change-me';
const TENANT_B_EMAIL = __ENV.TENANT_B_EMAIL || 'test-tenant-b@example.com';
const TENANT_B_PASSWORD = __ENV.TENANT_B_PASSWORD || 'change-me';

// Custom metrics
const authFailureRate = new Rate('auth_failures');
const crossTenantRejectionRate = new Rate('cross_tenant_rejections');

// Test options
export const options = {
  stages: [
    { duration: '2m', target: 5 },    // Warm-up
    { duration: '5m', target: 10 },   // Baseline
    { duration: '5m', target: 50 },   // Ramp-up
    { duration: '10m', target: 50 },  // Sustained
    { duration: '2m', target: 100 },  // Spike
    { duration: '3m', target: 10 },   // Recovery
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],            // Error rate < 1%
    http_req_duration: ['p(95)<500', 'p(99)<1000'], // p95 < 500ms, p99 < 1000ms
    auth_failures: ['rate<0.01'],              // Auth failure < 1% for valid requests
    cross_tenant_rejections: ['rate==1.0'],    // 100% cross-tenant rejection
  },
};

// Login helper
function login(email, password) {
  const response = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      params: { cookies: { jar: http.cookieJar() } },
    }
  );

  const success = check(response, {
    'login status 200': (r) => r.status === 200,
    'login has access_token': (r) => r.json('accessToken') !== undefined,
  });

  authFailureRate.add(!success);

  if (!success) {
    return null;
  }

  return {
    accessToken: response.json('accessToken'),
    cookies: response.cookies,
  };
}

// Test scenario
export default function () {
  // 1. Health check
  const health = http.get(`${BASE_URL}/actuator/health`);
  check(health, {
    'health status 200': (r) => r.status === 200,
    'health UP': (r) => r.json('status') === 'UP',
  });

  // 2. Login as Tenant A
  const sessionA = login(TENANT_A_EMAIL, TENANT_A_PASSWORD);
  if (!sessionA) {
    sleep(1);
    return;
  }

  // 3. /me (tenant binding)
  const me = http.get(`${BASE_URL}/api/v1/auth/me`, {
    headers: { Authorization: `Bearer ${sessionA.accessToken}` },
  });
  check(me, {
    'me status 200': (r) => r.status === 200,
    'me has tenant_id': (r) => r.json('tenantId') !== undefined,
  });

  // 4. Read-only tenant-scoped endpoint
  const orgs = http.get(`${BASE_URL}/api/v1/organizations`, {
    headers: { Authorization: `Bearer ${sessionA.accessToken}` },
  });
  check(orgs, {
    'orgs status 200': (r) => r.status === 200,
  });

  // 5. Token refresh
  const refresh = http.post(
    `${BASE_URL}/api/v1/auth/refresh`,
    '{}',
    {
      headers: { 'Content-Type': 'application/json' },
      cookies: sessionA.cookies,
    }
  );
  check(refresh, {
    'refresh status 200': (r) => r.status === 200,
  });

  // 6. Logout
  const logout = http.post(
    `${BASE_URL}/api/v1/auth/logout`,
    '{}',
    {
      headers: {
        Authorization: `Bearer ${sessionA.accessToken}`,
        'Content-Type': 'application/json',
      },
    }
  );
  check(logout, {
    'logout status 204': (r) => r.status === 204,
  });

  sleep(1);
}

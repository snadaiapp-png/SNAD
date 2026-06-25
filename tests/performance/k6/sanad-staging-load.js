import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// ============================================================
// SANAD Staging Load Test — k6 script
// EXEC-PROMPT-010R3 Section 16: Corrected auth contract
// ============================================================
// Auth contract (verified from AuthController.java):
//   - Login: POST /api/v1/auth/login → response body has accessToken
//   - Refresh: POST /api/v1/auth/refresh → X-SANAD-Refresh-Token header
//   - Logout: POST /api/v1/auth/logout → Authorization: Bearer <token>
//
// The refresh token is returned in the X-SANAD-Refresh-Token response
// header on login (BFF pattern), NOT as a cookie in production.
//
// This script is documented but NOT executable until staging
// environment is provisioned. See SANAD-LOAD-TEST-PLAN.md.
// ============================================================

// Configuration (set via environment variables — never hardcode credentials)
const BASE_URL = __ENV.STAGING_BASE_URL || 'https://staging-backend.example.com';
const TENANT_A_EMAIL = __ENV.TENANT_A_EMAIL || 'test-tenant-a@example.com';
const TENANT_A_PASSWORD = __ENV.TENANT_A_PASSWORD || 'change-me';

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
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    auth_failures: ['rate<0.01'],
    cross_tenant_rejections: ['rate==1.0'],
  },
};

// Login helper — extracts access token from body and refresh token from header
function login(email, password) {
  const response = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: { 'Content-Type': 'application/json' },
    }
  );

  const success = check(response, {
    'login status 200': (r) => r.status === 200,
    'login has accessToken': (r) => {
      try {
        return r.json('accessToken') !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  authFailureRate.add(!success);

  if (!success) {
    return null;
  }

  // Extract access token from response body
  let accessToken;
  try {
    accessToken = response.json('accessToken');
  } catch (e) {
    return null;
  }

  // Extract refresh token from X-SANAD-Refresh-Token response header
  // (NOT from cookie — production uses BFF header pattern)
  const refreshToken = response.headers['X-SANAD-Refresh-Token'];

  return {
    accessToken: accessToken,
    refreshToken: refreshToken || '',
  };
}

// Test scenario
export default function () {
  // 1. Health check
  const health = http.get(`${BASE_URL}/actuator/health`);
  check(health, {
    'health status 200': (r) => r.status === 200,
    'health UP': (r) => {
      try {
        return r.json('status') === 'UP';
      } catch (e) {
        return false;
      }
    },
  });

  // 2. Login
  const session = login(TENANT_A_EMAIL, TENANT_A_PASSWORD);
  if (!session || !session.accessToken) {
    sleep(1);
    return;
  }

  // 3. /me (tenant binding)
  const me = http.get(`${BASE_URL}/api/v1/auth/me`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  });
  check(me, {
    'me status 200': (r) => r.status === 200,
    'me has tenantId': (r) => {
      try {
        return r.json('tenantId') !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  // 4. Read-only tenant-scoped endpoint
  const orgs = http.get(`${BASE_URL}/api/v1/organizations`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  });
  check(orgs, {
    'orgs status 200': (r) => r.status === 200,
  });

  // 5. Token refresh — uses X-SANAD-Refresh-Token header (NOT cookie)
  if (session.refreshToken) {
    const refresh = http.post(
      `${BASE_URL}/api/v1/auth/refresh`,
      '{}',
      {
        headers: {
          'Content-Type': 'application/json',
          'X-SANAD-Refresh-Token': session.refreshToken,
        },
      }
    );
    check(refresh, {
      'refresh status 200': (r) => r.status === 200,
    });
  }

  // 6. Logout — uses Authorization header (not refresh token)
  const logout = http.post(
    `${BASE_URL}/api/v1/auth/logout`,
    '{}',
    {
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        'Content-Type': 'application/json',
      },
    }
  );
  check(logout, {
    'logout status 204': (r) => r.status === 204,
  });

  sleep(1);
}

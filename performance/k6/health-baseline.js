import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    baseline: {
      executor: 'constant-vus',
      vus: 10,
      duration: '60s',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: false }],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    'http_req_duration{endpoint:actuator-health}': ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';

export default function () {
  const response = http.get(`${baseUrl}/actuator/health`, {
    tags: { endpoint: 'actuator-health' },
    timeout: '10s',
  });

  check(response, {
    'health returns 200': (r) => r.status === 200,
    'health content type is JSON': (r) => {
      const contentType = String(r.headers['Content-Type'] || '').toLowerCase();
      return contentType.includes('application/json') || contentType.includes('+json');
    },
    'health reports UP': (r) => r.body && r.body.includes('"status":"UP"'),
  });

  sleep(0.2);
}

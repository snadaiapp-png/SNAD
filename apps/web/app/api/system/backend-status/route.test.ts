import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

/**
 * Route contract test for /api/system/backend-status
 *
 * Tests the actual GET() handler exported by the Next.js route.
 * Mocks checkBackendIntegration() to verify that the route:
 *   1. Returns the correct JSON structure
 *   2. Only exposes safe fields
 *   3. Never exposes sensitive fields
 */

// We need to mock the api-integration module before importing the route
vi.mock('@/lib/api-integration', () => ({
  checkBackendIntegration: vi.fn(),
}));

// Import after mock
const { GET } = await import('./route');
const { checkBackendIntegration } = await import('@/lib/api-integration');

describe('GET /api/system/backend-status', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns configured=true, reachable=true, statusCode=200 when backend is healthy', async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      error: null,
    });

    const response = await GET();
    const json = await response.json();

    expect(response.status).toBe(200);
    expect(json.configured).toBe(true);
    expect(json.reachable).toBe(true);
    expect(json.statusCode).toBe(200);

    // Verify only safe keys are present
    const keys = Object.keys(json);
    expect(keys).toEqual(expect.arrayContaining(['configured', 'reachable', 'statusCode']));
    expect(keys).not.toContain('error');
    expect(keys).not.toContain('url');
    expect(keys).not.toContain('headers');
    expect(keys).not.toContain('body');
    expect(keys).not.toContain('credentials');
    expect(keys).not.toContain('database');
    expect(keys).not.toContain('stack');
  });

  it('returns configured=false, reachable=false, statusCode=null when API not configured', async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: false,
      reachable: false,
      statusCode: null,
      error: 'NEXT_PUBLIC_API_BASE_URL is not set',
    });

    const response = await GET();
    const json = await response.json();

    expect(json.configured).toBe(false);
    expect(json.reachable).toBe(false);
    expect(json.statusCode).toBeNull();

    // Verify only safe keys
    const keys = Object.keys(json);
    expect(keys).toEqual(expect.arrayContaining(['configured', 'reachable', 'statusCode']));
    expect(keys).not.toContain('error');
    expect(keys).not.toContain('url');
  });

  it('returns configured=true, reachable=false, statusCode=null when backend unavailable', async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: false,
      statusCode: null,
      error: 'Connection refused',
    });

    const response = await GET();
    const json = await response.json();

    expect(json.configured).toBe(true);
    expect(json.reachable).toBe(false);
    expect(json.statusCode).toBeNull();

    // Verify no sensitive fields
    const keys = Object.keys(json);
    expect(keys).not.toContain('error');
    expect(keys).not.toContain('credentials');
    expect(keys).not.toContain('stack');
  });

  it('response always contains exactly configured, reachable, statusCode keys', async () => {
    vi.mocked(checkBackendIntegration).mockResolvedValue({
      configured: true,
      reachable: true,
      statusCode: 200,
      error: null,
    });

    const response = await GET();
    const json = await response.json();
    const keys = Object.keys(json).sort();

    // Must contain exactly these three keys (no more, no less)
    expect(keys).toEqual(['configured', 'reachable', 'statusCode']);
  });
});

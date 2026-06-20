import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// We test the api-config module which reads process.env.NEXT_PUBLIC_API_BASE_URL
// Since the module is imported at load time, we need to set env before import
// and re-import for each test. We use dynamic imports.

describe('api-config', () => {
  const originalEnv = process.env.NEXT_PUBLIC_API_BASE_URL;

  afterEach(() => {
    if (originalEnv === undefined) {
      delete process.env.NEXT_PUBLIC_API_BASE_URL;
    } else {
      process.env.NEXT_PUBLIC_API_BASE_URL = originalEnv;
    }
    vi.resetModules();
  });

  it('returns empty API_BASE_URL when NEXT_PUBLIC_API_BASE_URL is not set', async () => {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
    const mod = await import('./api-config');
    expect(mod.API_BASE_URL).toBe('');
    expect(mod.IS_API_CONFIGURED).toBe(false);
  });

  it('normalizes trailing slash from API base URL', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com/';
    const mod = await import('./api-config');
    expect(mod.API_BASE_URL).toBe('https://api.example.com');
  });

  it('normalizes multiple trailing slashes', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com///';
    const mod = await import('./api-config');
    expect(mod.API_BASE_URL).toBe('https://api.example.com');
  });

  it('preserves URL without trailing slash', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com';
    const mod = await import('./api-config');
    expect(mod.API_BASE_URL).toBe('https://api.example.com');
  });

  it('buildApiUrl constructs full URL from path', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com';
    const mod = await import('./api-config');
    expect(mod.buildApiUrl('/actuator/health')).toBe('https://api.example.com/actuator/health');
  });

  it('buildApiUrl adds leading slash if missing', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com';
    const mod = await import('./api-config');
    expect(mod.buildApiUrl('actuator/health')).toBe('https://api.example.com/actuator/health');
  });

  it('buildApiUrl returns empty string when API not configured', async () => {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
    const mod = await import('./api-config');
    expect(mod.buildApiUrl('/actuator/health')).toBe('');
  });

  it('IS_API_CONFIGURED is true when URL is set', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com';
    const mod = await import('./api-config');
    expect(mod.IS_API_CONFIGURED).toBe(true);
  });

  it('API_TIMEOUT_MS is 10000', async () => {
    const mod = await import('./api-config');
    expect(mod.API_TIMEOUT_MS).toBe(10000);
  });
});

describe('api-integration checkBackendIntegration', () => {
  const originalEnv = process.env.NEXT_PUBLIC_API_BASE_URL;

  afterEach(() => {
    if (originalEnv === undefined) {
      delete process.env.NEXT_PUBLIC_API_BASE_URL;
    } else {
      process.env.NEXT_PUBLIC_API_BASE_URL = originalEnv;
    }
    vi.restoreAllMocks();
    vi.resetModules();
  });

  it('returns configured=false when API_BASE_URL is not set', async () => {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
    const mod = await import('./api-integration');
    const result = await mod.checkBackendIntegration();
    expect(result.configured).toBe(false);
    expect(result.reachable).toBe(false);
  });

  it('returns reachable=true when backend returns 200', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com';
    const mod = await import('./api-integration');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
    }));
    const result = await mod.checkBackendIntegration();
    expect(result.configured).toBe(true);
    expect(result.reachable).toBe(true);
    expect(result.statusCode).toBe(200);
  });

  it('returns reachable=false when backend returns non-200', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com';
    const mod = await import('./api-integration');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
    }));
    const result = await mod.checkBackendIntegration();
    expect(result.configured).toBe(true);
    expect(result.reachable).toBe(false);
    expect(result.statusCode).toBe(500);
  });

  it('returns reachable=false on network failure', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com';
    const mod = await import('./api-integration');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')));
    const result = await mod.checkBackendIntegration();
    expect(result.configured).toBe(true);
    expect(result.reachable).toBe(false);
    expect(result.error).toBe('Network error');
  });

  it('returns reachable=false on timeout', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com';
    const mod = await import('./api-integration');
    // Simulate AbortError (timeout)
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(
      new DOMException('The operation was aborted', 'AbortError')
    ));
    const result = await mod.checkBackendIntegration();
    expect(result.configured).toBe(true);
    expect(result.reachable).toBe(false);
    expect(result.error).toContain('aborted');
  });

  it('checkBackendIntegration result contains only safe fields', async () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.com';
    const mod = await import('./api-integration');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 200 }));
    const result = await mod.checkBackendIntegration();
    const keys = Object.keys(result);
    expect(keys).toContain('configured');
    expect(keys).toContain('reachable');
    expect(keys).toContain('statusCode');
    expect(keys).toContain('error');
    // Must NOT contain sensitive fields
    expect(keys).not.toContain('url');
    expect(keys).not.toContain('headers');
    expect(keys).not.toContain('body');
    expect(keys).not.toContain('credentials');
  });
});

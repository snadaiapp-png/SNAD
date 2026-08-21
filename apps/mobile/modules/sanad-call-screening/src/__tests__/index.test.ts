/**
 * G8 Track E — TypeScript facade unit tests (pure helpers only; the native
 * module itself is exercised by the Android build gate and physical device
 * evidence — G8-05 §51–§54, §57).
 */

// The facade imports react-native for Platform/NativeModules; unit tests run
// in the node environment, so the module is mocked here (Track D convention).
jest.mock('react-native', () => ({
  Platform: { OS: 'android' },
  NativeModules: {},
}));

import { buildProjectionBatch, roleState } from '../index';
import type { NativeCallerProjectionRecord } from '../types';

function record(overrides: Partial<NativeCallerProjectionRecord> = {}): NativeCallerProjectionRecord {
  return {
    lookupToken: 'tok-1',
    entityType: 'CONTACT',
    entityId: 'c-1',
    displayName: 'أحمد',
    accountName: 'شركة',
    phoneLabel: 'mobile',
    verified: true,
    preferred: false,
    lifecycleStatus: 'ACTIVE',
    privacyLevel: 'PUBLIC',
    syncVersion: 1,
    updatedAt: '2026-08-21T00:00:00Z',
    deleted: false,
    ...overrides,
  };
}

describe('buildProjectionBatch', () => {
  it('strips display PII from RESTRICTED rows (G8-03 §41 policy)', () => {
    const batch = buildProjectionBatch([
      record({ privacyLevel: 'RESTRICTED', displayName: 'سر', accountName: 'سر' }),
    ]);
    expect(batch[0].displayName).toBeNull();
    expect(batch[0].accountName).toBeNull();
  });

  it('keeps identity for PUBLIC/INTERNAL rows', () => {
    const batch = buildProjectionBatch([record()]);
    expect(batch[0].displayName).toBe('أحمد');
    expect(batch[0].accountName).toBe('شركة');
  });

  it('caps the batch at 500 and coerces booleans', () => {
    const many = Array.from({ length: 600 }, (_, i) => record({ lookupToken: `tok-${i}` }));
    const batch = buildProjectionBatch(many);
    expect(batch).toHaveLength(500);
    expect(batch[0].verified).toBe(true);
    expect(batch[0].preferred).toBe(false);
  });

  it('is idempotent per row (same input → same output)', () => {
    expect(buildProjectionBatch([record()])).toEqual(buildProjectionBatch([record()]));
  });
});

describe('roleState (G8-05 §9, §45)', () => {
  it('maps the four required states', () => {
    expect(roleState(false, false, false)).toBe('UNSUPPORTED');
    expect(roleState(true, false, false)).toBe('UNSUPPORTED');
    expect(roleState(true, true, false)).toBe('REVOKED');
    expect(roleState(true, true, true)).toBe('GRANTED');
  });
});

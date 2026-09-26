-- ============================================================
-- G2 Human Visual Preview Fixtures
-- G2_VISUAL_FIXTURE
-- ------------------------------------------------------------
-- Deterministic, idempotent product data used ONLY by
-- .github/workflows/hrm-human-preview.yml after g2-acceptance-seed.sql.
-- This file is not a Flyway migration and is never used in production.
-- RLS remains enabled/forced; the least-privilege preview role supplies
-- the governed tenant context explicitly. No BYPASSRLS is required.
-- ============================================================

SELECT set_config('app.tenant_id', '33333333-3333-4333-8333-333333333331', false);

-- Employee attendance: completed history plus an open current-day record.
INSERT INTO hr_attendance_records (
    id, tenant_id, employment_id, record_date,
    clock_in, clock_out, break_minutes, worked_minutes,
    source, state, notes, created_at, updated_at
) VALUES (
    '33333333-3333-4333-8333-333333333391',
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333361',
    CURRENT_DATE - 1,
    (CURRENT_DATE - 1) + TIME '08:05',
    (CURRENT_DATE - 1) + TIME '16:35',
    30,
    480,
    'MANUAL',
    'COMPLETED',
    'G2 visual fixture: completed attendance day',
    NOW(),
    NOW()
), (
    '33333333-3333-4333-8333-333333333392',
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333361',
    CURRENT_DATE,
    CURRENT_DATE + TIME '08:10',
    NULL,
    0,
    NULL,
    'MANUAL',
    'OPEN',
    'G2 visual fixture: current open attendance',
    NOW(),
    NOW()
)
ON CONFLICT (tenant_id, employment_id, record_date) DO NOTHING;

-- Self + manager timesheet views: one draft and one submitted period.
INSERT INTO hr_timesheets (
    id, tenant_id, employment_id, period_start, period_end,
    total_worked_minutes, total_break_minutes, state,
    submitted_at, created_at, updated_at
) VALUES (
    '33333333-3333-4333-8333-333333333393',
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333361',
    CURRENT_DATE - 6,
    CURRENT_DATE,
    2310,
    150,
    'DRAFT',
    NULL,
    NOW(),
    NOW()
), (
    '33333333-3333-4333-8333-333333333394',
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333361',
    CURRENT_DATE - 13,
    CURRENT_DATE - 7,
    2400,
    150,
    'SUBMITTED',
    NOW() - INTERVAL '1 day',
    NOW(),
    NOW()
)
ON CONFLICT (tenant_id, employment_id, period_start, period_end) DO NOTHING;

-- Manager and HR approval queues. These are display fixtures only; the
-- separate G2 Authenticated Acceptance creates and owns its own request ID.
INSERT INTO hr_leave_requests (
    id, tenant_id, employment_id, leave_type_id,
    start_date, end_date, days_count, reason, state,
    submitted_at, created_at, updated_at
)
SELECT
    '33333333-3333-4333-8333-333333333395',
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333361',
    lt.id,
    CURRENT_DATE + 30,
    CURRENT_DATE + 31,
    2.00,
    'G2 Visual Fixture — Manager approval queue',
    'PENDING_MANAGER',
    NOW() - INTERVAL '2 hours',
    NOW(),
    NOW()
FROM hr_leave_types lt
WHERE lt.tenant_id = '33333333-3333-4333-8333-333333333331'
  AND lt.code = 'ANNUAL'
  AND NOT EXISTS (
      SELECT 1 FROM hr_leave_requests r
      WHERE r.id = '33333333-3333-4333-8333-333333333395'
        AND r.tenant_id = '33333333-3333-4333-8333-333333333331'
  );

INSERT INTO hr_leave_requests (
    id, tenant_id, employment_id, leave_type_id,
    start_date, end_date, days_count, reason, state,
    submitted_at, created_at, updated_at
)
SELECT
    '33333333-3333-4333-8333-333333333396',
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333361',
    lt.id,
    CURRENT_DATE + 40,
    CURRENT_DATE + 41,
    2.00,
    'G2 Visual Fixture — HR approval queue',
    'PENDING_HR',
    NOW() - INTERVAL '1 hour',
    NOW(),
    NOW()
FROM hr_leave_types lt
WHERE lt.tenant_id = '33333333-3333-4333-8333-333333333331'
  AND lt.code = 'ANNUAL'
  AND NOT EXISTS (
      SELECT 1 FROM hr_leave_requests r
      WHERE r.id = '33333333-3333-4333-8333-333333333396'
        AND r.tenant_id = '33333333-3333-4333-8333-333333333331'
  );

-- HR schedule administration surface.
INSERT INTO hr_work_schedules (
    id, tenant_id, code, name_ar, name_en, timezone,
    working_days, shift_start, shift_end,
    break_start, break_end, break_minutes, expected_minutes,
    is_overnight, state, created_at, updated_at
) VALUES (
    '33333333-3333-4333-8333-333333333397',
    '33333333-3333-4333-8333-333333333331',
    'G2-VISUAL-DAY',
    'الدوام النهاري التجريبي',
    'G2 Visual Day Shift',
    'Asia/Riyadh',
    ARRAY['SUN','MON','TUE','WED','THU']::varchar(20)[],
    TIME '08:00',
    TIME '16:30',
    TIME '12:00',
    TIME '12:30',
    30,
    480,
    false,
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- Fail-closed fixture proof for CI logs.
SELECT 'G2_VISUAL_FIXTURE attendance=' || COUNT(*)
FROM hr_attendance_records
WHERE tenant_id = '33333333-3333-4333-8333-333333333331';
SELECT 'G2_VISUAL_FIXTURE timesheets=' || COUNT(*)
FROM hr_timesheets
WHERE tenant_id = '33333333-3333-4333-8333-333333333331';
SELECT 'G2_VISUAL_FIXTURE manager_leave=' || COUNT(*)
FROM hr_leave_requests
WHERE tenant_id = '33333333-3333-4333-8333-333333333331' AND state = 'PENDING_MANAGER';
SELECT 'G2_VISUAL_FIXTURE hr_leave=' || COUNT(*)
FROM hr_leave_requests
WHERE tenant_id = '33333333-3333-4333-8333-333333333331' AND state = 'PENDING_HR';
SELECT 'G2_VISUAL_FIXTURE schedules=' || COUNT(*)
FROM hr_work_schedules
WHERE tenant_id = '33333333-3333-4333-8333-333333333331';

SELECT set_config('app.tenant_id', '', false);

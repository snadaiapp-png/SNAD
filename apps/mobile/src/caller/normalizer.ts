/**
 * G8 Caller Identification — mobile phone normalizer.
 *
 * SEMANTIC PARITY with the backend authority
 * {@code com.sanad.platform.crm.party.domain.PhoneNumberNormalizer}
 * (G8-03 §50–§51): identical rules — strip [\s().-], 00→+, E.164
 * pass-through (^\+[1-9][0-9]{7,14}$), and the Saudi forms
 * 05xxxxxxxx / 5xxxxxxxx / 966xxxxxxxxx with an explicit countryHint=SA.
 * Shared test vectors in docs/crm/g8/caller-phone-normalization-vectors.json
 * gate both implementations (NORMALIZATION_PARITY).
 */

const E164_PATTERN = /^\+[1-9][0-9]{7,14}$/;

/** Normalize a raw phone number to E.164, or null when not possible. */
export function normalizePhone(raw: string | null | undefined, countryHint?: string | null): string | null {
  if (raw == null) return null;
  let compact = raw.replace(/[\s().-]/g, '');
  if (compact.length === 0) return null;
  if (compact.startsWith('00')) compact = '+' + compact.substring(2);
  if (E164_PATTERN.test(compact)) return compact;

  const hint = countryHint == null ? null : countryHint.trim().toUpperCase();
  if (hint === 'SA') {
    if (/^05[0-9]{8}$/.test(compact)) compact = '+966' + compact.substring(1);
    else if (/^5[0-9]{8}$/.test(compact)) compact = '+966' + compact;
    else if (/^966[0-9]{9}$/.test(compact)) compact = '+' + compact;
    if (E164_PATTERN.test(compact)) return compact;
  }
  return null;
}

/** Digits-only representation (country code included) of a normalized E.164. */
export function digits(normalizedE164: string): string {
  return normalizedE164.startsWith('+') ? normalizedE164.substring(1) : normalizedE164;
}

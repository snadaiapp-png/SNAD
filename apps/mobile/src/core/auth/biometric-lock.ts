import * as LocalAuthentication from "expo-local-authentication";

export async function canUseBiometricUnlock(): Promise<boolean> {
  const [hardware, enrolled] = await Promise.all([
    LocalAuthentication.hasHardwareAsync(),
    LocalAuthentication.isEnrolledAsync(),
  ]);
  return hardware && enrolled;
}

export async function requestBiometricUnlock(): Promise<boolean> {
  if (!(await canUseBiometricUnlock())) return false;
  const result = await LocalAuthentication.authenticateAsync({
    promptMessage: "فتح SNAD CRM",
    cancelLabel: "إلغاء",
    disableDeviceFallback: false,
  });
  return result.success;
}

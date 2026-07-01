import * as Device from "expo-device";
import * as Notifications from "expo-notifications";
import * as Updates from "expo-updates";
import { environmentCapabilities } from "@/core/config/environment";

export async function applyAvailableUpdate(): Promise<boolean> {
  if (!Updates.isEnabled || !environmentCapabilities.easConfigured) return false;
  const update = await Updates.checkForUpdateAsync();
  if (!update.isAvailable) return false;
  await Updates.fetchUpdateAsync();
  await Updates.reloadAsync();
  return true;
}

export async function registerForPushNotifications(): Promise<string | null> {
  if (!Device.isDevice || !environmentCapabilities.easConfigured) return null;

  const current = await Notifications.getPermissionsAsync();
  const permission =
    current.status === "granted"
      ? current
      : await Notifications.requestPermissionsAsync();
  if (permission.status !== "granted") return null;

  const projectId = environmentCapabilities.easConfigured
    ? process.env.EAS_PROJECT_ID
    : undefined;
  if (!projectId) return null;

  const token = await Notifications.getExpoPushTokenAsync({ projectId });
  return token.data;
}

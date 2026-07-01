import type { ConfigContext, ExpoConfig } from "expo/config";

const APP_ENV = process.env.EXPO_PUBLIC_APP_ENV ?? "development";
const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://10.0.2.2:8080";
const EAS_PROJECT_ID = process.env.EAS_PROJECT_ID;

export default ({ config }: ConfigContext): ExpoConfig => {
  const production = APP_ENV === "production";
  const eas = EAS_PROJECT_ID ? { projectId: EAS_PROJECT_ID } : undefined;
  const updates = EAS_PROJECT_ID
    ? {
        url: `https://u.expo.dev/${EAS_PROJECT_ID}`,
        checkAutomatically: "ON_LOAD" as const,
        fallbackToCacheTimeout: 0,
      }
    : undefined;

  return {
    ...config,
    name: production ? "SNAD CRM" : `SNAD CRM (${APP_ENV})`,
    slug: "snad-crm",
    version: "0.1.0",
    orientation: "portrait",
    userInterfaceStyle: "automatic",
    newArchEnabled: true,
    scheme: "snadcrm",
    ios: {
      bundleIdentifier: "com.snad.crm",
      supportsTablet: true,
      infoPlist: {
        NSFaceIDUsageDescription: "استخدام Face ID لحماية الوصول المحلي إلى SNAD CRM.",
      },
    },
    android: {
      package: "com.snad.crm",
      permissions: ["POST_NOTIFICATIONS"],
    },
    web: {
      bundler: "metro",
    },
    plugins: [
      "expo-router",
      [
        "expo-secure-store",
        {
          configureAndroidBackup: false,
          faceIDPermission: "استخدام Face ID لحماية الوصول المحلي إلى SNAD CRM.",
        },
      ],
      [
        "expo-local-authentication",
        {
          faceIDPermission: "استخدام Face ID لحماية الوصول المحلي إلى SNAD CRM.",
        },
      ],
      [
        "expo-notifications",
        {
          defaultChannel: "default",
        },
      ],
      "expo-localization",
      "expo-sqlite",
    ],
    experiments: {
      typedRoutes: true,
    },
    extra: {
      appEnv: APP_ENV,
      apiBaseUrl: API_BASE_URL,
      eas,
    },
    ...(updates
      ? {
          updates,
          runtimeVersion: { policy: "appVersion" },
        }
      : {}),
  };
};

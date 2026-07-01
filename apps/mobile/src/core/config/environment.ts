import Constants from "expo-constants";
import { z } from "zod";

const EnvironmentSchema = z.object({
  appEnv: z.enum(["development", "preview", "production"]),
  apiBaseUrl: z.string().url(),
  eas: z
    .object({
      projectId: z.string().uuid().optional(),
    })
    .optional(),
});

const extra = Constants.expoConfig?.extra ?? {};

export const environment = EnvironmentSchema.parse({
  appEnv: extra.appEnv ?? "development",
  apiBaseUrl: extra.apiBaseUrl ?? "http://10.0.2.2:8080",
  eas: extra.eas,
});

export const environmentCapabilities = {
  easConfigured: Boolean(environment.eas?.projectId),
  production: environment.appEnv === "production",
} as const;

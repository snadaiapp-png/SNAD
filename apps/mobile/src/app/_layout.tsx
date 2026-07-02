import "react-native-gesture-handler";
import "react-native-reanimated";

import { useEffect } from "react";
import { I18nextProvider } from "react-i18next";
import { QueryClientProvider } from "@tanstack/react-query";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { i18n } from "../core/i18n";
import { getMobileDatabase } from "../core/offline/database";
import { queryClient } from "../core/query/query-client";
import { applyAvailableUpdate } from "../core/runtime/activation";

export default function RootLayout() {
  useEffect(() => {
    void getMobileDatabase();
    void applyAvailableUpdate().catch(() => {
      // Update failures must not prevent local application startup.
    });
  }, []);

  return (
    <SafeAreaProvider>
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <StatusBar style="auto" />
          <Stack screenOptions={{ headerShown: false }} />
        </QueryClientProvider>
      </I18nextProvider>
    </SafeAreaProvider>
  );
}

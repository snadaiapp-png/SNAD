import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { apiRequest } from "@/core/api/client";
import {
  environment,
  environmentCapabilities,
} from "@/core/config/environment";
import { getMobileDatabase } from "@/core/offline/database";

type ApiState = "checking" | "online" | "offline";

export default function EnvironmentReadinessScreen() {
  const { t, i18n } = useTranslation();
  const [databaseReady, setDatabaseReady] = useState(false);
  const [apiState, setApiState] = useState<ApiState>("checking");
  const rtl = i18n.language === "ar";

  useEffect(() => {
    let active = true;

    void getMobileDatabase().then(() => {
      if (active) setDatabaseReady(true);
    });

    void apiRequest<{ status?: string }>("/actuator/health")
      .then(() => {
        if (active) setApiState("online");
      })
      .catch(() => {
        if (active) setApiState("offline");
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={[styles.container, { direction: rtl ? "rtl" : "ltr" }]}>
        <View style={styles.hero}>
          <Text style={[styles.title, rtl && styles.right]}>{t("title")}</Text>
          <Text style={[styles.subtitle, rtl && styles.right]}>
            {t("subtitle")}
          </Text>
        </View>

        <View style={styles.grid}>
          <StatusCard
            label={t("environment")}
            value={environment.appEnv}
            ready
            rtl={rtl}
          />
          <StatusCard
            label={t("api")}
            value={
              apiState === "checking"
                ? "…"
                : apiState === "online"
                  ? t("online")
                  : t("offline")
            }
            ready={apiState === "online"}
            rtl={rtl}
          />
          <StatusCard
            label={t("eas")}
            value={
              environmentCapabilities.easConfigured
                ? t("configured")
                : t("pending")
            }
            ready={environmentCapabilities.easConfigured}
            rtl={rtl}
          />
          <StatusCard
            label={t("database")}
            value={databaseReady ? t("ready") : "…"}
            ready={databaseReady}
            rtl={rtl}
          />
        </View>

        <Text style={[styles.endpoint, rtl && styles.right]}>
          {environment.apiBaseUrl}
        </Text>
      </View>
    </SafeAreaView>
  );
}

function StatusCard({
  label,
  value,
  ready,
  rtl,
}: {
  label: string;
  value: string;
  ready: boolean;
  rtl: boolean;
}) {
  return (
    <View style={styles.card}>
      <View style={[styles.indicator, ready ? styles.ready : styles.pending]} />
      <View style={styles.cardBody}>
        <Text style={[styles.label, rtl && styles.right]}>{label}</Text>
        <Text style={[styles.value, rtl && styles.right]}>{value}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: "#F5F7FB" },
  container: { flex: 1, padding: 24, gap: 24 },
  hero: {
    borderRadius: 24,
    backgroundColor: "#102A56",
    padding: 24,
    gap: 8,
  },
  title: { color: "#FFFFFF", fontSize: 30, fontWeight: "800" },
  subtitle: { color: "#D6E2F5", fontSize: 16, lineHeight: 24 },
  grid: { gap: 12 },
  card: {
    minHeight: 78,
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: "#E1E7F0",
    backgroundColor: "#FFFFFF",
    padding: 18,
  },
  indicator: { width: 12, height: 12, borderRadius: 6 },
  ready: { backgroundColor: "#1D8A55" },
  pending: { backgroundColor: "#D68A18" },
  cardBody: { flex: 1, gap: 3 },
  label: { color: "#63718A", fontSize: 13 },
  value: { color: "#14213D", fontSize: 17, fontWeight: "700" },
  endpoint: {
    color: "#63718A",
    fontSize: 12,
    textAlign: "center",
  },
  right: { textAlign: "right" },
});

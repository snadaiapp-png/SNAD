"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { WorkspaceDashboard } from "@/components/workspace/WorkspaceDashboard";

export default function WorkspacePage() {
  const { state } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (
      state === "ANONYMOUS" ||
      state === "ERROR" ||
      state === "EXPIRED" ||
      state === "CREDENTIAL_ROTATION_REQUIRED"
    ) {
      router.replace("/");
    }
  }, [state, router]);

  if (
    state === "INITIALIZING" ||
    state === "REFRESHING" ||
    state === "LOGGING_OUT" ||
    state !== "AUTHENTICATED"
  ) {
    return <AuthLoadingState />;
  }

  return <WorkspaceDashboard />;
}

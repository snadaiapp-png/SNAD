import { AuthLoadingState } from "@/components/auth/auth-loading-state";

export default function SystemHealthLoading() {
  return <AuthLoadingState phase="session" />;
}

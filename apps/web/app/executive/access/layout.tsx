import type { ReactNode } from "react";
import { ExecutivePlatformIamI18nAugmenter } from "../_components/ExecutivePlatformIamI18nAugmenter";

export default function PlatformAccessLayout({ children }: { children: ReactNode }) {
  return <ExecutivePlatformIamI18nAugmenter>{children}</ExecutivePlatformIamI18nAugmenter>;
}

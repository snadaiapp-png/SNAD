import type { ReactNode } from "react";
import { AccessI18nAugmenter } from "./_client/access-i18n-augmenter";

export default function AccessLayout({ children }: { children: ReactNode }) {
  return <AccessI18nAugmenter>{children}</AccessI18nAugmenter>;
}

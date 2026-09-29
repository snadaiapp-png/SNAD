import type { ReactNode } from "react";
import { UsersI18nAugmenter } from "./_client/users-i18n-augmenter";

export default function UsersLayout({ children }: { children: ReactNode }) {
  return <UsersI18nAugmenter>{children}</UsersI18nAugmenter>;
}

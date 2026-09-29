import type { Locale, TranslationDictionary } from "./types";

const ar: TranslationDictionary = {
  "management.iam.label": "إدارة الهوية والوصول",
  "management.iam.users": "المستخدمون",
  "management.iam.access": "إدارة الوصول",
};

const en: TranslationDictionary = {
  "management.iam.label": "Identity and access management",
  "management.iam.users": "Users",
  "management.iam.access": "Access management",
};

export function managementIamDictionary(locale: Locale): TranslationDictionary {
  return locale === "en" ? en : ar;
}

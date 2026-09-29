import type { Locale, TranslationDictionary } from "./types";

type Translate = (key: string, params?: Record<string, string | number>) => string;

const ar: TranslationDictionary = {
  "users.title": "المستخدمون",
  "users.subtitle": "إدارة مستخدمي مساحة العمل",
  "users.search": "بحث في المستخدمين",
  "users.statusFilter": "حالة المستخدم",
  "users.allStatuses": "كل الحالات",
  "users.create": "إضافة مستخدم",
  "users.createTitle": "إضافة مستخدم جديد",
  "users.email": "البريد الإلكتروني",
  "users.displayName": "الاسم المعروض",
  "users.submitCreate": "إرسال الدعوة",
  "users.cancel": "إلغاء",
  "users.empty": "لا يوجد مستخدمون بعد",
  "users.noMatches": "لا توجد نتائج مطابقة",
  "users.loading": "جارٍ تحميل المستخدمين",
  "users.forbidden": "لا تملك صلاحية عرض المستخدمين",
  "users.error": "تعذر تحميل المستخدمين",
  "users.open": "فتح التفاصيل",
  "users.activate": "تفعيل",
  "users.deactivate": "تعطيل",
  "users.suspend": "إيقاف مؤقت",
  "users.archive": "أرشفة",
  "users.actions": "الإجراءات",
  "users.status": "الحالة",
  "users.status.ACTIVE": "نشط",
  "users.status.INACTIVE": "غير نشط",
  "users.status.INVITED": "مدعو",
  "users.status.SUSPENDED": "موقوف",
  "users.status.ARCHIVED": "مؤرشف",
  "users.close": "إغلاق",
  "management.users.title": "المستخدمون",
  "management.users.detail.title": "تفاصيل المستخدم",
  "management.users.detail.save": "حفظ التعديلات",
  "management.users.detail.memberships": "عضويات المؤسسات",
  "management.users.detail.roles": "الأدوار المسندة",
  "management.users.detail.role": "الدور",
  "management.users.detail.grant": "إسناد الدور",
  "management.users.detail.revoke": "سحب الدور",
  "management.users.detail.noMemberships": "لا توجد عضويات",
  "management.users.detail.noRoles": "لا توجد أدوار مسندة",
  "management.users.detail.loading": "جارٍ تحميل بيانات المستخدم",
  "management.users.detail.error": "تعذر تحميل بيانات المستخدم",
};

const en: TranslationDictionary = {
  "users.title": "Users",
  "users.subtitle": "Manage workspace users",
  "users.search": "Search users",
  "users.statusFilter": "User status",
  "users.allStatuses": "All statuses",
  "users.create": "Add user",
  "users.createTitle": "Add a new user",
  "users.email": "Email",
  "users.displayName": "Display name",
  "users.submitCreate": "Send invitation",
  "users.cancel": "Cancel",
  "users.empty": "No users yet",
  "users.noMatches": "No matching users",
  "users.loading": "Loading users",
  "users.forbidden": "You do not have permission to view users",
  "users.error": "Unable to load users",
  "users.open": "Open details",
  "users.activate": "Activate",
  "users.deactivate": "Deactivate",
  "users.suspend": "Suspend",
  "users.archive": "Archive",
  "users.actions": "Actions",
  "users.status": "Status",
  "users.status.ACTIVE": "Active",
  "users.status.INACTIVE": "Inactive",
  "users.status.INVITED": "Invited",
  "users.status.SUSPENDED": "Suspended",
  "users.status.ARCHIVED": "Archived",
  "users.close": "Close",
  "management.users.title": "Users",
  "management.users.detail.title": "User details",
  "management.users.detail.save": "Save changes",
  "management.users.detail.memberships": "Organization memberships",
  "management.users.detail.roles": "Assigned roles",
  "management.users.detail.role": "Role",
  "management.users.detail.grant": "Assign role",
  "management.users.detail.revoke": "Revoke role",
  "management.users.detail.noMemberships": "No memberships",
  "management.users.detail.noRoles": "No assigned roles",
  "management.users.detail.loading": "Loading user details",
  "management.users.detail.error": "Unable to load user details",
};

export function usersDictionary(locale: Locale): TranslationDictionary {
  return locale === "en" ? en : ar;
}

export interface UsersMessages {
  title: string; subtitle: string; search: string; statusFilter: string; allStatuses: string;
  create: string; createTitle: string; email: string; displayName: string; submitCreate: string;
  cancel: string; empty: string; noMatches: string; loading: string; forbidden: string; error: string;
  open: string; activate: string; deactivate: string; suspend: string; archive: string; actions: string;
  status: string; status_ACTIVE: string; status_INACTIVE: string; status_INVITED: string;
  status_SUSPENDED: string; status_ARCHIVED: string; close: string;
}

export function usersMessages(t: Translate): UsersMessages {
  return {
    title: t("users.title"), subtitle: t("users.subtitle"), search: t("users.search"),
    statusFilter: t("users.statusFilter"), allStatuses: t("users.allStatuses"), create: t("users.create"),
    createTitle: t("users.createTitle"), email: t("users.email"), displayName: t("users.displayName"),
    submitCreate: t("users.submitCreate"), cancel: t("users.cancel"), empty: t("users.empty"),
    noMatches: t("users.noMatches"), loading: t("users.loading"), forbidden: t("users.forbidden"),
    error: t("users.error"), open: t("users.open"), activate: t("users.activate"),
    deactivate: t("users.deactivate"), suspend: t("users.suspend"), archive: t("users.archive"),
    actions: t("users.actions"), status: t("users.status"), status_ACTIVE: t("users.status.ACTIVE"),
    status_INACTIVE: t("users.status.INACTIVE"), status_INVITED: t("users.status.INVITED"),
    status_SUSPENDED: t("users.status.SUSPENDED"), status_ARCHIVED: t("users.status.ARCHIVED"), close: t("users.close"),
  };
}

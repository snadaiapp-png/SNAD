export function formValue(form: FormData, key: string): string {
  return String(form.get(key) ?? "").trim();
}

export function optionalValue(form: FormData, key: string): string | undefined {
  return formValue(form, key) || undefined;
}

export function formatNumber(value: number | null | undefined): string {
  return new Intl.NumberFormat("ar-SA", { maximumFractionDigits: 2 }).format(value ?? 0);
}

export function formatDate(value: string | null | undefined): string {
  return value
    ? new Intl.DateTimeFormat("ar-SA", { dateStyle: "medium" }).format(new Date(value))
    : "—";
}

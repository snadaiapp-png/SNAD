"use client";

export default function SystemHealthError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div style={{ padding: "2rem", textAlign: "center" }}>
      <h2 style={{ marginBottom: "1rem" }}>تعذر تحميل صفحة صحة النظام</h2>
      <p style={{ color: "var(--snad-color-text-muted, #6b7280)", marginBottom: "1.5rem" }}>
        {error.message || "حدث خطأ غير متوقع."}
      </p>
      <button
        type="button"
        onClick={reset}
        style={{
          padding: "0.5rem 1.5rem",
          borderRadius: "0.5rem",
          border: "none",
          background: "var(--snad-color-primary, #2563eb)",
          color: "#fff",
          cursor: "pointer",
          fontWeight: 500,
        }}
      >
        إعادة المحاولة
      </button>
    </div>
  );
}

import Link from "next/link";

/**
 * Global not-found page — displayed when Next.js cannot match a route.
 *
 * This is a server component (no "use client") because it only renders
 * static content with a navigation link.
 */
export default function NotFound() {
  return (
    <div
      role="status"
      aria-label="Page not found"
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "60vh",
        padding: "2rem",
        fontFamily: "var(--font-snad-latin), system-ui, sans-serif",
        textAlign: "center",
      }}
    >
      <h1
        style={{
          fontSize: "3rem",
          fontWeight: 700,
          color: "var(--snad-color-text-muted, #d1d5db)",
          marginBottom: "0.25rem",
        }}
      >
        404
      </h1>
      <h2
        style={{
          fontSize: "1.25rem",
          fontWeight: 600,
          color: "var(--snad-color-text, #1a1a1a)",
          marginBottom: "0.5rem",
        }}
      >
        Page not found
      </h2>
      <p
        style={{
          fontSize: "0.875rem",
          color: "var(--snad-color-text-muted, #6b7280)",
          marginBottom: "1.5rem",
          maxWidth: "24rem",
        }}
      >
        The page you are looking for does not exist or has been moved.
      </p>
      <Link
        href="/"
        style={{
          padding: "0.5rem 1.5rem",
          borderRadius: "0.375rem",
          border: "1px solid var(--snad-color-border, #d1d5db)",
          background: "var(--snad-color-bg, #ffffff)",
          color: "var(--snad-color-text, #1a1a1a)",
          fontSize: "0.875rem",
          fontWeight: 500,
          textDecoration: "none",
        }}
      >
        Go to home
      </Link>
    </div>
  );
}

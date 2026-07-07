# SNAD Typography

> **Primary Arabic face:** Tajawal
> **Primary Latin face:** Inter
> **Numeric face:** Inter (Latin)
> **Type scale:** 13 steps, 12px → 48px

SNAD is an **Arabic-first** product. The typography system is built around
the Arabic script and the Latin script is a first-class fallback — never
an afterthought.

## 1. Font families

### Arabic — `--snad-font-arabic`

```css
--snad-font-arabic: 'Tajawal', 'IBM Plex Sans Arabic', 'Noto Sans Arabic', Tahoma, sans-serif;
```

| Face | Role | Why |
| --- | --- | --- |
| **Tajawal** | Primary | Modern geometric Arabic face, designed by Boutros International. Excellent legibility at small sizes; supports Arabic, Arabic-Indic digits, and Arabic punctuation. |
| IBM Plex Sans Arabic | Fallback 1 | Licensed under SIL OFL; bundled as a webfont in production. |
| Noto Sans Arabic | Fallback 2 | Google's universal Arabic face; covers every Unicode Arabic block including extension blocks for African and Asian scripts. |
| Tahoma | Fallback 3 | System font on Windows; reasonable Arabic coverage. |
| sans-serif | Last resort | Browser default sans-serif. |

### Latin — `--snad-font-latin`

```css
--snad-font-latin: 'Inter', 'Noto Sans', Arial, sans-serif;
```

| Face | Role | Why |
| --- | --- | --- |
| **Inter** | Primary | Variable font designed for UI; excellent at 14–18px. Tabular figures available via `font-feature-settings: "tnum"`. |
| Noto Sans | Fallback 1 | Universal coverage; matches Inter's proportions reasonably well. |
| Arial | Fallback 2 | Universal system font. |
| sans-serif | Last resort | Browser default. |

### Composed font tokens

The four composed font tokens map to either Arabic or Latin based on use
case. **Always use the composed token, never the raw family token** —
this lets us swap faces in a future SDS major version without touching
component code.

| Token | Resolves to | Use |
| --- | --- | --- |
| `--snad-font-display` | `var(--snad-font-arabic)` | Hero text, marketing |
| `--snad-font-body` | `var(--snad-font-arabic)` | Body copy, form labels |
| `--snad-font-label` | `var(--snad-font-arabic)` | Buttons, captions |
| `--snad-font-numeric` | `var(--snad-font-latin)` | Numbers, dates, currency, IDs |

**Why Latin for numerics?** SNAD's product surface mixes Arabic body copy
with Latin numerals (Western Arabic digits 0–9). Latin numerals in Inter
are tabular and align cleanly in tables and dashboards. Tajawal also
supports Western digits but Inter's tabular figures are tighter and more
legible at small sizes.

## 2. Type scale

Each `--snad-text-*` token is a CSS shorthand: `font-size font-weight
line-height`. Use it as `font: var(--snad-text-h1);`.

| Token | Size | Weight | Line-height | Use |
| --- | --- | --- | --- | --- |
| `--snad-text-display` | 3rem (48px) | 800 | 1.15 | Hero moments, marketing landings |
| `--snad-text-h1` | 2.25rem (36px) | 800 | 1.2 | Page title (one per page) |
| `--snad-text-h2` | 1.875rem (30px) | 700 | 1.25 | Section title |
| `--snad-text-h3` | 1.5rem (24px) | 700 | 1.3 | Subsection title |
| `--snad-text-h4` | 1.25rem (20px) | 700 | 1.4 | Card title |
| `--snad-text-title` | 1.125rem (18px) | 600 | 1.45 | List item title |
| `--snad-text-subtitle` | 1rem (16px) | 600 | 1.5 | Subtitle, supporting heading |
| `--snad-text-body` | 1rem (16px) | 400 | 1.6 | Body copy (default) |
| `--snad-text-label` | 0.875rem (14px) | 600 | 1.4 | Form labels, dense UI |
| `--snad-text-caption` | 0.75rem (12px) | 400 | 1.4 | Captions, timestamps, metadata |
| `--snad-text-button` | 0.9375rem (15px) | 700 | 1.2 | Button text |
| `--snad-text-table` | 0.875rem (14px) | 500 | 1.5 | Table cells |
| `--snad-text-numeric` | 1rem (16px) | 600 | 1.4 | Numeric data (uses Latin font) |

### Line-height rationale

Body copy uses 1.6 — generous for Arabic, which needs more vertical space
than Latin because Arabic glyphs have more ascenders/descenders and the
script is connected. Headings use 1.2–1.3 (tighter, since they're short
and bold). Captions use 1.4 (slightly tighter than body to keep them
visually compact).

### Weight rationale

* **800** (ExtraBold) for display and H1 — high impact.
* **700** (Bold) for H2–H4 — clear hierarchy without being shouty.
* **600** (SemiBold) for titles, labels, subtitles — emphasis without
  overwhelming body copy.
* **500** (Medium) for table cells — slightly heavier than body for
  tabular data.
* **400** (Regular) for body and captions — default reading weight.

## 3. Arabic / English rules

### Mixed-direction copy

Arabic body copy frequently contains Latin words (brand names, technical
terms, numbers). The CSS `unicode-bidi: plaintext` rule (set on `body`)
lets the browser auto-detect direction for each run.

```css
body {
  font-family: var(--snad-font-body);
  unicode-bidi: plaintext;
}
```

For explicit mixed-direction spans:

```tsx
<span dir="auto">شركة SNAD للتقنية</span>
```

### Numerals

* Use **Western Arabic digits** (0–9) for all numerals — they align
  cleanly in tables and are universally readable.
* Set `font-family: var(--snad-font-numeric)` on numeric table cells,
  stat cards, and dashboards.
* Set `direction: ltr` on numeric runs inside Arabic copy so a value
  like `1,234.56` doesn't get comma-flipped.

```tsx
<td style={{ fontFamily: "var(--snad-font-numeric)", direction: "ltr" }}>
  1,234.56
</td>
```

### Punctuation

* Arabic copy uses Arabic punctuation: `،` (comma), `؛` (semicolon), `؟`
  (question mark), `«»` (guillemets).
* Never mix Latin punctuation into Arabic copy. The lint rule
  `check-arabic-punctuation` (planned for SDS 2.1) will catch this.

### Font loading

```tsx
// app/layout.tsx
import { Tajawal, Inter } from "next/font/google";

const tajawal = Tajawal({
  subsets: ["arabic", "latin"],
  weight: ["400", "500", "700", "800"],
  variable: "--font-snad-arabic",
  display: "swap",
});

const inter = Inter({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
  variable: "--font-snad-latin",
  display: "swap",
});

export default function RootLayout({ children }) {
  return (
    <html lang="ar" dir="rtl" className={`${tajawal.variable} ${inter.variable}`}>
      <body>{children}</body>
    </html>
  );
}
```

The CSS variables `--font-snad-arabic` and `--font-snad-latin` are
referenced by `--snad-font-arabic` and `--snad-font-latin` (via the legacy
shim in `app/snad-tokens.css`). The Next.js font optimizer inlines the
font CSS and preloads the woff2 files.

## 4. Length & spacing

* **Maximum line length** for body copy: 65–75 characters. Longer lines
  hurt reading speed; shorter lines feel choppy.
* **Paragraph spacing**: use `--snad-space-4` (16px) between paragraphs —
  equal to one line of body copy.
* **List spacing**: use `--snad-space-2` (8px) between list items.

## 5. Prohibited typography

1. ❌ Hardcoded `font-family` declarations anywhere except
   `design-system/tokens/theme.css`, `tokens.json`, and the legacy
   `snad-tokens.css` shim.
2. ❌ Using `--snad-font-arabic` or `--snad-font-latin` directly in
   components — always use the composed tokens (`--snad-font-body`,
   `--snad-font-display`, etc.).
3. ❌ Setting `font-size`, `font-weight`, or `line-height` independently
   in components — always use the composed `--snad-text-*` shorthand.
4. ❌ Mixing Latin punctuation into Arabic copy.
5. ❌ Using Eastern Arabic digits (٠١٢٣) in product UIs. Western digits
   (0123) are the standard.
6. ❌ Using `text-muted` for body copy — it falls below WCAG AA contrast.

## 6. Compliance

Run `python3 scripts/ci/check-design-system-compliance.py` to verify no
hardcoded `font-family` declarations slipped in. The script allows
`font-family: inherit`, `font-family: initial`, etc. — everything else
must reference `var(--snad-font-*)`.

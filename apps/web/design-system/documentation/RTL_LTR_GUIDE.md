# SNAD RTL / LTR Bidirectional Guide

> **Default direction:** RTL (Arabic-first)
> **Supported directions:** RTL (Arabic), LTR (English)
> **Switching:** via `<html dir="rtl|ltr" lang="ar|en">`

SNAD is an **Arabic-first** product. Every surface ships in Arabic
(right-to-left) by default, with English (left-to-right) as a first-class
alternative. This document specifies how to build UI that works in both
directions without conditional CSS.

## 1. The single rule

> **Never use physical properties (`left`, `right`, `top` as a directional
> axis). Always use logical properties (`inline-start`, `inline-end`,
> `block-start`, `block-end`).**

Logical properties automatically flip based on the element's `dir`
attribute. Physical properties do not — they always refer to the same
edge regardless of direction.

### Property mapping

| Physical (❌) | Logical (✅) | Notes |
| --- | --- | --- |
| `margin-left` | `margin-inline-start` | |
| `margin-right` | `margin-inline-end` | |
| `padding-left` | `padding-inline-start` | |
| `padding-right` | `padding-inline-end` | |
| `border-left` | `border-inline-start` | |
| `border-right` | `border-inline-end` | |
| `border-left-width` | `border-inline-start-width` | |
| `left` | `inset-inline-start` | For absolutely positioned elements |
| `right` | `inset-inline-end` | For absolutely positioned elements |
| `text-align: left` | `text-align: start` | |
| `text-align: right` | `text-align: end` | |
| `float: left` | `float: inline-start` | |
| `float: right` | `float: inline-end` | |
| `top` | `inset-block-start` | Top is "block-start" in horizontal writing modes |
| `bottom` | `inset-block-end` | |

## 2. Setting direction

The `<html>` element carries the global direction:

```tsx
// app/layout.tsx
export default function RootLayout({ children, params }: {
  children: ReactNode;
  params: { locale: "ar" | "en" };
}) {
  return (
    <html lang={params.locale} dir={params.locale === "ar" ? "rtl" : "ltr"}>
      <body>{children}</body>
    </html>
  );
}
```

The direction cascades. A page can override locally if needed (rare):

```tsx
// English content embedded in an Arabic page
<div dir="ltr" lang="en">
  <p>This English quote renders LTR even though the page is RTL.</p>
</div>
```

## 3. Mixed-direction content

Arabic body copy frequently contains Latin words (brand names, technical
terms, numbers, URLs). Use `dir="auto"` on the parent to let the browser
auto-detect direction for each run:

```tsx
<p dir="auto">شركة SNAD للتقنية تستخدم React 19.</p>
```

The browser will render `شركة` RTL, `SNAD` LTR, `للتقنية تستخدم` RTL,
`React 19` LTR, and the trailing period in the surrounding RTL context.

### `unicode-bidi: plaintext`

For body copy that may contain mixed-direction runs, set
`unicode-bidi: plaintext` on the container. This is already set on
`body` in `globals.css`:

```css
body {
  unicode-bidi: plaintext;
}
```

## 4. Icons

Directional icons (arrows, chevrons) must flip in RTL:

| LTR icon | RTL icon | When |
| --- | --- | --- |
| `←` (left arrow) | `→` (right arrow) | "Back" navigation |
| `→` (right arrow) | `←` (left arrow) | "Next" / "Forward" |
| `‹` (left chevron) | `›` (right chevron) | Carousel prev |
| `›` (right chevron) | `‹` (left chevron) | Carousel next |

Non-directional icons (close `×`, plus `+`, search `🔍`) do not flip.

Use CSS to flip:

```css
.icon-back {
  /* In LTR, the icon points left. In RTL, it points right. */
  transform: scaleX(1);
}
[dir="rtl"] .icon-back {
  transform: scaleX(-1);
}
```

Or use logical icon names and let the icon library handle it:

```tsx
import { ChevronStartIcon } from "@snad/icons";
// Renders ← in LTR, → in RTL automatically.
```

## 5. Numerals and dates

### Numerals

Use **Western Arabic digits** (0–9) for all numerals. They are
universally readable and align cleanly in tables. Eastern Arabic digits
(٠١٢٣٤٥٦٧٨٩) are not used in SNAD product UIs.

Numeric runs inside Arabic copy should be wrapped with `dir="ltr"` so a
value like `1,234.56` doesn't get comma-flipped:

```tsx
<p>
  إجمالي المبيعات: <span dir="ltr" style={{ fontFamily: "var(--snad-font-numeric)" }}>1,234.56</span> ريال
</p>
```

### Dates

Use the **Gregorian** calendar for all product UIs. The Hijri calendar is
supported in user-facing date pickers for religious dates only.

Format dates with `Intl.DateTimeFormat`:

```tsx
const fmt = new Intl.DateTimeFormat("ar-SA", {
  year: "numeric", month: "long", day: "numeric",
});
fmt.format(new Date()); // "٧ يوليو ٢٠٢٦" (Arabic month names, Western digits)
```

## 6. Flexbox and Grid

Flexbox and Grid auto-flip in RTL for inline-axis properties. But watch
out for these gotchas:

### Flexbox

```css
.row {
  display: flex;
  flex-direction: row;  /* LTR: items flow left-to-right. RTL: right-to-left. */
  gap: var(--snad-space-2);
}
```

`flex-direction: row` automatically reverses in RTL. You don't need
`row-reverse` — that would double-reverse and break the layout.

### Grid

```css
.grid {
  display: grid;
  grid-template-columns: 200px 1fr;  /* LTR: 200px on left. RTL: 200px on right. */
  gap: var(--snad-space-4);
}
```

Grid columns auto-flip in RTL. The first column ends up on the right in
RTL.

## 7. Position

Absolutely positioned elements should use logical `inset-inline-start` /
`inset-inline-end`:

```css
.drawer {
  position: fixed;
  inset-block: 0;             /* top: 0; bottom: 0; */
  inset-inline-end: 0;        /* right: 0 in LTR; left: 0 in RTL */
  width: min(520px, 94vw);
}
```

## 8. Borders

Asymmetric borders should use logical properties:

```css
.timeline-item {
  border-inline-start: 3px solid var(--snad-color-brand-primary);
  padding-inline-start: var(--snad-space-3);
}
```

In LTR, the border is on the left. In RTL, it's on the right. This is
the correct behavior for a timeline — the marker sits on the "start" side
of the reading direction.

## 9. Testing

### Manual

* Set `<html dir="rtl" lang="ar">` and walk through every flow.
* Set `<html dir="ltr" lang="en">` and walk through every flow.
* Toggle direction mid-session (rare but possible via user preference).

### Automated

* Playwright test that snapshots every page in both directions.
* Visual regression catches layout drift.
* Lint rule (planned for SDS 2.1) that flags physical `left`/`right`
  usage in CSS.

## 10. Common pitfalls

1. ❌ `margin-left: 16px` — breaks in RTL. Use `margin-inline-start: var(--snad-space-4)`.
2. ❌ `text-align: right` for Arabic — this is right in RTL but wrong in LTR. Use `text-align: start`.
3. ❌ Forgetting to flip directional icons.
4. ❌ Using `flex-direction: row-reverse` to "fix" RTL — `row` already does the right thing.
5. ❌ Hardcoding `dir="rtl"` on individual elements — set it on `<html>` and let it cascade.
6. ❌ Mixed-direction numerals without `dir="ltr"` wrapping.
7. ❌ Translating UI strings but not flipping layouts — both are required.
8. ❌ Using `direction: rtl` in CSS instead of `dir="rtl"` HTML attribute — `dir` is the correct mechanism.

## 11. Compliance

The lint script does not currently enforce logical properties (planned
for SDS 2.1 via a separate `check-rtl-compliance.py`). Until then, code
review must verify that every new CSS declaration uses logical properties
for inline-axis values.

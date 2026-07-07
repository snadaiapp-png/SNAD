/**
 * Vitest global setup — runs before every test file.
 *
 * Polyfills browser APIs that jsdom does not implement natively.
 * Currently polyfills:
 *   - window.matchMedia  (used by ThemeProvider to detect prefers-color-scheme)
 *
 * This file is referenced via vitest.config.ts → test.setupFiles.
 */
import "@testing-library/jest-dom/vitest";

if (typeof window !== "undefined" && typeof window.matchMedia !== "function") {
  window.matchMedia = (query: string): MediaQueryList => {
    const mql: MediaQueryList = {
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    };
    return mql;
  };
}

// jsdom doesn't implement IntersectionObserver either; some SDS components
// may use it for lazy-loading. Provide a no-op stub.
if (typeof window !== "undefined" && typeof window.IntersectionObserver === "undefined") {
  class IntersectionObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return [];
    }
    root = null;
    rootMargin = "";
    thresholds = [];
  }
  window.IntersectionObserver = IntersectionObserverStub as unknown as typeof IntersectionObserver;
}

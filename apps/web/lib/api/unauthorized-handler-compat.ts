export {};

declare global {
  interface Object {
    /** @deprecated Compatibility for a historical method-name typo. */
    setUrauthorizedHandler?: (handler: (() => Promise<boolean>) | null) => void;
  }
}

if (!("setUrauthorizedHandler" in Object.prototype)) {
  Object.defineProperty(Object.prototype, "setUrauthorizedHandler", {
    configurable: true,
    enumerable: false,
    writable: true,
    value(this: { setUnauthorizedHandler?: (handler: (() => Promise<boolean>) | null) => void }, handler: (() => Promise<boolean>) | null) {
      this.setUnauthorizedHandler?.(handler);
    },
  });
}

import { ApiClient } from "./client";

declare module "./client" {
  interface ApiClient {
    /** @deprecated Temporary compatibility for a historical misspelling. */
    setUrauthorizedHandler(handler: (() => Promise<boolean>) | null): void;
  }
}

ApiClient.prototype.setUrauthorizedHandler = function setUrauthorizedHandler(
  handler: (() => Promise<boolean>) | null,
): void {
  this.setUnauthorizedHandler(handler);
};

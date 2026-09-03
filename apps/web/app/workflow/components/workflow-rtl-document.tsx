"use client";

import { useEffect } from "react";

/**
 * Applies the Workflow surface's Arabic-first document semantics while the
 * route is mounted, then restores the previous document attributes on exit.
 */
export function WorkflowRtlDocument() {
  useEffect(() => {
    const previousDir = document.documentElement.getAttribute("dir");
    const previousLang = document.documentElement.getAttribute("lang");

    document.documentElement.dir = "rtl";
    document.documentElement.lang = "ar";

    return () => {
      if (previousDir === null) document.documentElement.removeAttribute("dir");
      else document.documentElement.dir = previousDir;

      if (previousLang === null) document.documentElement.removeAttribute("lang");
      else document.documentElement.lang = previousLang;
    };
  }, []);

  return null;
}

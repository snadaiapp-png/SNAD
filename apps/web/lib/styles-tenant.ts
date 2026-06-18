import { injectStyle } from "./inject-style";

injectStyle("sanad-tenant", `.tenant-console{display:grid;gap:22px;align-items:end;border:1px solid #d8ece8;border-radius:18px;background:#fff;padding:20px}.tenant-console p{margin:6px 0 0;color:var(--muted);line-height:1.7}.tenant-controls{display:grid;gap:9px;align-items:center}@media(min-width:700px){.tenant-console{grid-template-columns:1fr 1.4fr}.tenant-controls{grid-template-columns:1fr auto auto}}`);

const css = `:root{--ink:#17202a;--muted:#667085;--line:#e7e9ee;--surface:#fff;--soft:#f7f8fa;--canvas:#f2f4f7;--brand:#0f766e;--danger:#b42318;--success:#027a48;--warning:#b54708}*{box-sizing:border-box}html{background:var(--canvas);color:var(--ink);font-family:Segoe UI,Tahoma,Arial,sans-serif}body{min-height:100vh;margin:0;background:radial-gradient(circle at 20% 0,rgba(15,118,110,.09),transparent 28rem),var(--canvas)}button,input,textarea,select{font:inherit}button{cursor:pointer}button:disabled{cursor:not-allowed;opacity:.6}input,textarea,select{width:100%;border:1px solid #d0d5dd;border-radius:11px;background:#fff;padding:11px 12px;outline:none}input:focus,textarea:focus,select:focus{border-color:var(--brand);box-shadow:0 0 0 4px rgba(15,118,110,.1)}label{display:grid;gap:7px;color:#344054;font-size:13px;font-weight:700}.primary,.secondary{border-radius:11px;padding:10px 15px;font-weight:800}.primary{border:1px solid var(--brand);background:var(--brand);color:#fff}.secondary{border:1px solid #d0d5dd;background:#fff;color:#344054}.danger{color:var(--danger)!important;background:#fef3f2!important}.link{border:0;background:transparent;color:var(--brand);font-weight:800}.feedback{margin-top:15px;border-radius:12px;padding:12px 14px;font-weight:700}.feedback.success{background:#ecfdf3;color:var(--success)}.feedback.error{background:#fef3f2;color:var(--danger)}`;

if (typeof document !== "undefined" && !document.getElementById("sanad-base-styles")) {
  const style = document.createElement("style");
  style.id = "sanad-base-styles";
  style.textContent = css;
  document.head.append(style);
}

export {};

import { createRequire } from "node:module";
import fs from "node:fs";
const require = createRequire(new URL("../../apps/web/package.json", import.meta.url));
const { chromium } = require("@playwright/test");
const base=(process.env.PRODUCTION_BASE_URL||"").replace(/\/$/,"");
const email=process.env.PROD_QA_ADMIN_EMAIL||"";
const password=process.env.PROD_QA_ADMIN_PASSWORD||"";
const tenantId=process.env.PROD_QA_TENANT_ID||"";
const defId=process.env.WORKFLOW_DEFINITION_ID||"";
const code=process.env.WORKFLOW_DEFINITION_CODE||"";
const expectedSha=process.env.EXPECTED_MAIN_SHA||"";
const out=process.env.VISUAL_EVIDENCE_DIR||"workflow-production-visual-evidence";
fs.mkdirSync(out,{recursive:true});
if(!base||!email||!password||!tenantId||!defId||!code||!expectedSha) throw new Error("Missing required visual proof environment");
const rel=await fetch(`${base}/api/system/release`).then(async r=>{if(!r.ok)throw new Error(`release HTTP ${r.status}`);return r.json()});
if(rel.commitSha!==expectedSha) throw new Error(`Vercel SHA mismatch ${rel.commitSha} != ${expectedSha}`);
const browser=await chromium.launch({headless:true});
const page=await browser.newPage({viewport:{width:1440,height:1000}});
const consoleErrors=[]; page.on("console",m=>{if(m.type()==="error")consoleErrors.push(m.text())}); page.on("pageerror",e=>consoleErrors.push(`pageerror:${e.message}`));
await page.route("**/api/platform/api/v1/auth/login",async route=>{
  const request=route.request();
  if(request.method()!=="POST"){await route.continue();return;}
  const requestBody=request.postDataJSON()||{};
  await route.continue({
    headers:{...request.headers(),"content-type":"application/json"},
    postData:JSON.stringify({...requestBody,tenantId}),
  });
},{times:1});
await page.goto(base,{waitUntil:"domcontentloaded",timeout:60000});
await page.locator("#login-email").waitFor({state:"visible",timeout:30000});
await page.locator("#login-email").fill(email);
await page.locator("#login-password").fill(password);
const loginRequestPromise=page.waitForRequest(request=>
  request.method()==="POST" &&
  request.url().includes("/api/platform/api/v1/auth/login"),
{timeout:30000});
await page.locator('form button[type="submit"]').click();
const loginRequest=await loginRequestPromise;
const loginResponse=await loginRequest.response();
if(!loginResponse) throw new Error("Tenant B UI login produced no HTTP response");
if(!loginResponse.ok()) throw new Error(`Tenant B UI login HTTP ${loginResponse.status()}`);
const loginBody=await loginResponse.json();
if(loginBody?.user?.tenantId!==tenantId) throw new Error("Tenant B UI login resolved to unexpected tenant");
if(loginBody?.credentialRotationRequired===true) throw new Error("Tenant B UI login unexpectedly requires credential rotation");
await page.waitForURL(url=>url.pathname==="/workspace"||url.pathname.startsWith("/workspace/"),{timeout:30000});
await page.locator("#login-email").waitFor({state:"hidden",timeout:30000});
await page.goto(`${base}/workflow`,{waitUntil:"domcontentloaded",timeout:60000});
await page.getByRole("heading",{name:"محرك سير العمل"}).waitFor({state:"visible",timeout:30000});
const dir=await page.evaluate(()=>document.documentElement.getAttribute("dir")); const lang=await page.evaluate(()=>document.documentElement.getAttribute("lang"));
if(dir!=="rtl"||lang!=="ar") throw new Error(`RTL contract failed dir=${dir} lang=${lang}`);
await page.screenshot({path:`${out}/workflow-production-home-desktop.png`,fullPage:true});
await page.getByRole("tab",{name:"التعريفات"}).click();
await page.getByRole("heading",{name:"تعريفات سير العمل"}).waitFor({state:"visible",timeout:30000});
await page.getByRole("textbox",{name:"بحث"}).fill(code);
const row=page.locator('[data-testid="definitions-table"] tbody tr').filter({hasText:code}); if(await row.count()!==1) throw new Error(`Expected one definition row for ${code}`);
await page.screenshot({path:`${out}/workflow-production-definition-row.png`,fullPage:true});
await row.getByRole("link",{name:"فتح المصمم"}).click();
await page.waitForURL(new RegExp(`/workflow/definitions/${defId}$`),{timeout:30000});
await page.getByRole("heading",{name:"Production 3-User Journey"}).waitFor({state:"visible",timeout:30000});
const palette=page.getByRole("complementary",{name:"مكتبة الخطوات"}); const inspector=page.getByRole("complementary",{name:"مفتش سير العمل"}); const canvas=page.locator('[aria-label="لوحة تصميم سير العمل"][tabindex="0"]');
await palette.waitFor({state:"visible"}); await inspector.waitFor({state:"visible"}); await canvas.waitFor({state:"visible"});
await page.screenshot({path:`${out}/workflow-production-designer-desktop.png`,fullPage:true});
await page.setViewportSize({width:390,height:844}); await palette.waitFor({state:"visible"}); await inspector.waitFor({state:"visible"}); await canvas.waitFor({state:"visible"});
await page.screenshot({path:`${out}/workflow-production-designer-mobile.png`,fullPage:true});
fs.writeFileSync(`${out}/visual-evidence.json`,JSON.stringify({result:"PASS",baseUrl:base,releaseSha:expectedSha,tenantId,definitionId:defId,definitionCode:code,rtl:dir,lang,screenshots:["workflow-production-home-desktop.png","workflow-production-definition-row.png","workflow-production-designer-desktop.png","workflow-production-designer-mobile.png"],consoleErrors},null,2));
await browser.close(); console.log("Production Workflow visual proof: PASSED");

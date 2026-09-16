import { expect, test, type APIRequestContext } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3001";
const API = process.env.SANAD_BACKEND_BASE_URL ?? "http://127.0.0.1:8080";
const PASSWORD = process.env.WF_E2E_PASSWORD ?? "WfE2eTest!2026";

const DESIGNER = "wf-e2e-designer@snad-e2e.example";
const PUBLISHER = "wf-e2e-publisher@snad-e2e.example";

type Definition = {
  id: string;
  code: string;
  name: string;
  version: number;
  versionLock: number;
  publicationState: string;
  engineGeneration: string;
};

type Step = {
  id: string;
  stepKey: string;
  name: string;
  stepType: string;
};

async function tokenFor(request: APIRequestContext, email: string) {
  const response = await request.post(`${API}/api/v1/auth/login`, {
    data: { email, password: PASSWORD },
  });
  expect(response.status(), `real login must succeed for ${email}`).toBe(200);
  const body = await response.json() as { accessToken?: string };
  expect(body.accessToken).toBeTruthy();
  return body.accessToken as string;
}

function headers(token: string) {
  return {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };
}

async function seedY2Draft(request: APIRequestContext) {
  const designerToken = await tokenFor(request, DESIGNER);
  const publisherToken = await tokenFor(request, PUBLISHER);
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  const code = `BROWSER-DESIGNER-${suffix}`;
  const name = `مصمم قبول المتصفح ${suffix}`;

  const create = await request.post(`${API}/api/v1/workflows/definitions`, {
    headers: headers(designerToken),
    data: {
      code,
      name,
      description: "Workflow Designer Browser Acceptance Gate",
      module: "GENERAL",
      triggerType: "MANUAL",
    },
  });
  expect(create.status(), "definition create must succeed").toBe(200);
  const legacy = await create.json() as Definition;
  expect(legacy.publicationState).toBe("DRAFT");

  const startResponse = await request.post(
    `${API}/api/v1/workflows/definitions/${legacy.id}/steps`,
    {
      headers: headers(designerToken),
      data: {
        stepKey: "seed_start",
        name: "بداية البذرة",
        stepType: "START",
        sequenceOrder: 1,
        configuration: "{}",
        slaHours: 0,
        requiredCapability: "",
        requiredRole: "",
      },
    },
  );
  expect(startResponse.status()).toBe(200);
  const start = await startResponse.json() as Step;

  const endResponse = await request.post(
    `${API}/api/v1/workflows/definitions/${legacy.id}/steps`,
    {
      headers: headers(designerToken),
      data: {
        stepKey: "seed_end",
        name: "نهاية البذرة",
        stepType: "END",
        sequenceOrder: 2,
        configuration: "{}",
        slaHours: 0,
        requiredCapability: "",
        requiredRole: "",
      },
    },
  );
  expect(endResponse.status()).toBe(200);
  const end = await endResponse.json() as Step;

  const transition = await request.post(
    `${API}/api/v1/workflows/definitions/${legacy.id}/transitions`,
    {
      headers: headers(designerToken),
      data: {
        fromStepId: start.id,
        toStepId: end.id,
        transitionKey: "seed_direct",
        outcome: "SUCCESS",
        conditionAst: "{}",
        priority: 10,
        metadata: "{}",
      },
    },
  );
  expect(transition.status()).toBe(200);

  const validation = await request.post(
    `${API}/api/v1/workflows/definitions/${legacy.id}/validate`,
    { headers: headers(designerToken), data: {} },
  );
  expect(validation.status()).toBe(200);
  expect((await validation.json() as { valid: boolean }).valid).toBe(true);

  const publish = await request.post(
    `${API}/api/v1/workflows/definitions/${legacy.id}/publish`,
    {
      headers: headers(publisherToken),
      data: { expectedVersion: legacy.versionLock },
    },
  );
  expect(publish.status()).toBe(200);
  const published = await publish.json() as Definition;
  expect(published.publicationState).toBe("PUBLISHED");
  expect(published.engineGeneration).toBe("Y2");

  const nextDraftResponse = await request.post(
    `${API}/api/v1/workflows/definitions/${published.id}/next-draft`,
    { headers: headers(designerToken), data: {} },
  );
  expect(nextDraftResponse.status()).toBe(200);
  const draft = await nextDraftResponse.json() as Definition;
  expect(draft.publicationState).toBe("DRAFT");
  expect(draft.engineGeneration).toBe("Y2");

  return { draft, designerToken, code, name };
}

async function loginThroughUi(page: import("@playwright/test").Page) {
  await page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });
  await page.locator("#login-email").waitFor({ state: "visible", timeout: 30_000 });
  await page.locator("#login-email").fill(DESIGNER);
  await page.locator("#login-password").fill(PASSWORD);
  await page.locator("#login-password").press("Enter");
  await page.locator("#login-email").waitFor({ state: "hidden", timeout: 30_000 });
}

test("Designer Browser Acceptance Gate — real Y2 designer journey", async ({ page, request }, testInfo) => {
  const seeded = await seedY2Draft(request);

  await loginThroughUi(page);

  await page.goto(`${BASE_URL}/workflow`, { waitUntil: "domcontentloaded" });
  await page.getByRole("heading", { name: "محرك سير العمل" })
    .waitFor({ state: "visible", timeout: 30_000 });

  const dir = await page.evaluate(() => document.documentElement.getAttribute("dir"));
  const lang = await page.evaluate(() => document.documentElement.getAttribute("lang"));
  expect(dir).toBe("rtl");
  expect(lang).toBe("ar");

  await page.getByRole("tab", { name: "التعريفات" }).click();
  await page.getByRole("heading", { name: "تعريفات سير العمل" }).waitFor({ state: "visible" });
  await page.getByRole("textbox", { name: "بحث" }).fill(seeded.code);

  const row = page.locator('[data-testid="definitions-table"] tbody tr').filter({ hasText: seeded.code });
  await expect(row).toHaveCount(1);
  await row.getByRole("link", { name: "فتح المصمم" }).click();

  await expect(page).toHaveURL(new RegExp(`/workflow/definitions/${seeded.draft.id}$`));
  await expect(page.getByRole("heading", { name: seeded.name })).toBeVisible();

  const palette = page.getByRole("complementary", { name: "مكتبة الخطوات" });
  const inspector = page.getByRole("complementary", { name: "مفتش سير العمل" });
  const canvas = page.locator('[aria-label="لوحة تصميم سير العمل"][tabindex="0"]');
  await expect(palette).toBeVisible();
  await expect(inspector).toBeVisible();
  await expect(canvas).toBeVisible();

  // Palette → real persisted step → Inspector.
  await palette.getByRole("button", { name: /مهمة بشرية/ }).click();
  await expect(inspector.getByText("خطوة جديدة غير محفوظة")).toBeVisible();
  await inspector.getByLabel("مفتاح الخطوة").fill("browser_task");
  await inspector.getByLabel("الاسم").fill("مهمة قبول المتصفح");
  await inspector.getByLabel("القدرة المطلوبة").fill("WORKFLOW.TASK_EXECUTE");
  await inspector.getByRole("button", { name: "حفظ الخطوة في الخادم" }).click();

  const taskNode = canvas.getByRole("button", { name: /مهمة قبول المتصفح/ });
  await expect(taskNode).toBeVisible();
  await taskNode.click();
  await expect(inspector.getByLabel("تفاصيل الخطوة المحفوظة")).toContainText("browser_task");

  // Complete the graph through the same real backend, then reload the designer.
  const stepsResponse = await request.get(
    `${API}/api/v1/workflows/definitions/${seeded.draft.id}/steps`,
    { headers: headers(seeded.designerToken) },
  );
  expect(stepsResponse.status()).toBe(200);
  const steps = await stepsResponse.json() as Step[];
  const start = steps.find((step) => step.stepKey === "seed_start");
  const end = steps.find((step) => step.stepKey === "seed_end");
  const task = steps.find((step) => step.stepKey === "browser_task");
  expect(start).toBeTruthy();
  expect(end).toBeTruthy();
  expect(task).toBeTruthy();

  const branchTransition = await request.post(
    `${API}/api/v1/workflows/definitions/${seeded.draft.id}/transitions`,
    {
      headers: headers(seeded.designerToken),
      data: {
        fromStepId: start!.id,
        toStepId: task!.id,
        transitionKey: "browser_branch",
        outcome: "ALTERNATE",
        conditionAst: "{}",
        priority: 20,
        metadata: "{}",
      },
    },
  );
  expect(branchTransition.status()).toBe(200);

  const taskToEnd = await request.post(
    `${API}/api/v1/workflows/definitions/${seeded.draft.id}/transitions`,
    {
      headers: headers(seeded.designerToken),
      data: {
        fromStepId: task!.id,
        toStepId: end!.id,
        transitionKey: "browser_complete",
        outcome: "SUCCESS",
        conditionAst: "{}",
        priority: 10,
        metadata: "{}",
      },
    },
  );
  expect(taskToEnd.status()).toBe(200);

  await page.getByRole("button", { name: "تحديث من الخادم" }).click();
  await expect(page.getByLabel("انتقال browser_branch: ALTERNATE")).toBeVisible();
  await expect(page.getByLabel("انتقال browser_complete: SUCCESS")).toBeVisible();

  // Canvas keyboard contract.
  await canvas.focus();
  await canvas.press("f");
  await canvas.press("+");
  await canvas.press("-");
  await expect(page.getByText("معاينة بنيوية · ليست حالة تنفيذ إنتاجية")).toBeVisible();

  // Server-authoritative validation and side-effect-free simulation.
  await page.getByRole("button", { name: "1. تحقق" }).click();
  await expect(page.getByText("التحقق من الخادم: صالح للنشر ✓")).toBeVisible({ timeout: 20_000 });
  await page.getByRole("button", { name: "2. محاكاة" }).click();
  await expect(page.getByRole("status").filter({ hasText: "محاكاة غير إنتاجية" })).toBeVisible({ timeout: 20_000 });

  // Diagnostics tabs + keyboard roving focus.
  const validationTab = page.getByRole("tab", { name: "Validation" });
  const simulationTab = page.getByRole("tab", { name: "Simulation" });
  const activityTab = page.getByRole("tab", { name: "Activity" });
  await validationTab.focus();
  await validationTab.press("ArrowRight");
  await expect(simulationTab).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("tabpanel", { name: "Simulation" })).toContainText("الخطوات التي تمت زيارتها");
  await simulationTab.press("End");
  await expect(activityTab).toHaveAttribute("aria-selected", "true");
  await activityTab.press("Home");
  await expect(validationTab).toHaveAttribute("aria-selected", "true");

  // Axe evidence on the real designer route.
  const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
  await testInfo.attach("axe-workflow-designer.json", {
    body: Buffer.from(JSON.stringify(axe, null, 2)),
    contentType: "application/json",
  });
  const blockingA11y = axe.violations.filter((violation) => violation.impact === "critical");
  expect(blockingA11y, blockingA11y.map((violation) => violation.id).join(", ")).toHaveLength(0);

  const desktopShot = testInfo.outputPath("workflow-designer-desktop.png");
  await page.screenshot({ path: desktopShot, fullPage: true });
  await testInfo.attach("workflow-designer-desktop", { path: desktopShot, contentType: "image/png" });

  // Responsive acceptance on a real mobile viewport.
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("heading", { name: seeded.name })).toBeVisible();
  await expect(palette).toBeVisible();
  await expect(canvas).toBeVisible();
  await expect(inspector).toBeVisible();

  const mobileShot = testInfo.outputPath("workflow-designer-mobile.png");
  await page.screenshot({ path: mobileShot, fullPage: true });
  await testInfo.attach("workflow-designer-mobile", { path: mobileShot, contentType: "image/png" });

  await testInfo.attach("workflow-designer-evidence.json", {
    body: Buffer.from(JSON.stringify({
      definitionId: seeded.draft.id,
      route: `/workflow/definitions/${seeded.draft.id}`,
      definitionCode: seeded.code,
      rtl: dir,
      lang,
      validation: "PASS",
      simulation: "PASS",
      screenshots: ["workflow-designer-desktop.png", "workflow-designer-mobile.png"],
    }, null, 2)),
    contentType: "application/json",
  });
});

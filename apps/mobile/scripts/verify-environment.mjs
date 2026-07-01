const [major, minor] = process.versions.node.split(".").map(Number);
const supportedNode = major > 22 || (major === 22 && minor >= 13);

if (!supportedNode || major >= 25) {
  console.error(`Unsupported Node.js ${process.versions.node}. Use Node >=22.13 and <25.`);
  process.exit(1);
}

const appEnv = process.env.EXPO_PUBLIC_APP_ENV ?? "development";
const apiBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL;
const easProjectId = process.env.EAS_PROJECT_ID;

if (!apiBaseUrl) {
  console.warn("EXPO_PUBLIC_API_BASE_URL is not configured; local Android fallback will be used.");
}

if (!easProjectId) {
  console.warn("EAS_PROJECT_ID is not configured; cloud updates and push tokens stay disabled.");
}

if (appEnv === "production" && (!apiBaseUrl || !easProjectId)) {
  console.error("Production requires EXPO_PUBLIC_API_BASE_URL and EAS_PROJECT_ID.");
  process.exit(1);
}

console.log(JSON.stringify({
  node: process.versions.node,
  appEnv,
  apiConfigured: Boolean(apiBaseUrl),
  easConfigured: Boolean(easProjectId),
}, null, 2));

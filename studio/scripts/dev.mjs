import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { findAvailablePort } from "../server/src/services/availablePort.ts";

const studioRoot = fileURLToPath(new URL("../", import.meta.url));
const requestedApiPort = Number(process.env.STUDIO_PORT ?? 4310);
const requestedWebPort = Number(process.env.STUDIO_WEB_PORT ?? 5180);
const [apiPort, webPort] = await Promise.all([
  findAvailablePort(requestedApiPort),
  findAvailablePort(requestedWebPort),
]);

const serverEnv = { ...process.env, STUDIO_PORT: String(apiPort) };
const webEnv = {
  ...process.env,
  STUDIO_PORT: String(apiPort),
  STUDIO_WEB_PORT: String(webPort),
};
console.log(`Urkaaaz Studio API: http://127.0.0.1:${apiPort}`);
console.log(`Urkaaaz Studio web: http://127.0.0.1:${webPort}`);

const processes = [
  spawn("npm", ["run", "start", "--workspace", "server"], {
    cwd: studioRoot,
    env: serverEnv,
    stdio: "inherit",
  }),
  spawn("npm", ["run", "dev", "--workspace", "web"], {
    cwd: studioRoot,
    env: webEnv,
    stdio: "inherit",
  }),
];

let stopping = false;
function stopAll(code) {
  if (stopping) return;
  stopping = true;
  for (const child of processes) child.kill("SIGTERM");
  process.exitCode = code;
}

for (const child of processes) child.on("exit", code => stopAll(code ?? 0));
process.on("SIGINT", () => stopAll(0));
process.on("SIGTERM", () => stopAll(0));

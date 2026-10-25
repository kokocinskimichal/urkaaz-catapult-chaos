import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const studioRoot = fileURLToPath(new URL("../", import.meta.url));

const processes = [
  spawn("npm", ["run", "start", "--workspace", "server"], { cwd: studioRoot, stdio: "inherit" }),
  spawn("npm", ["run", "dev", "--workspace", "web"], { cwd: studioRoot, stdio: "inherit" }),
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

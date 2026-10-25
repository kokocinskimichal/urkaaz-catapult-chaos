import { access } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const REPO_MARKER = "settings.gradle.kts";

export async function resolveRepoRoot(
  override: string | undefined = process.env.STUDIO_REPO_ROOT,
  start: string = dirname(fileURLToPath(import.meta.url)),
): Promise<string> {
  if (override) {
    const root = resolve(override);
    await assertRepoRoot(root);
    return root;
  }
  let current = resolve(start);
  for (;;) {
    if (await hasMarker(current)) return current;
    const parent = dirname(current);
    if (parent === current) throw new Error(`Repository root not found above ${start}`);
    current = parent;
  }
}

async function hasMarker(directory: string): Promise<boolean> {
  try {
    await access(join(directory, REPO_MARKER));
    return true;
  } catch {
    return false;
  }
}

async function assertRepoRoot(root: string): Promise<void> {
  if (!(await hasMarker(root))) throw new Error(`${root} is not an Urkaaaz repository root (no ${REPO_MARKER})`);
}

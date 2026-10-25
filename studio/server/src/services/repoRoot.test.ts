import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { resolveRepoRoot } from "./repoRoot.ts";

const created: string[] = [];

async function tempRepo(): Promise<string> {
  const root = await mkdtemp(join(tmpdir(), "studio-root-"));
  created.push(root);
  await writeFile(join(root, "settings.gradle.kts"), "");
  return root;
}

afterEach(async () => {
  await Promise.all(created.splice(0).map(path => rm(path, { recursive: true, force: true })));
});

describe("resolveRepoRoot", () => {
  it("walks up from a nested directory to the repository marker", async () => {
    const root = await tempRepo();
    const nested = join(root, "studio", "server", "src");
    await mkdir(nested, { recursive: true });
    expect(await resolveRepoRoot(undefined, nested)).toBe(root);
  });

  it("accepts an explicit override that is a repository root", async () => {
    const root = await tempRepo();
    expect(await resolveRepoRoot(root)).toBe(root);
  });

  it("rejects an override that is not a repository root", async () => {
    const empty = await mkdtemp(join(tmpdir(), "studio-empty-"));
    created.push(empty);
    await expect(resolveRepoRoot(empty)).rejects.toThrow(/not an Urkaaaz repository root/);
  });
});

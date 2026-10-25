import { execFile } from "node:child_process";
import { promisify } from "node:util";

const run = promisify(execFile);

export interface GitState {
  branch: string | null;
  changedFiles: number | null;
}

export type GitStateReader = (repoRoot: string) => Promise<GitState>;

// Read-only: Studio never changes git state, it only reports it.
export const readGitState: GitStateReader = async repoRoot => {
  try {
    const [branch, status] = await Promise.all([
      run("git", ["-C", repoRoot, "rev-parse", "--abbrev-ref", "HEAD"]),
      run("git", ["-C", repoRoot, "status", "--porcelain"]),
    ]);
    const changedFiles = status.stdout.split("\n").filter(line => line.trim().length > 0).length;
    return { branch: branch.stdout.trim(), changedFiles };
  } catch {
    return { branch: null, changedFiles: null };
  }
};

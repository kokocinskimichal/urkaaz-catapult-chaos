import { readdir, readFile, realpath, stat } from "node:fs/promises";
import { isAbsolute, join, relative, sep } from "node:path";

const TERRAIN_DIRECTORY = ["game-assets", "src", "main", "resources", "terrain"] as const;
const DRAWABLE_DIRECTORY = ["android-app", "src", "main", "res", "drawable-nodpi"] as const;
const DRAWABLE_RELIC = /^terrain_(green_frontier|frost|copperwood|ashen_march)_(?:relic|detail)_\d{2}_[^.]+\.png$/;
const SHARED_RELIC = /^relic_medium_[a-z0-9_]+\.png$/;

export async function readTerrainAssetIndex(repoRoot: string): Promise<string> {
  const terrainRoot = join(repoRoot, ...TERRAIN_DIRECTORY);
  const indexPath = join(terrainRoot, "asset-index.txt");
  const sourceIndex = (await readFile(indexPath, "utf8"))
    .split(/\r?\n/)
    .map(name => name.trim())
    .filter(Boolean);
  const drawableNames = await readdir(join(repoRoot, ...DRAWABLE_DIRECTORY));
  const additions = drawableNames.filter(name => DRAWABLE_RELIC.test(name) || SHARED_RELIC.test(name));
  return `${[...new Set([...sourceIndex, ...additions])].sort().join("\n")}\n`;
}

export async function resolveTerrainAssetPath(repoRoot: string, relativeAssetPath: string): Promise<string | null> {
  const normalized = relativeAssetPath.replaceAll("\\", "/");
  if (!normalized || normalized.startsWith("/") || normalized.split("/").some(part => part === "." || part === "..")) {
    return null;
  }
  const parts = normalized.split("/");
  if (parts.length > 2 || parts.some(part => !part) || !parts.at(-1)?.endsWith(".png")) return null;
  const fileName = parts.at(-1)!;
  const directory = parts.length === 2 ? parts[0] : "";

  const terrainRoot = join(repoRoot, ...TERRAIN_DIRECTORY);
  const terrainPath = join(terrainRoot, ...parts);
  if (await isSafeFile(repoRoot, terrainPath)) return terrainPath;
  if (!directory) return null;

  const drawablePath = join(repoRoot, ...DRAWABLE_DIRECTORY, fileName);
  const isRegisteredDrawable = DRAWABLE_RELIC.test(fileName) || SHARED_RELIC.test(fileName) ||
    fileName === "background_sunken_marshes.png";
  if (!isRegisteredDrawable || !await isSafeFile(repoRoot, drawablePath)) return null;
  const expectedDirectory = SHARED_RELIC.test(fileName) ? "shared" :
    fileName === "background_sunken_marshes.png" ? "sunken_marshes" :
      DRAWABLE_RELIC.exec(fileName)?.[1];
  return directory === expectedDirectory ? drawablePath : null;
}

async function isSafeFile(repoRoot: string, path: string): Promise<boolean> {
  try {
    const [root, actualPath, file] = await Promise.all([
      realpath(repoRoot),
      realpath(path),
      stat(path),
    ]);
    const relativePath = relative(root, actualPath);
    const isWithinRoot = relativePath === "" ||
      (!relativePath.startsWith(`..${sep}`) && relativePath !== ".." && !isAbsolute(relativePath));
    return isWithinRoot && file.isFile();
  } catch {
    return false;
  }
}

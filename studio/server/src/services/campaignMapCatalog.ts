import { createHash, randomUUID } from "node:crypto";
import { createReadStream } from "node:fs";
import { open, readFile, realpath, rename, rm, stat, writeFile } from "node:fs/promises";
import { isAbsolute, join, relative, sep } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import type {
  CampaignChapterCatalogEntry,
  CampaignChapterCatalogResponse,
  CampaignMapLayoutDocumentResponse,
  CampaignMapLayoutValidationIssue,
  CampaignMapLayoutValidationResponse,
} from "../contracts/campaign.ts";

const CHAPTER_CATALOG_PATH = ["game-campaign", "src", "main", "resources", "campaign-chapters.json"] as const;
const LAYOUT_DIRECTORY = ["game-campaign", "src", "main", "resources", "campaign-map-layouts"] as const;
const CAMPAIGN_MAP_DIRECTORY = ["assets_src", "campaign", "chapter-maps"] as const;
const CAMPAIGN_UI_DIRECTORY = ["assets_src", "campaign", "ui"] as const;
const CAMPAIGN_MARKER_DIRECTORY = ["assets_src", "campaign", "map-markers"] as const;
const CHAPTER_ID = /^[a-z][a-z0-9_]*$/;
const ASSET_KEY = /^[a-z][a-z0-9_]*$/;
const CHAPTER_MAP_KEY = /^campaign_chapter_[0-9]{2}_[a-z0-9_]+_map$/;
const SCHEMA_VERSION = 1;
const lockByChapter = new Map<string, Promise<void>>();
const FILE_LOCK_WAIT_MILLISECONDS = 10_000;
const FILE_LOCK_STALE_MILLISECONDS = 5_000;
const FILE_LOCK_RETRY_MILLISECONDS = 25;

const FIXED_ASSETS: Readonly<Record<string, readonly string[]>> = {
  campaign_map_frame_overlay: [...CAMPAIGN_UI_DIRECTORY, "campaign_map_frame_overlay.png"],
  campaign_map_marker_fortress: [...CAMPAIGN_MARKER_DIRECTORY, "campaign_map_marker_fortress.png"],
  campaign_map_marker_boss: [...CAMPAIGN_MARKER_DIRECTORY, "campaign_map_marker_boss.png"],
};

export class CampaignLayoutConflictError extends Error {
  readonly currentHash: string | null;

  constructor(currentHash: string | null) {
    super("Campaign map layout changed since it was loaded");
    this.currentHash = currentHash;
  }
}

export class CampaignLayoutLockTimeoutError extends Error {
  constructor() {
    super("Another Studio instance is still saving this campaign layout");
  }
}

export class CampaignLayoutValidationError extends Error {
  readonly issues: CampaignMapLayoutValidationIssue[];

  constructor(issues: CampaignMapLayoutValidationIssue[]) {
    super("Campaign map layout validation failed");
    this.issues = issues;
  }
}

export class CampaignLayoutArtworkMissingError extends Error {
  readonly chapterId: string;

  constructor(chapterId: string) {
    super(`Campaign map artwork for '${chapterId}' is not available`);
    this.chapterId = chapterId;
  }
}

export class CampaignChapterNotFoundError extends Error {
  readonly chapterId: string;

  constructor(chapterId: string) {
    super(`Campaign chapter '${chapterId}' was not found`);
    this.chapterId = chapterId;
  }
}

export async function listCampaignChapters(repoRoot: string): Promise<CampaignChapterCatalogResponse> {
  const catalog = await readChapterCatalog(repoRoot);
  const chapters = await Promise.all(catalog.chapters.map(async chapter => ({
    ...chapter,
    mapAvailable: await chapterHasMapArtwork(repoRoot, chapter.id),
  })));
  return { chapters };
}

export async function readCampaignMapLayout(
  repoRoot: string,
  chapterId: string,
): Promise<CampaignMapLayoutDocumentResponse | null> {
  const catalog = await readChapterCatalog(repoRoot);
  const chapter = catalog.chapters.find(candidate => candidate.id === chapterId);
  if (!chapter) throw new CampaignChapterNotFoundError(chapterId);

  const content = await readLayoutFile(repoRoot, chapterId);
  if (content === null) return null;
  const validation = await validateCampaignMapLayout(repoRoot, chapter, content);
  if (!validation.valid) throw new CampaignLayoutValidationError(validation.issues);
  return { chapterId, content, hash: hash(content) };
}

export async function validateCampaignMapLayout(
  repoRoot: string,
  chapter: CampaignChapterCatalogEntry,
  content: string,
): Promise<CampaignMapLayoutValidationResponse> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(content);
  } catch {
    return invalid("layout", "Layout must contain valid JSON");
  }
  if (!isRecord(parsed)) return invalid("layout", "Layout must be a JSON object");

  const issues: CampaignMapLayoutValidationIssue[] = [];
  if (!hasOnlyKeys(parsed, [
    "schemaVersion",
    "chapterId",
    "backgroundAssetKey",
    "imageWidthPx",
    "imageHeightPx",
    "anchors",
  ])) {
    issues.push(issue("error", "layout", "Layout contains unsupported fields"));
  }
  if (parsed.schemaVersion !== SCHEMA_VERSION) {
    issues.push(issue("error", "schemaVersion", "Unsupported campaign layout schema"));
  }
  if (parsed.chapterId !== chapter.id) {
    issues.push(issue("error", "chapterId", "Layout chapterId must match the selected chapter"));
  }
  if (typeof parsed.backgroundAssetKey !== "string" || !ASSET_KEY.test(parsed.backgroundAssetKey)) {
    issues.push(issue("error", "backgroundAssetKey", "A valid background asset key is required"));
  } else if (!CHAPTER_MAP_KEY.test(parsed.backgroundAssetKey)) {
    issues.push(issue("error", "backgroundAssetKey", "A chapter map asset key is required"));
  } else {
    const backgroundPath = await resolveCampaignAssetPath(repoRoot, parsed.backgroundAssetKey);
    if (!backgroundPath) {
      issues.push(issue("error", "backgroundAssetKey", "Campaign map background asset is missing or unsupported"));
    } else if (typeof parsed.imageWidthPx === "number" && typeof parsed.imageHeightPx === "number") {
      const dimensions = await readPngDimensions(backgroundPath);
      if (!dimensions || dimensions.width !== parsed.imageWidthPx || dimensions.height !== parsed.imageHeightPx) {
        issues.push(issue("error", "imageWidthPx", "Layout image dimensions must match its PNG background"));
      }
    }
  }
  if (typeof parsed.imageWidthPx !== "number" ||
    !Number.isInteger(parsed.imageWidthPx) ||
    parsed.imageWidthPx <= 0
  ) {
    issues.push(issue("error", "imageWidthPx", "Image width must be a positive integer"));
  }
  if (typeof parsed.imageHeightPx !== "number" ||
    !Number.isInteger(parsed.imageHeightPx) ||
    parsed.imageHeightPx <= 0
  ) {
    issues.push(issue("error", "imageHeightPx", "Image height must be a positive integer"));
  }

  const anchors = parsed.anchors;
  const expectedLevels = Array.from(
    { length: chapter.lastLevel - chapter.firstLevel + 1 },
    (_, index) => chapter.firstLevel + index,
  );
  if (!Array.isArray(anchors) || anchors.length !== expectedLevels.length) {
    issues.push(issue("error", "anchors", `Exactly ${expectedLevels.length} level anchors are required`));
  } else {
    anchors.forEach((value, index) => {
      const path = `anchors[${index}]`;
      if (!isRecord(value)) {
        issues.push(issue("error", path, "Anchor must be an object"));
        return;
      }
      if (!hasOnlyKeys(value, ["level", "x", "y"])) {
        issues.push(issue("error", path, "Anchor contains unsupported fields"));
      }
      if (value.level !== expectedLevels[index]) {
        issues.push(issue("error", `${path}.level`, `Expected campaign level ${expectedLevels[index]}`));
      }
      for (const axis of ["x", "y"] as const) {
        const coordinate = value[axis];
        if (typeof coordinate !== "number" || !Number.isFinite(coordinate) || coordinate < 0 || coordinate > 1) {
          issues.push(issue("error", `${path}.${axis}`, "Coordinate must be finite and between 0 and 1"));
        }
      }
    });
  }
  return { valid: !issues.some(candidate => candidate.severity === "error"), issues };
}

export async function saveCampaignMapLayout(
  repoRoot: string,
  chapterId: string,
  content: string,
  expectedHash: string,
): Promise<CampaignMapLayoutDocumentResponse> {
  const previous = lockByChapter.get(chapterId) ?? Promise.resolve();
  let release!: () => void;
  const current = new Promise<void>(resolve => { release = resolve; });
  lockByChapter.set(chapterId, current);
  await previous;
  try {
    const catalog = await readChapterCatalog(repoRoot);
    const chapter = catalog.chapters.find(candidate => candidate.id === chapterId);
    if (!chapter) throw new CampaignChapterNotFoundError(chapterId);

    const path = await layoutPath(repoRoot, chapterId);
    if (!path) throw new CampaignLayoutArtworkMissingError(chapterId);
    const validation = await validateCampaignMapLayout(repoRoot, chapter, content);
    if (!validation.valid) throw new CampaignLayoutValidationError(validation.issues);
    return await withCampaignLayoutFileLock(path, async () => {
      const previousContent = await readTextFile(path);
      if (previousContent === null) throw new CampaignLayoutArtworkMissingError(chapterId);
      const currentHash = hash(previousContent);
      if (currentHash !== expectedHash) throw new CampaignLayoutConflictError(currentHash);

      await replaceAtomically(path, content);
      return { chapterId, content, hash: hash(content) };
    });
  } finally {
    release();
    if (lockByChapter.get(chapterId) === current) lockByChapter.delete(chapterId);
  }
}

export async function resolveCampaignAssetPath(
  repoRoot: string,
  assetKey: string,
): Promise<string | null> {
  if (!ASSET_KEY.test(assetKey)) return null;
  const relativePath = FIXED_ASSETS[assetKey] ??
    (CHAPTER_MAP_KEY.test(assetKey)
      ? [...CAMPAIGN_MAP_DIRECTORY, `${assetKey}.png`]
      : null);
  if (!relativePath) return null;
  return safeExistingFile(repoRoot, relativePath);
}

export async function readChapterCatalog(
  repoRoot: string,
): Promise<{ schemaVersion: number; chapters: CampaignChapterCatalogEntry[] }> {
  const path = await safeExistingFile(repoRoot, CHAPTER_CATALOG_PATH);
  if (!path) throw new Error("Campaign chapter catalog is missing");
  const content = await readFile(path, "utf8");
  const parsed: unknown = JSON.parse(content);
  if (!isRecord(parsed) ||
    !hasOnlyKeys(parsed, ["schemaVersion", "chapters"]) ||
    parsed.schemaVersion !== SCHEMA_VERSION ||
    !Array.isArray(parsed.chapters)
  ) {
    throw new Error("Campaign chapter catalog is invalid");
  }
  const chapters: CampaignChapterCatalogEntry[] = [];
  let nextLevel = 1;
  const titles = new Set<string>();
  for (const [index, item] of parsed.chapters.entries()) {
    if (!isRecord(item) ||
      !hasOnlyKeys(item, ["id", "title", "firstLevel", "lastLevel", "biome", "bossPositions"]) ||
      typeof item.id !== "string" || !CHAPTER_ID.test(item.id) ||
      typeof item.title !== "string" || !item.title.trim() ||
      titles.has(item.title) ||
      typeof item.firstLevel !== "number" ||
      typeof item.lastLevel !== "number" ||
      !Number.isInteger(item.firstLevel) || !Number.isInteger(item.lastLevel) ||
      item.firstLevel !== nextLevel ||
      item.lastLevel !== item.firstLevel + 9 ||
      typeof item.biome !== "string" ||
      !Array.isArray(item.bossPositions) ||
      item.bossPositions.length !== 2 ||
      item.bossPositions[0] !== 5 ||
      item.bossPositions[1] !== 10
    ) {
      throw new Error(`Campaign chapter catalog entry ${index} is invalid`);
    }
    chapters.push({
      id: item.id,
      title: item.title,
      firstLevel: item.firstLevel,
      lastLevel: item.lastLevel,
      biome: item.biome,
      bossPositions: [5, 10],
      mapAvailable: false,
    });
    titles.add(item.title);
    nextLevel = item.lastLevel + 1;
  }
  if (chapters.length === 0 || new Set(chapters.map(chapter => chapter.id)).size !== chapters.length) {
    throw new Error("Campaign chapter catalog must contain unique chapters");
  }
  return { schemaVersion: SCHEMA_VERSION, chapters };
}

async function readLayoutFile(repoRoot: string, chapterId: string): Promise<string | null> {
  const path = await layoutPath(repoRoot, chapterId);
  return path ? readTextFile(path) : null;
}

async function chapterHasMapArtwork(repoRoot: string, chapterId: string): Promise<boolean> {
  const content = await readLayoutFile(repoRoot, chapterId);
  if (content === null) return false;
  try {
    const parsed: unknown = JSON.parse(content);
    if (!isRecord(parsed) || typeof parsed.backgroundAssetKey !== "string") return true;
    return (await resolveCampaignAssetPath(repoRoot, parsed.backgroundAssetKey)) !== null;
  } catch {
    return true;
  }
}

async function layoutPath(
  repoRoot: string,
  chapterId: string,
): Promise<string | null> {
  if (!CHAPTER_ID.test(chapterId)) return null;
  const root = await realpath(repoRoot);
  const directoryCandidate = join(root, ...LAYOUT_DIRECTORY);
  const directory = await realpath(directoryCandidate).catch(error => {
    if (isMissing(error)) return null;
    throw error;
  });
  if (!directory) return null;
  if (!isWithin(root, directory)) throw new Error("Campaign layout directory escapes repository root");
  const candidate = join(directory, `${chapterId}.json`);
  const actual = await realpath(candidate).catch(error => {
    if (isMissing(error)) return candidate;
    throw error;
  });
  if (!isWithin(directory, actual)) throw new Error("Campaign layout path escapes its allowlisted directory");
  return actual;
}

async function safeExistingFile(
  repoRoot: string,
  relativePath: readonly string[],
): Promise<string | null> {
  const root = await realpath(repoRoot);
  const directoryCandidate = join(root, ...relativePath.slice(0, -1));
  const directory = await realpath(directoryCandidate).catch(error => {
    if (isMissing(error)) return null;
    throw error;
  });
  if (!directory) return null;
  if (!isWithin(root, directory)) throw new Error("Campaign asset directory escapes repository root");
  const candidate = join(directory, relativePath.at(-1)!);
  const actual = await realpath(candidate).catch(error => {
    if (isMissing(error)) return null;
    throw error;
  });
  if (!actual) return null;
  if (!isWithin(directory, actual)) throw new Error("Campaign asset path escapes its allowlisted directory");
  const file = await stat(actual);
  return file.isFile() ? actual : null;
}

async function readTextFile(path: string): Promise<string | null> {
  try {
    return await readFile(path, "utf8");
  } catch (error) {
    if (isMissing(error)) return null;
    throw error;
  }
}

async function readPngDimensions(path: string): Promise<{ width: number; height: number } | null> {
  const data = await readFile(path);
  if (data.length < 24 ||
    data.readUInt32BE(0) !== 0x89504e47 ||
    data.readUInt32BE(4) !== 0x0d0a1a0a
  ) return null;
  return { width: data.readUInt32BE(16), height: data.readUInt32BE(20) };
}

async function replaceAtomically(path: string, content: string): Promise<void> {
  const temporary = `${path}.${randomUUID()}.tmp`;
  try {
    await writeFile(temporary, content, { encoding: "utf8", flag: "wx" });
    await rename(temporary, path);
  } finally {
    await rm(temporary, { force: true });
  }
}

async function withCampaignLayoutFileLock<T>(
  layoutPath: string,
  operation: () => Promise<T>,
): Promise<T> {
  const lockPath = `${layoutPath}.lock`;
  const deadline = Date.now() + FILE_LOCK_WAIT_MILLISECONDS;
  let lockHandle: Awaited<ReturnType<typeof open>> | null = null;

  while (lockHandle === null) {
    try {
      const candidate = await open(lockPath, "wx");
      try {
        await candidate.writeFile(`${process.pid}\n`, "utf8");
        await candidate.sync();
      } catch (error) {
        await candidate.close();
        await rm(lockPath, { force: true });
        throw error;
      }
      lockHandle = candidate;
    } catch (error) {
      if (!isErrorCode(error, "EEXIST")) throw error;
      await removeStaleCampaignLayoutFileLock(lockPath);
      if (Date.now() >= deadline) throw new CampaignLayoutLockTimeoutError();
      await delay(FILE_LOCK_RETRY_MILLISECONDS);
    }
  }

  try {
    return await operation();
  } finally {
    try {
      await lockHandle.close();
    } finally {
      await rm(lockPath, { force: true });
    }
  }
}

async function removeStaleCampaignLayoutFileLock(lockPath: string): Promise<void> {
  const lockStat = await stat(lockPath).catch(error => {
    if (isMissing(error)) return null;
    throw error;
  });
  if (!lockStat || Date.now() - lockStat.mtimeMs < FILE_LOCK_STALE_MILLISECONDS) return;

  const ownerPid = Number((await readTextFile(lockPath))?.trim());
  if (Number.isSafeInteger(ownerPid) && ownerPid > 0) {
    try {
      process.kill(ownerPid, 0);
      return;
    } catch (error) {
      if (isErrorCode(error, "EPERM")) return;
      if (!isErrorCode(error, "ESRCH")) throw error;
    }
  }
  await rm(lockPath, { force: true });
}

function hash(content: string): string {
  return createHash("sha256").update(content).digest("hex");
}

function issue(
  severity: CampaignMapLayoutValidationIssue["severity"],
  path: string,
  message: string,
): CampaignMapLayoutValidationIssue {
  return { severity, path, message };
}

function invalid(path: string, message: string): CampaignMapLayoutValidationResponse {
  return { valid: false, issues: [issue("error", path, message)] };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function hasOnlyKeys(value: Record<string, unknown>, allowed: readonly string[]): boolean {
  return Object.keys(value).every(key => allowed.includes(key));
}

function isWithin(root: string, path: string): boolean {
  const relativePath = relative(root, path);
  return relativePath === "" ||
    (!relativePath.startsWith(`..${sep}`) && relativePath !== ".." && !isAbsolute(relativePath));
}

function isMissing(error: unknown): boolean {
  return typeof error === "object" && error !== null && "code" in error && error.code === "ENOENT";
}

function isErrorCode(error: unknown, code: string): boolean {
  return typeof error === "object" && error !== null && "code" in error && error.code === code;
}

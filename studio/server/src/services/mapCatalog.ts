import { createHash, randomUUID } from "node:crypto";
import { copyFile, mkdir, readFile, readdir, realpath, rename, rm, writeFile } from "node:fs/promises";
import { isAbsolute, join, relative, sep } from "node:path";
import type { MapCatalogEntry, MapDocumentResponse } from "../contracts/maps.ts";

const MAP_DIRECTORIES = [
  ["game-campaign", "src", "main", "resources", "maps"],
  ["android-app", "src", "main", "assets", "maps"],
] as const;
const MAP_ID = /^[a-z0-9][a-z0-9-]*$/;
const locks = new Map<string, Promise<void>>();

export class MapConflictError extends Error {
  readonly currentHash: string | null;

  constructor(currentHash: string | null) {
    super("Map files changed since they were loaded");
    this.currentHash = currentHash;
  }
}

export function isMapId(value: string): boolean {
  return MAP_ID.test(value);
}

export async function listMaps(repoRoot: string): Promise<MapCatalogEntry[]> {
  const ids = new Set<string>();
  for (const directory of await mapDirectoryPaths(repoRoot, false)) {
    const names = await readDirectory(directory);
    for (const name of names) {
      if (name.endsWith(".json") && isMapId(name.slice(0, -5))) ids.add(name.slice(0, -5));
    }
  }
  return Promise.all([...ids].sort().map(async id => {
    const state = await readMap(repoRoot, id);
    return {
      id,
      biome: state ? readBiome(state.content) : null,
      synchronized: state?.synchronized ?? false,
    };
  }));
}

export async function readMap(repoRoot: string, id: string): Promise<MapDocumentResponse | null> {
  if (!isMapId(id)) return null;
  const paths = await mapFilePaths(repoRoot, id, false);
  const [campaign, android] = await Promise.all(paths.map(readOptional));
  if (campaign === null && android === null) return null;
  const content = campaign ?? android!;
  return {
    id,
    content,
    hash: hashPair(campaign, android),
    synchronized: campaign !== null && campaign === android,
  };
}

export async function saveMap(
  repoRoot: string,
  id: string,
  content: string,
  expectedHash: string | null,
): Promise<MapDocumentResponse> {
  if (!isMapId(id)) throw new Error("Map id must contain lowercase letters, numbers, and hyphens");
  const previous = locks.get(id) ?? Promise.resolve();
  let release!: () => void;
  const current = new Promise<void>(resolve => { release = resolve; });
  locks.set(id, current);
  await previous;
  try {
    const state = await readMap(repoRoot, id);
    if ((state?.hash ?? null) !== expectedHash) throw new MapConflictError(state?.hash ?? null);
    await replaceMapPair(repoRoot, id, content);
    const saved = await readMap(repoRoot, id);
    if (!saved) throw new Error("Saved map files could not be read");
    return saved;
  } finally {
    release();
    if (locks.get(id) === current) locks.delete(id);
  }
}

async function replaceMapPair(repoRoot: string, id: string, content: string): Promise<void> {
  const paths = await mapFilePaths(repoRoot, id, true);
  const token = randomUUID();
  const staged = paths.map(path => `${path}.${token}.tmp`);
  const backups = paths.map(path => `${path}.${token}.bak`);
  const originals = await Promise.all(paths.map(path => readOptional(path)));
  const replaced: number[] = [];

  try {
    await Promise.all(paths.map(async (path, index) => {
      await writeFile(staged[index], content, { encoding: "utf8", flag: "wx" });
      if (originals[index] !== null) await copyFile(path, backups[index]);
    }));
    for (let index = 0; index < paths.length; index += 1) {
      await rename(staged[index], paths[index]);
      replaced.push(index);
    }
  } catch (error) {
    for (const index of replaced.reverse()) {
      if (originals[index] === null) await rm(paths[index], { force: true });
      else await rename(backups[index], paths[index]);
    }
    throw error;
  } finally {
    await Promise.all([...staged, ...backups].map(path => rm(path, { force: true })));
  }
}

async function mapFilePaths(repoRoot: string, id: string, createDirectories: boolean): Promise<string[]> {
  const directories = await mapDirectoryPaths(repoRoot, createDirectories);
  return directories.map(directory => join(directory, `${id}.json`));
}

async function mapDirectoryPaths(repoRoot: string, createDirectories: boolean): Promise<string[]> {
  const root = await realpath(repoRoot);
  return Promise.all(MAP_DIRECTORIES.map(async relativeDirectory => {
    const path = join(root, ...relativeDirectory);
    if (createDirectories) await mkdir(path, { recursive: true });
    const actualDirectory = await realpath(path).catch(error => {
      if (isMissing(error)) return path;
      throw error;
    });
    if (!isWithin(root, actualDirectory)) throw new Error("Map path escapes the repository root");
    return actualDirectory;
  }));
}

function isWithin(root: string, path: string): boolean {
  const relativePath = relative(root, path);
  return relativePath === "" ||
    (!relativePath.startsWith(`..${sep}`) && relativePath !== ".." && !isAbsolute(relativePath));
}

async function readDirectory(path: string): Promise<string[]> {
  try {
    return await readdir(path);
  } catch (error) {
    if (isMissing(error)) return [];
    throw error;
  }
}

async function readOptional(path: string): Promise<string | null> {
  try {
    return await readFile(path, "utf8");
  } catch (error) {
    if (isMissing(error)) return null;
    throw error;
  }
}

function isMissing(error: unknown): boolean {
  return typeof error === "object" && error !== null && "code" in error && error.code === "ENOENT";
}

function hashPair(campaign: string | null, android: string | null): string {
  return createHash("sha256")
    .update(JSON.stringify([campaign, android]))
    .digest("hex");
}

function readBiome(content: string): string | null {
  try {
    const value: unknown = JSON.parse(content);
    if (typeof value !== "object" || value === null || !("biome" in value) || typeof value.biome !== "string") {
      return null;
    }
    return value.biome;
  } catch {
    return null;
  }
}

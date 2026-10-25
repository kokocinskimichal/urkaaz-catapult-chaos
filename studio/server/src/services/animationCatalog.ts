import { readdir, readFile, stat } from "node:fs/promises";
import { join } from "node:path";
import type { AnimationCatalogResponse, AnimationCategory, AnimationSequence } from "../contracts/animations.ts";

export const ANIMATION_DIRECTORY = ["android-app", "src", "main", "res", "drawable-nodpi"] as const;
export const ANIMATION_CONFIG_PATH = ["studio", "config", "animations.json"] as const;

const FRAME_NAME = /^(anim_(character|spell|ammunition)_[a-z0-9_]+)_(\d{3})\.png$/;
const TEAM_VARIANTS = new Set(["blue", "red"]);

export interface AnimationConfig {
  targets: Record<AnimationCategory, string[]>;
  teamTextureTargets: string[];
  withoutLoader: string[];
}

export const EMPTY_ANIMATION_CONFIG: AnimationConfig = {
  targets: { character: [], spell: [], ammunition: [] },
  teamTextureTargets: [],
  withoutLoader: [],
};

export function buildAnimationCatalog(fileNames: readonly string[], config: AnimationConfig): AnimationCatalogResponse {
  const framesByStem = new Map<string, { category: AnimationCategory; indices: number[] }>();
  const invalidFiles: string[] = [];
  for (const name of fileNames) {
    if (!name.startsWith("anim_") || !name.endsWith(".png")) continue;
    const match = FRAME_NAME.exec(name);
    if (!match) {
      invalidFiles.push(name);
      continue;
    }
    const entry = framesByStem.get(match[1]) ?? { category: match[2] as AnimationCategory, indices: [] };
    entry.indices.push(Number(match[3]));
    framesByStem.set(match[1], entry);
  }
  const withoutLoader = new Set(config.withoutLoader);
  const sequences = [...framesByStem.entries()]
    .map(([stem, { category, indices }]) => toSequence(stem, category, indices, config, withoutLoader))
    .sort((a, b) => a.id.localeCompare(b.id));
  return { sequences, invalidFiles: invalidFiles.sort() };
}

function toSequence(
  stem: string,
  category: AnimationCategory,
  indices: number[],
  config: AnimationConfig,
  withoutLoader: ReadonlySet<string>,
): AnimationSequence {
  const frameIndices = [...indices].sort((a, b) => a - b);
  const tokens = stem.slice(`anim_${category}_`.length).split("_");
  const variant = TEAM_VARIANTS.has(tokens[tokens.length - 1]) ? tokens.pop()! : null;
  const body = tokens.join("_");
  const { target, type, configured } = splitTarget(body, config.targets[category] ?? []);
  const contiguous = frameIndices.every((index, position) => index === position);
  return {
    id: stem,
    category,
    target,
    type,
    variant,
    frameIndices,
    targetConfigured: configured,
    hasRuntimeLoader: !withoutLoader.has(stem),
    supportsTeamTexture:
      category === "character" &&
      variant === null &&
      config.teamTextureTargets.includes(target),
    issues: contiguous ? [] : ["Frame indices are not contiguous from 000"],
  };
}

function splitTarget(body: string, knownTargets: readonly string[]): { target: string; type: string; configured: boolean } {
  const known = [...knownTargets]
    .sort((a, b) => b.length - a.length)
    .find(target => body.startsWith(`${target}_`));
  if (known) return { target: known, type: body.slice(known.length + 1), configured: true };
  const tokens = body.split("_");
  if (tokens.length < 2) return { target: body, type: "default", configured: false };
  return { target: tokens.slice(0, -1).join("_"), type: tokens[tokens.length - 1], configured: false };
}

export async function loadAnimationConfig(repoRoot: string): Promise<AnimationConfig> {
  let raw: string;
  try {
    raw = await readFile(join(repoRoot, ...ANIMATION_CONFIG_PATH), "utf8");
  } catch {
    return EMPTY_ANIMATION_CONFIG;
  }
  return parseAnimationConfig(JSON.parse(raw));
}

export function parseAnimationConfig(value: unknown): AnimationConfig {
  const source = (typeof value === "object" && value !== null ? value : {}) as Record<string, unknown>;
  const targets = (typeof source.targets === "object" && source.targets !== null ? source.targets : {}) as Record<string, unknown>;
  return {
    targets: {
      character: stringArray(targets.character),
      spell: stringArray(targets.spell),
      ammunition: stringArray(targets.ammunition),
    },
    teamTextureTargets: stringArray(source.teamTextureTargets),
    withoutLoader: stringArray(source.withoutLoader),
  };
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

export async function readAnimationCatalog(repoRoot: string): Promise<AnimationCatalogResponse> {
  const [fileNames, config] = await Promise.all([
    readdir(join(repoRoot, ...ANIMATION_DIRECTORY)),
    loadAnimationConfig(repoRoot),
  ]);
  return buildAnimationCatalog(fileNames, config);
}

export function animationFramePath(repoRoot: string, sequenceId: string, index: number): string | null {
  if (!/^anim_(character|spell|ammunition)_[a-z0-9_]+$/.test(sequenceId)) return null;
  if (!Number.isInteger(index) || index < 0 || index > 999) return null;
  return join(repoRoot, ...ANIMATION_DIRECTORY, `${sequenceId}_${String(index).padStart(3, "0")}.png`);
}

export function teamAnimationMaskPath(repoRoot: string, sequenceId: string, index: number): string | null {
  const match = /^anim_character_([a-z0-9_]+)_(idle|move|attack|death)$/.exec(sequenceId);
  if (!match || !Number.isInteger(index) || index < 0 || index > 999) return null;
  return join(
    repoRoot,
    "android-app",
    "src",
    "main",
    "assets",
    "animations",
    match[1],
    "masks",
    match[2],
    `${String(index).padStart(3, "0")}.png`,
  );
}

export async function teamTexturePath(
  repoRoot: string,
  target: string,
  team: string,
): Promise<string | null> {
  if (!/^[a-z0-9_]+$/.test(target) || (team !== "blue" && team !== "red")) return null;
  const configPath = join(
    repoRoot,
    "android-app",
    "src",
    "main",
    "assets",
    "animations",
    target,
    "palettes.json",
  );
  if (!(await stat(configPath).catch(() => null))?.isFile()) return null;
  const config = JSON.parse(await readFile(configPath, "utf8")) as {
    teams?: Record<string, unknown>;
  };
  const texture = config.teams?.[team.toUpperCase()];
  if (
    typeof texture !== "string" ||
    !/^animations\/[a-z0-9_-]+\/[a-z0-9_-]+\.png$/.test(texture)
  ) {
    return null;
  }
  return join(repoRoot, "android-app", "src", "main", "assets", texture);
}

import type { MapBiome } from "./map";

export type TerrainAssetCategory = "tree" | "rock" | "prop" | "relic" | "detail" | "cap" | "tile" | "soil";
export type EditorPaletteCategory = "tree" | "rock" | "prop" | "relic";
export interface EditorPaletteAsset {
  assetKey: string;
  category: EditorPaletteCategory;
  label: string;
  thumbnailPath: string;
  relicSize?: "SMALL";
}

function assetBiomeForMapBiome(biome: string): string {
  if (biome === "frostbound") return "frost";
  if (biome === "sunken_marshes") return "green_frontier";
  return biome;
}

const THEME_ASSETS: Record<MapBiome, { assetBiome: string; backgroundBiome: string; background: string }> = {
  green_frontier: {
    assetBiome: "green_frontier",
    backgroundBiome: "green_frontier",
    background: "background_green_frontier",
  },
  frostbound: {
    assetBiome: "frostbound",
    backgroundBiome: "frostbound",
    background: "background_frostbound_pass",
  },
  copperwood: {
    assetBiome: "copperwood",
    backgroundBiome: "copperwood",
    background: "background_copperwood",
  },
  ashen_march: {
    assetBiome: "ashen_march",
    backgroundBiome: "ashen_march",
    background: "background_ashen_march",
  },
  sunken_marshes: {
    assetBiome: "green_frontier",
    backgroundBiome: "sunken_marshes",
    background: "background_sunken_marshes",
  },
};

export function terrainThemeAssetPaths(biome: MapBiome) {
  const theme = THEME_ASSETS[biome];
  return {
    background: `/assets/terrain/${theme.backgroundBiome}/${theme.background}.png`,
    soil: `/assets/terrain/${theme.assetBiome}/terrain_${theme.assetBiome}_soil_00.png`,
    cap: `/assets/terrain/${theme.assetBiome}/terrain_${theme.assetBiome}_cap_00.png`,
  };
}

export function paletteAssetsForBiome(
  assetKeys: ReadonlySet<string>,
  biome: MapBiome,
): Record<EditorPaletteCategory, EditorPaletteAsset[]> {
  const assetBiome = assetBiomeForMapBiome(biome);
  const palette: Record<EditorPaletteCategory, EditorPaletteAsset[]> = {
    tree: [],
    rock: [],
    prop: [],
    relic: [],
  };

  for (const category of ["tree", "rock", "prop"] as const) {
    const prefix = `terrain_${assetBiome}_${category}_`;
    palette[category] = [...assetKeys]
      .filter(key => key.startsWith(prefix))
      .sort()
      .map(assetKey => ({
        assetKey,
        category,
        label: `${category} ${assetKey.slice(prefix.length).replaceAll("_", " ")}`,
        thumbnailPath: `/assets/terrain/${assetBiome}/${assetKey}.png`,
      }));
  }

  const relicCategory = biome === "copperwood" ? "detail" : "relic";
  const relicPrefix = `terrain_${assetBiome}_${relicCategory}_`;
  palette.relic = [...assetKeys]
    .filter(key => key.startsWith(relicPrefix))
    .sort()
    .flatMap(assetFileKey => {
      const match = /_(\d{2})_(.+)$/.exec(assetFileKey);
      if (!match) return [];
      const index = Number(match[1]);
      if (index < 1 || index > 10) return [];
      return [{
        assetKey: `relic.${biome}.small.${index}`,
        category: "relic" as const,
        label: match[2].replaceAll("_", " "),
        thumbnailPath: `/assets/terrain/${assetBiome}/${assetFileKey}.png`,
        relicSize: "SMALL" as const,
      }];
    });

  return palette;
}

export function parseAssetIndex(contents: string): ReadonlySet<string> {
  const keys = new Set<string>();
  for (const line of contents.split(/\r?\n/)) {
    const fileName = line.trim();
    if (!fileName) continue;
    if (!/^terrain_([^_]+(?:_[^_]+)*)_(tree|rock|prop|relic|detail|cap|tile|soil)_([^.]+)\.png$/.test(fileName)) {
      throw new Error(`Unsupported terrain asset index entry: ${fileName}`);
    }
    keys.add(fileName.slice(0, -4));
  }
  return keys;
}

export function relicAssetPath(
  biome: string,
  assetKey: string,
  assetKeys: ReadonlySet<string>,
): string | undefined {
  const relic = /^relic\.([a-z_]+)\.small\.(\d+)$/.exec(assetKey);
  if (!relic || relic[1] !== biome) return undefined;
  const index = Number(relic[2]);
  if (index < 1 || index > 10) return undefined;
  const assetBiome = assetBiomeForMapBiome(biome);
  const category = biome === "copperwood" ? "detail" : "relic";
  const prefix = `terrain_${assetBiome}_${category}_${String(index).padStart(2, "0")}_`;
  const fileKey = [...assetKeys].find(key => key.startsWith(prefix));
  return fileKey ? `/assets/terrain/${assetBiome}/${fileKey}.png` : undefined;
}

export function firstAssetKeyForBiome(
  assetKeys: ReadonlySet<string>,
  biome: string,
  category: TerrainAssetCategory,
): string | undefined {
  const prefix = `terrain_${assetBiomeForMapBiome(biome)}_${category}_`;
  return [...assetKeys].filter(key => key.startsWith(prefix)).sort()[0];
}

export function isKnownMapDecoration(
  assetKey: string,
  biome: string,
  assetKeys: ReadonlySet<string>,
): boolean {
  const assetBiome = assetBiomeForMapBiome(biome);
  const relic = /^relic\.([a-z_]+)\.small\.(\d+)$/.exec(assetKey);
  if (relic?.[1] === biome && Number(relic[2]) >= 1 && Number(relic[2]) <= 10) {
    const category = biome === "copperwood" ? "detail" : "relic";
    const prefix = `terrain_${assetBiome}_${category}_${String(Number(relic[2])).padStart(2, "0")}_`;
    return [...assetKeys].some(key => key.startsWith(prefix));
  }
  return /^terrain_.*_(tree|rock|prop)_[^.]+$/.test(assetKey) &&
    assetKey.startsWith(`terrain_${assetBiome}_`) &&
    assetKeys.has(assetKey);
}

export function terrainAssetPath(biome: string, assetKey: string): string {
  const assetBiome = assetBiomeForMapBiome(biome);
  if (assetKey.startsWith(`terrain_${assetBiome}_`)) {
    return `/assets/terrain/${assetBiome}/${assetKey}.png`;
  }
  const category = assetKey.includes("rock") ? "rock" : "tree";
  return `/assets/terrain/${assetBiome}/terrain_${assetBiome}_${category}_00.png`;
}

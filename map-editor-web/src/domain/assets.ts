export type TerrainAssetCategory = "tree" | "rock" | "prop" | "relic" | "cap" | "tile" | "soil";

function assetBiomeForMapBiome(biome: string): string {
  return biome === "frostbound" ? "frost" : biome;
}

export function parseAssetIndex(contents: string): ReadonlySet<string> {
  const keys = new Set<string>();
  for (const line of contents.split(/\r?\n/)) {
    const fileName = line.trim();
    if (!fileName) continue;
    if (!/^terrain_([^_]+(?:_[^_]+)*)_(tree|rock|prop|relic|cap|tile|soil)_([^.]+)\.png$/.test(fileName)) {
      throw new Error(`Unsupported terrain asset index entry: ${fileName}`);
    }
    keys.add(fileName.slice(0, -4));
  }
  return keys;
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
  if (assetKey.startsWith(`terrain_${assetBiome}_`) && assetKeys.has(assetKey)) return true;
  const relic = /^relic\.([a-z_]+)\.small\.(\d+)$/.exec(assetKey);
  return relic?.[1] === biome && Number(relic[2]) >= 1 && Number(relic[2]) <= 10;
}

export function terrainAssetPath(biome: string, assetKey: string): string {
  const assetBiome = assetBiomeForMapBiome(biome);
  if (assetKey.startsWith(`terrain_${assetBiome}_`)) {
    return `/assets/terrain/${assetBiome}/${assetKey}.png`;
  }
  const category = assetKey.includes("rock") ? "rock" : "tree";
  return `/assets/terrain/${assetBiome}/terrain_${assetBiome}_${category}_00.png`;
}

import { cp, mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { join, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../../", import.meta.url));
const source = join(root, "game-assets", "src", "main", "resources", "terrain");
const destination = join(fileURLToPath(new URL("../", import.meta.url)), "public", "assets", "terrain");
const androidDrawableSource = join(root, "android-app", "src", "main", "res", "drawable-nodpi");

async function copyTree(from, to) {
  await mkdir(to, { recursive: true });
  for (const entry of await readdir(from, { withFileTypes: true })) {
    const sourcePath = join(from, entry.name);
    const destinationPath = join(to, entry.name);
    if (entry.isDirectory()) await copyTree(sourcePath, destinationPath);
    else if (entry.name.endsWith(".png")) await cp(sourcePath, destinationPath);
  }
}

await copyTree(source, destination);
const assetNames = new Set((await readFile(join(source, "asset-index.txt"), "utf8"))
  .split(/\r?\n/)
  .map(name => name.trim())
  .filter(Boolean));
const relicAssetPattern = /^terrain_(green_frontier|frost|copperwood|ashen_march)_(relic|detail)_(\d{2})_[^.]+\.png$/;
const sharedMediumRelicPattern = /^relic_medium_[a-z0-9_]+\.png$/;
for (const fileName of await readdir(androidDrawableSource)) {
  const match = relicAssetPattern.exec(fileName);
  const isSharedMediumRelic = sharedMediumRelicPattern.test(fileName);
  if (!match && !isSharedMediumRelic) continue;
  const assetDirectory = join(destination, isSharedMediumRelic ? "shared" : match[1]);
  await mkdir(assetDirectory, { recursive: true });
  await cp(join(androidDrawableSource, fileName), join(assetDirectory, fileName));
  assetNames.add(fileName);
}
await writeFile(join(destination, "asset-index.txt"), `${[...assetNames].sort().join("\n")}\n`);
const sunkenBackgroundDirectory = join(destination, "sunken_marshes");
await mkdir(sunkenBackgroundDirectory, { recursive: true });
await cp(
  join(root, "android-app", "src", "main", "res", "drawable-nodpi", "background_sunken_marshes.png"),
  join(sunkenBackgroundDirectory, "background_sunken_marshes.png"),
);
console.log(`Synced web assets from ${relative(root, source).split(sep).join("/")} to ${relative(root, destination).split(sep).join("/")}`);

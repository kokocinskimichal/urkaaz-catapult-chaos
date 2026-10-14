import { cp, mkdir, readdir } from "node:fs/promises";
import { join, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../../", import.meta.url));
const source = join(root, "game-assets", "src", "main", "resources", "terrain");
const destination = join(fileURLToPath(new URL("../", import.meta.url)), "public", "assets", "terrain");

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
console.log(`Synced web assets from ${relative(root, source).split(sep).join("/")} to ${relative(root, destination).split(sep).join("/")}`);

import { createStudioServer } from "./app.ts";
import { resolveRepoRoot } from "./services/repoRoot.ts";

const HOST = "127.0.0.1";
const port = Number(process.env.STUDIO_PORT ?? 4310);

const repoRoot = await resolveRepoRoot();
const server = createStudioServer({ repoRoot });
server.listen(port, HOST, () => {
  console.log(`Urkaaaz Studio server: http://${HOST}:${port} (repo: ${repoRoot})`);
});

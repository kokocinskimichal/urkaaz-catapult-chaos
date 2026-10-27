import { describe, expect, it } from "vitest";
import { ApiError, fetchHealth, fetchMap, fetchTerrainAssetIndex, requestJson, saveMap, validateMap } from "./api";

const json = (status: number, body: unknown) =>
  Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } }));

describe("api client", () => {
  it("returns the parsed body on success", async () => {
    const health = { version: "0.1.0", repoRoot: "/repo", branch: "develop", clean: true, changedFiles: 0 };
    expect(await fetchHealth(() => json(200, health))).toEqual(health);
  });

  it("raises ApiError with the structured body", async () => {
    const error = await requestJson("/api/x", () => json(409, { code: "conflict", message: "Changed", details: ["hash"] })).catch(e => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 409, code: "conflict", message: "Changed", details: ["hash"] });
  });

  it("falls back to an unknown error for non-contract bodies", async () => {
    const error = await requestJson("/api/x", () => Promise.resolve(new Response("oops", { status: 500, statusText: "Boom" }))).catch(e => e);
    expect(error).toMatchObject({ status: 500, code: "unknown", message: "Boom" });
  });

  it("uses the map API routes and preserves hash preconditions", async () => {
    const calls: Array<{ path: string; init?: RequestInit }> = [];
    const fetcher = (path: string, init?: RequestInit) => {
      calls.push({ path, init });
      return json(200, { id: "demo-map", content: "{}", hash: "hash", synchronized: true });
    };

    await fetchMap("demo-map", fetcher);
    await saveMap("demo-map", { content: "{}", expectedHash: "hash" }, fetcher);
    await validateMap("{}", fetcher);

    expect(calls.map(call => call.path)).toEqual([
      "/api/maps/demo-map",
      "/api/maps/demo-map",
      "/api/maps/validate",
    ]);
    expect(JSON.parse(String(calls[1].init?.body))).toEqual({ content: "{}", expectedHash: "hash" });
    expect(JSON.parse(String(calls[2].init?.body))).toEqual({ content: "{}" });
  });

  it("reads the terrain asset index as text", async () => {
    await expect(fetchTerrainAssetIndex(() => Promise.resolve(new Response("tree.png\n"))))
      .resolves.toBe("tree.png\n");
  });
});

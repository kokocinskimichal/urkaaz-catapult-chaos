import { describe, expect, it } from "vitest";
import {
  ApiError,
  fetchBalance,
  fetchCampaignAsset,
  fetchCampaignChapters,
  fetchCampaignMapLayout,
  fetchHealth,
  fetchMap,
  fetchProgress,
  fetchTerrainAssetIndex,
  requestJson,
  saveCampaignMapLayout,
  saveMap,
  saveBalance,
  saveProgress,
  validateCampaignMapLayout,
  validateMap,
  validateBalance,
  validateProgress,
} from "./api";

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

  it("uses the balance API routes with validation and hash preconditions", async () => {
    const calls: Array<{ path: string; init?: RequestInit }> = [];
    const fetcher = (path: string, init?: RequestInit) => {
      calls.push({ path, init });
      return json(200, {
        content: "{}",
        hash: "balance-hash",
        valid: true,
        issues: [],
      });

      it("uses progress API routes with validation and hash preconditions", async () => {
        const calls: Array<{ path: string; init?: RequestInit }> = [];
        const fetcher = (path: string, init?: RequestInit) => {
          calls.push({ path, init });
          return json(200, {
            content: "{}",
            hash: "progress-hash",
            valid: true,
            issues: [],
          });
        };

        await fetchProgress(fetcher);
        await validateProgress("{}", fetcher);
        await saveProgress({ content: "{}", expectedHash: "progress-hash" }, fetcher);

        expect(calls.map(call => call.path)).toEqual([
          "/api/progress",
          "/api/progress/validate",
          "/api/progress",
        ]);
        expect(JSON.parse(String(calls[1].init?.body))).toEqual({ content: "{}" });
        expect(JSON.parse(String(calls[2].init?.body))).toEqual({
          content: "{}",
          expectedHash: "progress-hash",
        });
      });
    };

    await fetchBalance(fetcher);
    await validateBalance("{}", fetcher);
    await saveBalance({ content: "{}", expectedHash: "balance-hash" }, fetcher);

    expect(calls.map(call => call.path)).toEqual([
      "/api/balance",
      "/api/balance/validate",
      "/api/balance",
    ]);
    expect(JSON.parse(String(calls[1].init?.body))).toEqual({ content: "{}" });
    expect(JSON.parse(String(calls[2].init?.body))).toEqual({
      content: "{}",
      expectedHash: "balance-hash",
    });
  });

  it("reads the terrain asset index as text", async () => {
    await expect(fetchTerrainAssetIndex(() => Promise.resolve(new Response("tree.png\n"))))
      .resolves.toBe("tree.png\n");
  });

  it("uses the campaign layout routes and preserves chapter hash preconditions", async () => {
    const calls: Array<{ path: string; init?: RequestInit }> = [];
    const response = {
      chapterId: "green_frontier",
      content: "{}",
      hash: "campaign-hash",
    };
    const fetcher = (path: string, init?: RequestInit) => {
      calls.push({ path, init });
      return json(200, response);
    };

    await fetchCampaignChapters(fetcher);
    await fetchCampaignMapLayout("green_frontier", fetcher);
    await saveCampaignMapLayout(
      "green_frontier",
      { content: "{}", expectedHash: "campaign-hash" },
      fetcher,
    );
    await validateCampaignMapLayout("green_frontier", "{}", fetcher);

    expect(calls.map(call => call.path)).toEqual([
      "/api/campaign/chapters",
      "/api/campaign/layouts/green_frontier",
      "/api/campaign/layouts/green_frontier",
      "/api/campaign/layouts/validate",
    ]);
    expect(JSON.parse(String(calls[2].init?.body))).toEqual({
      content: "{}",
      expectedHash: "campaign-hash",
    });
    expect(JSON.parse(String(calls[3].init?.body))).toEqual({
      chapterId: "green_frontier",
      content: "{}",
    });
  });

  it("reads campaign assets as blobs", async () => {
    const expected = new Blob(["png"]);
    await expect(
      fetchCampaignAsset("campaign_map_frame_overlay", () => Promise.resolve(new Response(expected))),
    ).resolves.toBeInstanceOf(Blob);
  });
});

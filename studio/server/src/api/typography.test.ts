import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { request } from "node:http";
import type { AddressInfo } from "node:net";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  BUTTON_TYPOGRAPHY_CONTROLS,
  type ButtonTypographyUpdate,
} from "../contracts/typography.ts";
import { createStudioServer } from "../app.ts";

let repoRoot: string;
let server: ReturnType<typeof createStudioServer>;
let port: number;

const LABELS = {
  en: "TEST MODE",
  pl: "TRYB TESTOWY",
  es: "MODO DE PRUEBA",
} as const;

beforeEach(async () => {
  repoRoot = await mkdtemp(join(tmpdir(), "studio-typography-api-"));
  for (const [locale, directory] of [
    ["en", "values"],
    ["pl", "values-pl"],
    ["es", "values-es"],
  ] as const) {
    const resourceDirectory = join(
      repoRoot,
      "android-app",
      "src",
      "main",
      "res",
      directory,
    );
    await mkdir(resourceDirectory, { recursive: true });
    const keys = [...new Set(BUTTON_TYPOGRAPHY_CONTROLS.map(control => control.stringKey))];
    const strings = keys.map(stringKey => `<string name="${stringKey}">${
      stringKey === "test_mode" ? LABELS[locale] : `${locale.toUpperCase()} ${stringKey}`
    }</string>`);
    const dimensions = BUTTON_TYPOGRAPHY_CONTROLS.map(control =>
      `<dimen name="button_text_size_${control.controlId}">${
        locale === "en" ? 14 : locale === "pl" ? 11 : 10
      }sp</dimen>`,
    );
    await writeFile(
      join(resourceDirectory, "strings.xml"),
      `<resources>${strings.join("")}</resources>\n`,
      "utf8",
    );
    await writeFile(
      join(resourceDirectory, "button_typography.xml"),
      `<resources>${dimensions.join("")}</resources>\n`,
      "utf8",
    );
  }
  server = createStudioServer({
    repoRoot,
    readGitState: async () => ({ branch: "develop", changedFiles: 0 }),
  });
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  port = (server.address() as AddressInfo).port;
});

afterEach(async () => {
  await new Promise<void>(resolve => server.close(() => resolve()));
  await rm(repoRoot, { recursive: true, force: true });
});

describe("button typography API", () => {
  it("reads categorized previews, validates changes, and saves all locale resources", async () => {
    const loaded = await call("/api/typography");
    const document = loaded.body as {
      hash: string;
      entries: Array<{
        controlId: string;
        category: string;
        stringKey: string;
        labels: Record<string, string>;
        fontSizeSp: Record<string, number>;
      }>;
    };
    expect(loaded.status).toBe(200);
    expect(document.entries).toHaveLength(BUTTON_TYPOGRAPHY_CONTROLS.length);
    expect(document.entries.find(entry => entry.controlId === "dashboard_test_mode")).toMatchObject({
      category: "dashboard",
      stringKey: "test_mode",
      labels: LABELS,
      fontSizeSp: { en: 14, pl: 11, es: 10 },
    });

    const entries: ButtonTypographyUpdate[] = document.entries.map(entry => ({
      controlId: entry.controlId,
      fontSizeSp: entry.controlId === "dashboard_test_mode"
        ? { en: 14, pl: 10.5, es: 9.5 }
        : { en: 14, pl: 11, es: 10 },
    }));
    await expect(call("/api/typography/validate", {
      method: "POST",
      body: { entries },
    })).resolves.toMatchObject({
      status: 200,
      body: { valid: true, issues: [] },
    });

    const saved = await call("/api/typography", {
      method: "PUT",
      body: { entries, expectedHash: document.hash },
    });
    expect(saved.status).toBe(200);
    expect((saved.body as { entries: unknown[] }).entries.find(
      entry => (entry as { controlId: string }).controlId === "dashboard_test_mode",
    )).toMatchObject({
      controlId: "dashboard_test_mode",
      category: "dashboard",
      fontSizeSp: { en: 14, pl: 10.5, es: 9.5 },
    });
    await expect(readFile(
      join(repoRoot, "android-app/src/main/res/values-es/button_typography.xml"),
      "utf8",
    )).resolves.toContain('name="button_text_size_dashboard_test_mode">9.5sp');
  });

  it("rejects stale hashes and malformed values without changing resources", async () => {
    const loaded = await call("/api/typography");
    const document = loaded.body as {
      hash: string;
      entries: Array<{ controlId: string }>;
    };
    const entries = document.entries.map(entry => ({
      controlId: entry.controlId,
      fontSizeSp: entry.controlId === "dashboard_test_mode"
        ? { en: 14, pl: 7, es: 10 }
        : { en: 14, pl: 11, es: 10 },
    }));
    const invalid = await call("/api/typography", {
      method: "PUT",
      body: { entries, expectedHash: document.hash },
    });
    expect(invalid.status).toBe(422);

    const staleEntries = document.entries.map(entry => ({
      controlId: entry.controlId,
      fontSizeSp: { en: 14, pl: 11, es: 10 },
    }));
    const stale = await call("/api/typography", {
      method: "PUT",
      body: { entries: staleEntries, expectedHash: "stale" },
    });
    expect(stale.status).toBe(409);
    await expect(readFile(
      join(repoRoot, "android-app/src/main/res/values-pl/button_typography.xml"),
      "utf8",
    )).resolves.toContain('name="button_text_size_dashboard_test_mode">11sp');
  });
});

function call(
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<{ status: number; body: unknown }> {
  return new Promise((resolve, reject) => {
    const body = options.body === undefined ? undefined : JSON.stringify(options.body);
    const req = request({
      host: "127.0.0.1",
      port,
      path,
      method: options.method ?? "GET",
      headers: body === undefined ? {} : {
        "content-type": "application/json",
        "content-length": Buffer.byteLength(body),
      },
    }, response => {
      let data = "";
      response.setEncoding("utf8");
      response.on("data", chunk => { data += chunk; });
      response.on("end", () => {
        let parsed: unknown;
        try {
          parsed = JSON.parse(data);
        } catch {
          parsed = data;
        }
        resolve({ status: response.statusCode ?? 0, body: parsed });
      });
    });
    req.on("error", reject);
    if (body !== undefined) req.write(body);
    req.end();
  });
}

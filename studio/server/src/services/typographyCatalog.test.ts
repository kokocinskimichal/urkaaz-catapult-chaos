import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  BUTTON_TYPOGRAPHY_CONTROLS,
  type ButtonTypographyUpdate,
  type TypographyLanguage,
} from "../contracts/typography.ts";
import { resolveRepoRoot } from "./repoRoot.ts";
import {
  readButtonTypography,
  saveButtonTypography,
  TypographyConflictError,
  TypographyResourceError,
  validateButtonTypography,
} from "./typographyCatalog.ts";

let repoRoot: string;

const LABELS: Record<TypographyLanguage, string> = {
  en: "TEST MODE",
  pl: "TRYB TESTOWY",
  es: "MODO DE PRUEBA",
};

const INITIAL_SIZES = { en: 14, pl: 11, es: 10 };

beforeEach(async () => {
  repoRoot = await mkdtemp(join(tmpdir(), "studio-typography-"));
  await writeResourceSet(repoRoot, INITIAL_SIZES);
});

afterEach(async () => {
  await rm(repoRoot, { recursive: true, force: true });
});

describe("button typography catalog", () => {
  it("reads every categorized control with its localized label and size", async () => {
    const result = await readButtonTypography(repoRoot);

    expect(result.hash).toHaveLength(64);
    expect(result.entries).toHaveLength(BUTTON_TYPOGRAPHY_CONTROLS.length);
    expect(result.entries[0]).toMatchObject({
      controlId: "common_loadout_back",
      category: "common",
      stringKey: "loadout_back",
      fontSizeSp: INITIAL_SIZES,
    });
    expect(result.entries.find(entry => entry.controlId === "dashboard_test_mode"))
      .toMatchObject({
        stringKey: "test_mode",
        labels: LABELS,
        fontSizeSp: INITIAL_SIZES,
      });
  });

  it("validates and saves independent locale sizes atomically", async () => {
    const loaded = await readButtonTypography(repoRoot);
    const updates = updatesForSizes();
    const edited = updates.map(entry => entry.controlId === "dashboard_test_mode"
      ? { ...entry, fontSizeSp: { en: 14, pl: 10.5, es: 9.5 } }
      : entry);
    expect(await validateButtonTypography(repoRoot, edited)).toEqual({
      valid: true,
      issues: [],
    });

    const saved = await saveButtonTypography(repoRoot, {
      entries: edited,
      expectedHash: loaded.hash,
    });

    expect(saved.entries.find(entry => entry.controlId === "dashboard_test_mode")?.fontSizeSp)
      .toEqual({ en: 14, pl: 10.5, es: 9.5 });
    expect(await readFile(dimensionPath("en"), "utf8"))
      .toContain('name="button_text_size_dashboard_test_mode">14sp');
    expect(await readFile(dimensionPath("pl"), "utf8"))
      .toContain('name="button_text_size_dashboard_test_mode">10.5sp');
    expect(await readFile(dimensionPath("es"), "utf8"))
      .toContain('name="button_text_size_dashboard_test_mode">9.5sp');
  });

  it("rejects missing controls, unknown fields, and invalid font sizes", async () => {
    const baseline = updatesForSizes();
    const first = baseline[0]!;
    const invalidCases = [
      [],
      baseline.slice(1),
      baseline.map(entry => entry.controlId === first.controlId
        ? { ...entry, stringKey: "unexpected" }
        : entry),
      baseline.map(entry => entry.controlId === "dashboard_test_mode"
        ? { ...entry, fontSizeSp: { en: 14, pl: 7, es: 10 } }
        : entry),
      baseline.map(entry => entry.controlId === "dashboard_test_mode"
        ? { ...entry, fontSizeSp: { en: 14, pl: 7.5, es: 10 } }
        : entry),
      baseline.map(entry => entry.controlId === "dashboard_test_mode"
        ? { ...entry, fontSizeSp: { en: 14, pl: 11, es: "10" } }
        : entry),
    ];

    for (const invalid of invalidCases) {
      expect((await validateButtonTypography(repoRoot, invalid)).valid).toBe(false);
    }
  });

  it("rejects stale updates without modifying any locale file", async () => {
    const stale = await readButtonTypography(repoRoot);
    const edited = updatesForSizes().map(entry => entry.controlId === "dashboard_test_mode"
      ? { ...entry, fontSizeSp: { en: 12, pl: 12, es: 12 } }
      : entry);
    await saveButtonTypography(repoRoot, {
      entries: edited,
      expectedHash: stale.hash,
    });

    await expect(saveButtonTypography(repoRoot, {
      entries: edited,
      expectedHash: stale.hash,
    })).rejects.toBeInstanceOf(TypographyConflictError);
  });

  it("rejects mismatched locale controls and missing translations", async () => {
    await writeFile(dimensionPath("es"), `
      <resources>
        <dimen name="button_text_size_other">10sp</dimen>
      </resources>
    `);
    await expect(readButtonTypography(repoRoot)).rejects.toBeInstanceOf(TypographyResourceError);

    await writeResourceSet(repoRoot, INITIAL_SIZES, "es:loadout_back");
    await expect(readButtonTypography(repoRoot)).rejects.toBeInstanceOf(TypographyResourceError);
  });

  it("accepts every checked-in categorized Android typography resource", async () => {
    const result = await readButtonTypography(await resolveRepoRoot());
    expect(result.entries.map(entry => entry.controlId))
      .toEqual(BUTTON_TYPOGRAPHY_CONTROLS.map(control => control.controlId));
  });
});

function updatesForSizes(): ButtonTypographyUpdate[] {
  return BUTTON_TYPOGRAPHY_CONTROLS.map(control => ({
    controlId: control.controlId,
    fontSizeSp: INITIAL_SIZES,
  }));
}

async function writeResourceSet(
  root: string,
  sizes: Record<TypographyLanguage, number>,
  missingTranslation?: `${TypographyLanguage}:${string}`,
): Promise<void> {
  for (const language of ["en", "pl", "es"] as const) {
    const directory = resourceDirectory(root, language);
    await mkdir(directory, { recursive: true });
    const strings = [...new Set(BUTTON_TYPOGRAPHY_CONTROLS.map(control => control.stringKey))]
      .filter(stringKey => missingTranslation !== `${language}:${stringKey}`)
      .map(stringKey => {
        const label = stringKey === "test_mode"
          ? LABELS[language]
          : `${language.toUpperCase()} ${stringKey}`;
        return `<string name="${stringKey}">${label}</string>`;
      });
    const dimensions = BUTTON_TYPOGRAPHY_CONTROLS.map(control =>
      `<dimen name="button_text_size_${control.controlId}">${sizes[language]}sp</dimen>`,
    );
    await writeFile(
      join(directory, "strings.xml"),
      `<resources>${strings.join("")}</resources>\n`,
      "utf8",
    );
    await writeFile(
      dimensionPathFor(root, language),
      `<resources>${dimensions.join("")}</resources>\n`,
      "utf8",
    );
  }
}

function dimensionPath(language: TypographyLanguage): string {
  return dimensionPathFor(repoRoot, language);
}

function dimensionPathFor(root: string, language: TypographyLanguage): string {
  return join(resourceDirectory(root, language), "button_typography.xml");
}

function resourceDirectory(root: string, language: TypographyLanguage): string {
  return join(
    root,
    "android-app",
    "src",
    "main",
    "res",
    language === "en" ? "values" : `values-${language}`,
  );
}

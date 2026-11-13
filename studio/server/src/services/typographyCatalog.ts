import { createHash, randomUUID } from "node:crypto";
import { copyFile, mkdir, readFile, realpath, rename, rm, writeFile } from "node:fs/promises";
import { isAbsolute, join, relative, sep } from "node:path";
import type {
  ButtonTypographyEntry,
  ButtonTypographyUpdate,
  TypographyDocumentResponse,
  TypographyLanguage,
  TypographySaveRequest,
  TypographyValidationIssue,
  TypographyValidationResponse,
} from "../contracts/typography.ts";
import { BUTTON_TYPOGRAPHY_CONTROLS } from "../contracts/typography.ts";
import {
  AndroidResourcesXmlError,
  readAndroidDimensionResources,
  readAndroidStringResources,
} from "./androidResourcesXml.ts";

const LOCALES: readonly TypographyLanguage[] = ["en", "pl", "es"];
export const TYPOGRAPHY_MAX_BODY_BYTES = 64 * 1024;
const MINIMUM_FONT_SIZE_SP = 8;
const MAXIMUM_FONT_SIZE_SP = 32;
const BUTTON_DIMEN_PREFIX = "button_text_size_";
const RESOURCE_DIRECTORY = ["android-app", "src", "main", "res"] as const;
const STRING_FILE = "strings.xml";
const TYPOGRAPHY_FILE = "button_typography.xml";
const NUMERIC_SP = /^(?:0|[1-9]\d*)(?:\.\d+)?sp$/;
let writeQueue: Promise<void> = Promise.resolve();

export class TypographyConflictError extends Error {
  readonly currentHash: string;

  constructor(currentHash: string) {
    super("Button typography resources changed since they were loaded");
    this.currentHash = currentHash;
  }
}

export class TypographyResourceError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "TypographyResourceError";
  }
}

interface ResourceSnapshot {
  document: TypographyDocumentResponse;
  dimensionPaths: Record<TypographyLanguage, string>;
  rawDimensionsByLocale: Record<TypographyLanguage, string>;
}

export async function readButtonTypography(
  repoRoot: string,
): Promise<TypographyDocumentResponse> {
  return (await readResourceSnapshot(repoRoot)).document;
}

export async function validateButtonTypography(
  repoRoot: string,
  value: unknown,
): Promise<TypographyValidationResponse> {
  try {
    const snapshot = await readResourceSnapshot(repoRoot);
    return validateUpdates(value, snapshot.document.entries);
  } catch (error) {
    return invalid("resources", resourceErrorMessage(error));
  }
}

export async function saveButtonTypography(
  repoRoot: string,
  request: TypographySaveRequest,
): Promise<TypographyDocumentResponse> {
  const previous = writeQueue;
  let release!: () => void;
  writeQueue = new Promise<void>(resolve => { release = resolve; });
  await previous;
  try {
    const current = await readResourceSnapshot(repoRoot);
    if (current.document.hash !== request.expectedHash) {
      throw new TypographyConflictError(current.document.hash);
    }

    const validation = validateUpdates(request.entries, current.document.entries);
    if (!validation.valid) {
      throw new TypographyResourceError(
        validation.issues.map(item => `${item.path}: ${item.message}`).join("\n"),
      );
    }

    const updates = request.entries as ButtonTypographyUpdate[];
    const nextDimensions = new Map(
      updates.map(entry => [entry.controlId, entry.fontSizeSp] as const),
    );
    const nextContent = Object.fromEntries(LOCALES.map(language => [
      language,
      serializeDimensionResources(
        current.document.entries.map(entry => ({
          controlId: entry.controlId,
          fontSizeSp: nextDimensions.get(entry.controlId)![language],
        })),
      ),
    ])) as Record<TypographyLanguage, string>;

    if (
      LOCALES.every(language =>
        nextContent[language] === current.rawDimensionsByLocale[language],
      )
    ) {
      return current.document;
    }

    await replaceDimensionFiles(current.dimensionPaths, nextContent);
    return (await readResourceSnapshot(repoRoot)).document;
  } finally {
    release();
  }
}

function validateUpdates(
  value: unknown,
  currentEntries: readonly ButtonTypographyEntry[],
): TypographyValidationResponse {
  const issues: TypographyValidationIssue[] = [];
  if (!Array.isArray(value)) {
    return invalid("entries", "must be an array");
  }
  if (value.length !== currentEntries.length) {
    issues.push(issue("entries", "must contain every configured button label exactly once"));
  }

  const currentKeys = new Set(currentEntries.map(entry => entry.controlId));
  const seen = new Set<string>();
  const updateEntries: ButtonTypographyUpdate[] = [];
  value.forEach((value, index) => {
    const path = `entries[${index}]`;
    const entry = asRecord(value);
    if (!entry) {
      issues.push(issue(path, "must be an object"));
      return;
    }
    checkKeys(entry, path, ["controlId", "fontSizeSp"], issues);
    if (typeof entry.controlId !== "string" || !currentKeys.has(entry.controlId)) {
      issues.push(issue(`${path}.controlId`, "must identify a configured interactive label"));
      return;
    }
    if (seen.has(entry.controlId)) {
      issues.push(issue(`${path}.controlId`, "must be unique"));
    } else {
      seen.add(entry.controlId);
    }
    const fontSizes = asRecord(entry.fontSizeSp);
    if (!fontSizes) {
      issues.push(issue(`${path}.fontSizeSp`, "must be an object"));
      return;
    }
    checkKeys(fontSizes, `${path}.fontSizeSp`, LOCALES, issues);
    for (const language of LOCALES) {
      if (!Number.isFinite(fontSizes[language]) ||
          typeof fontSizes[language] !== "number" ||
          fontSizes[language] < MINIMUM_FONT_SIZE_SP ||
          fontSizes[language] > MAXIMUM_FONT_SIZE_SP
      ) {
        issues.push(issue(
          `${path}.fontSizeSp.${language}`,
          `must be a finite number between ${MINIMUM_FONT_SIZE_SP} and ${MAXIMUM_FONT_SIZE_SP} sp`,
        ));
      }
    }
    if (LOCALES.every(language => typeof fontSizes[language] === "number")) {
      updateEntries.push({
        controlId: entry.controlId,
        fontSizeSp: {
          en: fontSizes.en as number,
          pl: fontSizes.pl as number,
          es: fontSizes.es as number,
        },
      });
    }
  });

  if ([...currentKeys].some(key => !seen.has(key))) {
    issues.push(issue("entries", "must contain every configured button label exactly once"));
  }
  if (updateEntries.length === currentEntries.length) {
    updateEntries.forEach((entry, index) => {
      if (entry.controlId !== currentEntries[index]?.controlId) {
        issues.push(issue("entries", "must preserve the configured control order"));
      }
    });
  }

  return { valid: issues.length === 0, issues };
}

async function readResourceSnapshot(repoRoot: string): Promise<ResourceSnapshot> {
  const paths = await resourcePaths(repoRoot);
  try {
    const rawDimensionsByLocale = Object.fromEntries(await Promise.all(
      LOCALES.map(async language => [
        language,
        await readFile(paths.dimensions[language], "utf8"),
      ] as const),
    )) as Record<TypographyLanguage, string>;
    const rawStringsByLocale = Object.fromEntries(await Promise.all(
      LOCALES.map(async language => [
        language,
        await readFile(paths.strings[language], "utf8"),
      ] as const),
    )) as Record<TypographyLanguage, string>;
    const dimensionsByLocale = Object.fromEntries(LOCALES.map(language => [
      language,
      readDimensionValues(rawDimensionsByLocale[language]),
    ])) as Record<TypographyLanguage, Map<string, number>>;
    const stringsByLocale = Object.fromEntries(LOCALES.map(language => [
      language,
      readAndroidStringResources(rawStringsByLocale[language]),
    ])) as Record<TypographyLanguage, Map<string, string>>;
    const controlIds = BUTTON_TYPOGRAPHY_CONTROLS.map(control => control.controlId);
    for (const language of LOCALES.slice(1)) {
      const localeControlIds = [...dimensionsByLocale[language].keys()].sort();
      if (JSON.stringify(localeControlIds) !== JSON.stringify([...controlIds].sort())) {
        throw new TypographyResourceError(
          `Button typography controls in ${language} must match the configured control catalog`,
        );
      }
    }

    const englishControlIds = [...dimensionsByLocale.en.keys()].sort();
    if (JSON.stringify(englishControlIds) !== JSON.stringify([...controlIds].sort())) {
      throw new TypographyResourceError(
        "English button typography controls must match the configured control catalog",
      );
    }

    const entries = BUTTON_TYPOGRAPHY_CONTROLS.map(control => {
      const labels = Object.fromEntries(LOCALES.map(language => {
        const label = stringsByLocale[language].get(control.stringKey);
        if (label === undefined || label.trim() === "") {
          throw new TypographyResourceError(
            `Missing non-empty '${control.stringKey}' string resource for ${control.controlId} in ${language}`,
          );
        }
        return [language, label];
      })) as Record<TypographyLanguage, string>;
      return {
        ...control,
        labels,
        fontSizeSp: Object.fromEntries(LOCALES.map(language => [
          language,
          dimensionsByLocale[language].get(control.controlId)!,
        ])) as Record<TypographyLanguage, number>,
      };
    });
    const hash = hashDimensionFiles(rawDimensionsByLocale);
    return {
      document: { entries, hash },
      dimensionPaths: paths.dimensions,
      rawDimensionsByLocale,
    };
  } catch (error) {
    if (error instanceof TypographyResourceError) throw error;
    if (error instanceof AndroidResourcesXmlError) {
      throw new TypographyResourceError(error.message);
    }
    throw error;
  }
}

function readDimensionValues(content: string): Map<string, number> {
  let dimensions: Map<string, string>;
  try {
    dimensions = readAndroidDimensionResources(content);
  } catch (error) {
    if (error instanceof AndroidResourcesXmlError) {
      throw new TypographyResourceError(error.message);
    }
    throw error;
  }

  const values = new Map<string, number>();
  const configuredControlIds = new Set<string>(
    BUTTON_TYPOGRAPHY_CONTROLS.map(control => control.controlId),
  );
  for (const [resourceName, rawValue] of dimensions) {
    if (!resourceName.startsWith(BUTTON_DIMEN_PREFIX)) {
      throw new TypographyResourceError(
        `Unsupported dimension resource '${resourceName}' in button typography`,
      );
    }
    const controlId = resourceName.slice(BUTTON_DIMEN_PREFIX.length);
    if (!/^[a-z][a-z0-9_]*$/.test(controlId) || !configuredControlIds.has(controlId)) {
      throw new TypographyResourceError(`Unknown button typography control '${controlId}'`);
    }
    const match = NUMERIC_SP.exec(rawValue);
    const value = match ? Number(rawValue.slice(0, -2)) : Number.NaN;
    if (
      !Number.isFinite(value) ||
      value < MINIMUM_FONT_SIZE_SP ||
      value > MAXIMUM_FONT_SIZE_SP
    ) {
      throw new TypographyResourceError(
        `${resourceName} must be between ${MINIMUM_FONT_SIZE_SP} and ${MAXIMUM_FONT_SIZE_SP} sp`,
      );
    }
    values.set(controlId, value);
  }
  return values;
}

async function resourcePaths(repoRoot: string): Promise<{
  dimensions: Record<TypographyLanguage, string>;
  strings: Record<TypographyLanguage, string>;
}> {
  const root = await realpath(repoRoot);
  const result = {} as {
    dimensions: Record<TypographyLanguage, string>;
    strings: Record<TypographyLanguage, string>;
  };
  for (const language of LOCALES) {
    const valuesDirectory = language === "en" ? "values" : `values-${language}`;
    const directory = join(root, ...RESOURCE_DIRECTORY, valuesDirectory);
    const actualDirectory = await realpath(directory);
    if (!isWithin(root, actualDirectory)) {
      throw new TypographyResourceError("Button typography resource path escapes the repository root");
    }
    result.dimensions ??= {} as Record<TypographyLanguage, string>;
    result.strings ??= {} as Record<TypographyLanguage, string>;
    result.dimensions[language] = await safeResourceFile(
      root,
      actualDirectory,
      TYPOGRAPHY_FILE,
    );
    result.strings[language] = await safeResourceFile(root, actualDirectory, STRING_FILE);
  }
  return result;
}

async function safeResourceFile(
  root: string,
  directory: string,
  fileName: string,
): Promise<string> {
  const path = join(directory, fileName);
  const actualPath = await realpath(path);
  if (
    actualPath !== path ||
    !isWithin(root, actualPath) ||
    !isWithin(directory, actualPath)
  ) {
    throw new TypographyResourceError("Button typography file escapes its resource directory");
  }
  return actualPath;
}

async function replaceDimensionFiles(
  paths: Record<TypographyLanguage, string>,
  nextContent: Record<TypographyLanguage, string>,
): Promise<void> {
  const token = randomUUID();
  const staged = Object.fromEntries(LOCALES.map(language => [
    language,
    `${paths[language]}.${token}.tmp`,
  ])) as Record<TypographyLanguage, string>;
  const backups = Object.fromEntries(LOCALES.map(language => [
    language,
    `${paths[language]}.${token}.bak`,
  ])) as Record<TypographyLanguage, string>;
  const replaced: TypographyLanguage[] = [];

  try {
    await Promise.all(LOCALES.map(async language => {
      await writeFile(staged[language], nextContent[language], {
        encoding: "utf8",
        flag: "wx",
      });
      await copyFile(paths[language], backups[language]);
    }));
    for (const language of LOCALES) {
      await rename(staged[language], paths[language]);
      replaced.push(language);
    }
  } catch (error) {
    for (const language of replaced.reverse()) {
      await rename(backups[language], paths[language]);
    }
    throw error;
  } finally {
    await Promise.all(
      LOCALES.flatMap(language => [
        rm(staged[language], { force: true }),
        rm(backups[language], { force: true }),
      ]),
    );
  }
}

function serializeDimensionResources(
  values: readonly { controlId: string; fontSizeSp: number }[],
): string {
  const lines = [
    '<?xml version="1.0" encoding="utf-8"?>',
    "<resources>",
    ...[...values]
      .sort((left, right) => left.controlId.localeCompare(right.controlId))
      .map(value =>
        `    <dimen name="${BUTTON_DIMEN_PREFIX}${value.controlId}">${formatSp(value.fontSizeSp)}sp</dimen>`,
      ),
    "</resources>",
    "",
  ];
  return lines.join("\n");
}

function formatSp(value: number): string {
  return String(value);
}

function hashDimensionFiles(content: Record<TypographyLanguage, string>): string {
  return createHash("sha256")
    .update(JSON.stringify(LOCALES.map(language => [language, content[language]])))
    .digest("hex");
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function checkKeys(
  value: Record<string, unknown>,
  path: string,
  expected: readonly string[],
  issues: TypographyValidationIssue[],
): void {
  const expectedSet = new Set(expected);
  for (const key of expected) {
    if (!(key in value)) issues.push(issue(`${path}.${key}`, "is required"));
  }
  for (const key of Object.keys(value)) {
    if (!expectedSet.has(key)) issues.push(issue(`${path}.${key}`, "is not supported"));
  }
}

function invalid(path: string, message: string): TypographyValidationResponse {
  return { valid: false, issues: [issue(path, message)] };
}

function issue(path: string, message: string): TypographyValidationIssue {
  return { severity: "error", path, message };
}

function resourceErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function isWithin(root: string, path: string): boolean {
  const relativePath = relative(root, path);
  return relativePath === "" ||
    (!relativePath.startsWith(`..${sep}`) && relativePath !== ".." && !isAbsolute(relativePath));
}

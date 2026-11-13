import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  AndroidResourcesXmlError,
  parseAndroidResourcesXml,
  readAndroidDimensionResources,
  readAndroidStringResources,
} from "./androidResourcesXml.ts";
import { resolveRepoRoot } from "./repoRoot.ts";

describe("Android resource XML codec", () => {
  it("reads string text, XML entities, and nested markup", () => {
    const strings = readAndroidStringResources(`
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
        <!-- Button label -->
        <string name="button_label">A &amp; <b>B</b> &#x43;</string>
      </resources>
    `);

    expect(strings.get("button_label")).toBe("A & B C");
  });

  it("reads named dimensions and rejects unsupported dimension resources", () => {
    const dimensions = readAndroidDimensionResources(`
      <resources>
        <dimen name="button_text_size_test_mode">10.5sp</dimen>
      </resources>
    `);

    expect(dimensions.get("button_text_size_test_mode")).toBe("10.5sp");
    expect(() => readAndroidDimensionResources(`
      <resources><string name="unexpected">value</string></resources>
    `)).toThrow(AndroidResourcesXmlError);
  });

  it("rejects malformed, ambiguous, and entity-bearing XML declarations", () => {
    for (const xml of [
      "<resources><string name=\"one\">one</resources>",
      "<!DOCTYPE resources [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><resources/>",
      "<resources><string name=\"one\">&unknown;</string></resources>",
    ]) {
      expect(() => parseAndroidResourcesXml(xml)).toThrow(AndroidResourcesXmlError);
    }
  });

  it("parses all checked-in localized string resources", async () => {
    const root = await resolveRepoRoot();
    for (const localeDirectory of ["values", "values-pl", "values-es"]) {
      const content = await readFile(
        join(root, "android-app/src/main/res", localeDirectory, "strings.xml"),
        "utf8",
      );
      const strings = readAndroidStringResources(content);
      expect(strings.has("test_mode")).toBe(true);
    }
  });
});

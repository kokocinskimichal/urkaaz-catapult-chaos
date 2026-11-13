export interface AndroidResourceElement {
  name: string;
  attributes: Readonly<Record<string, string>>;
  children: Array<AndroidResourceElement | string>;
}

const MAX_XML_CHARACTERS = 2_000_000;
const MAX_NESTING_DEPTH = 64;

export class AndroidResourcesXmlError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AndroidResourcesXmlError";
  }
}

export function parseAndroidResourcesXml(content: string): AndroidResourceElement[] {
  if (content.length > MAX_XML_CHARACTERS) {
    throw new AndroidResourcesXmlError("Android resource XML is too large");
  }

  let index = content.startsWith("\uFEFF") ? 1 : 0;
  skipMisc();
  const root = parseElement(0);
  if (root.name !== "resources") {
    throw new AndroidResourcesXmlError("Android resource XML must have a resources root");
  }
  if (root.children.some(child => typeof child === "string" && child.trim() !== "")) {
    throw new AndroidResourcesXmlError("Unexpected text inside the resources root");
  }
  skipMisc();
  if (index !== content.length) {
    throw new AndroidResourcesXmlError("Unexpected content after the resources root");
  }
  return root.children.filter(
    (child): child is AndroidResourceElement => typeof child !== "string",
  );

  function parseElement(depth: number): AndroidResourceElement {
    if (depth >= MAX_NESTING_DEPTH) {
      throw new AndroidResourcesXmlError("Android resource XML is nested too deeply");
    }
    expect("<");
    if (content[index] === "/" || content[index] === "!" || content[index] === "?") {
      throw new AndroidResourcesXmlError(`Expected an element at character ${index}`);
    }
    const name = readName();
    const attributes: Record<string, string> = {};
    skipWhitespace();

    while (content[index] !== ">" && !(content[index] === "/" && content[index + 1] === ">")) {
      const attributeName = readName();
      if (Object.hasOwn(attributes, attributeName)) {
        throw new AndroidResourcesXmlError(`Duplicate attribute '${attributeName}'`);
      }
      skipWhitespace();
      expect("=");
      skipWhitespace();
      const quote = content[index];
      if (quote !== "\"" && quote !== "'") {
        throw new AndroidResourcesXmlError(`Attribute '${attributeName}' must be quoted`);
      }
      index += 1;
      const valueStart = index;
      while (index < content.length && content[index] !== quote) {
        if (content[index] === "<") {
          throw new AndroidResourcesXmlError(`Invalid '<' in attribute '${attributeName}'`);
        }
        index += 1;
      }
      if (index >= content.length) {
        throw new AndroidResourcesXmlError(`Unterminated attribute '${attributeName}'`);
      }
      attributes[attributeName] = decodeEntities(content.slice(valueStart, index));
      index += 1;
      skipWhitespace();
    }

    if (content[index] === "/" && content[index + 1] === ">") {
      index += 2;
      return { name, attributes, children: [] };
    }
    expect(">");

    const children: Array<AndroidResourceElement | string> = [];
    while (index < content.length) {
      if (content.startsWith("</", index)) {
        index += 2;
        const closingName = readName();
        skipWhitespace();
        expect(">");
        if (closingName !== name) {
          throw new AndroidResourcesXmlError(
            `Mismatched closing tag '</${closingName}>' for '<${name}>'`,
          );
        }
        return { name, attributes, children };
      }
      if (content.startsWith("<!--", index)) {
        skipComment();
        continue;
      }
      if (content.startsWith("<![CDATA[", index)) {
        index += "<![CDATA[".length;
        const end = content.indexOf("]]>", index);
        if (end < 0) throw new AndroidResourcesXmlError("Unterminated CDATA section");
        children.push(content.slice(index, end));
        index = end + 3;
        continue;
      }
      if (content.startsWith("<!", index) || content.startsWith("<?", index)) {
        throw new AndroidResourcesXmlError(
          `Unsupported declaration inside '<${name}>' at character ${index}`,
        );
      }
      if (content[index] === "<") {
        children.push(parseElement(depth + 1));
        continue;
      }
      const textStart = index;
      while (index < content.length && content[index] !== "<") index += 1;
      children.push(decodeEntities(content.slice(textStart, index)));
    }
    throw new AndroidResourcesXmlError(`Unclosed element '<${name}>'`);
  }

  function skipMisc(): void {
    while (index < content.length) {
      skipWhitespace();
      if (content.startsWith("<!--", index)) {
        skipComment();
      } else if (content.startsWith("<?xml", index)) {
        const end = content.indexOf("?>", index);
        if (end < 0) throw new AndroidResourcesXmlError("Unterminated XML declaration");
        index = end + 2;
      } else {
        return;
      }
    }
  }

  function skipComment(): void {
    const end = content.indexOf("-->", index + 4);
    if (end < 0) throw new AndroidResourcesXmlError("Unterminated XML comment");
    index = end + 3;
  }

  function readName(): string {
    const start = index;
    while (index < content.length && isNameCharacter(content[index])) index += 1;
    if (start === index || !isNameStart(content[start])) {
      throw new AndroidResourcesXmlError(`Expected an XML name at character ${index}`);
    }
    return content.slice(start, index);
  }

  function skipWhitespace(): void {
    while (index < content.length && isXmlWhitespace(content[index])) index += 1;
  }

  function expect(expected: string): void {
    if (!content.startsWith(expected, index)) {
      throw new AndroidResourcesXmlError(`Expected '${expected}' at character ${index}`);
    }
    index += expected.length;
  }
}

export function readAndroidStringResources(content: string): Map<string, string> {
  const strings = new Map<string, string>();
  for (const element of parseAndroidResourcesXml(content)) {
    if (element.name !== "string") continue;
    const name = element.attributes.name;
    if (!name) throw new AndroidResourcesXmlError("Every string resource needs a name");
    if (strings.has(name)) {
      throw new AndroidResourcesXmlError(`Duplicate string resource '${name}'`);
    }
    strings.set(name, elementText(element));
  }
  return strings;
}

export function readAndroidDimensionResources(content: string): Map<string, string> {
  const dimensions = new Map<string, string>();
  for (const element of parseAndroidResourcesXml(content)) {
    if (element.name !== "dimen") {
      throw new AndroidResourcesXmlError(
        `Only <dimen> entries are supported in this file, found <${element.name}>`,
      );
    }
    const attributeNames = Object.keys(element.attributes);
    if (attributeNames.length !== 1 || attributeNames[0] !== "name") {
      throw new AndroidResourcesXmlError("A dimen resource may only have a name attribute");
    }
    const name = element.attributes.name;
    if (!name) throw new AndroidResourcesXmlError("Every dimen resource needs a name");
    if (dimensions.has(name)) {
      throw new AndroidResourcesXmlError(`Duplicate dimen resource '${name}'`);
    }
    if (element.children.some(child => typeof child !== "string")) {
      throw new AndroidResourcesXmlError(`Dimension '${name}' cannot contain nested elements`);
    }
    dimensions.set(name, elementText(element).trim());
  }
  return dimensions;
}

export function androidResourceElementText(element: AndroidResourceElement): string {
  return elementText(element);
}

function elementText(element: AndroidResourceElement): string {
  return element.children.map(child =>
    typeof child === "string" ? child : elementText(child),
  ).join("");
}

function decodeEntities(value: string): string {
  let decoded = "";
  let cursor = 0;
  while (cursor < value.length) {
    const ampersand = value.indexOf("&", cursor);
    if (ampersand < 0) {
      decoded += value.slice(cursor);
      break;
    }
    decoded += value.slice(cursor, ampersand);
    const semicolon = value.indexOf(";", ampersand + 1);
    if (semicolon < 0) {
      throw new AndroidResourcesXmlError("Unterminated XML entity");
    }
    const name = value.slice(ampersand + 1, semicolon);
    decoded += decodeEntity(name);
    cursor = semicolon + 1;
  }
  return decoded;
}

function decodeEntity(name: string): string {
  switch (name) {
    case "amp":
      return "&";
    case "lt":
      return "<";
    case "gt":
      return ">";
    case "apos":
      return "'";
    case "quot":
      return "\"";
    default: {
      const codePoint = name.startsWith("#x")
        ? Number.parseInt(name.slice(2), 16)
        : name.startsWith("#")
          ? Number.parseInt(name.slice(1), 10)
          : Number.NaN;
      if (
        !Number.isInteger(codePoint) ||
        codePoint < 0 ||
        codePoint > 0x10ffff ||
        (codePoint >= 0xd800 && codePoint <= 0xdfff)
      ) {
        throw new AndroidResourcesXmlError(`Unsupported XML entity '&${name};'`);
      }
      return String.fromCodePoint(codePoint);
    }
  }
}

function isNameStart(character: string | undefined): boolean {
  return character !== undefined && /[A-Za-z_:]/.test(character);
}

function isNameCharacter(character: string | undefined): boolean {
  return character !== undefined && /[A-Za-z0-9_.:-]/.test(character);
}

function isXmlWhitespace(character: string | undefined): boolean {
  return character === " " || character === "\t" || character === "\n" || character === "\r";
}

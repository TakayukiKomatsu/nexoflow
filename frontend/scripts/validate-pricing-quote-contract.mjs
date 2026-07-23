import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const defaults = {
  client: resolve(scriptDirectory, "../src/api/client.ts"),
  document:
    process.env.SRM_OPENAPI_DOCUMENT ??
    resolve(
      scriptDirectory,
      "../../backend/build/generated/openapi/srm-openapi.json",
    ),
};

function fail(message) {
  throw new Error(`API-CONTRACT-001 failed: ${message}`);
}

function parseArguments(arguments_) {
  const options = { ...defaults };
  for (let index = 0; index < arguments_.length; index += 2) {
    const name = arguments_[index];
    const value = arguments_[index + 1];
    if (!value || (name !== "--client" && name !== "--document")) {
      fail(`unsupported arguments: ${arguments_.join(" ")}`);
    }
    options[name.slice(2)] = value;
  }
  return options;
}

async function loadOpenApi(source) {
  let text;
  if (/^https?:\/\//i.test(source)) {
    const response = await fetch(source, {
      signal: AbortSignal.timeout(10_000),
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      fail(`OpenAPI URL ${source} returned HTTP ${response.status}`);
    }
    text = await response.text();
  } else {
    try {
      text = await readFile(resolve(source), "utf8");
    } catch (cause) {
      fail(
        `OpenAPI document is unavailable at ${resolve(source)} (${cause.message})`,
      );
    }
  }

  try {
    return JSON.parse(text);
  } catch (cause) {
    fail(`OpenAPI document is not valid JSON (${cause.message})`);
  }
}

function loadTypeScriptModel(clientPath) {
  const configurationPath = ts.findConfigFile(
    dirname(clientPath),
    ts.sys.fileExists,
    "tsconfig.app.json",
  );
  if (!configurationPath)
    fail(`could not find tsconfig.app.json for ${clientPath}`);
  const configuration = ts.readConfigFile(configurationPath, ts.sys.readFile);
  if (configuration.error) {
    fail(
      ts.flattenDiagnosticMessageText(configuration.error.messageText, "\n"),
    );
  }
  const parsed = ts.parseJsonConfigFileContent(
    configuration.config,
    ts.sys,
    dirname(configurationPath),
  );
  const program = ts.createProgram(parsed.fileNames, parsed.options);
  const absoluteClientPath = resolve(clientPath);
  const sourceFile = program
    .getSourceFiles()
    .find((source) => resolve(source.fileName) === absoluteClientPath);
  if (!sourceFile)
    fail(`TypeScript program did not load ${absoluteClientPath}`);
  const checker = program.getTypeChecker();

  function namedType(name) {
    const declaration = sourceFile.statements.find(
      (statement) =>
        ts.isTypeAliasDeclaration(statement) && statement.name.text === name,
    );
    if (!declaration) fail(`frontend model does not export type ${name}`);
    const symbol = checker.getSymbolAtLocation(declaration.name);
    if (!symbol) fail(`could not resolve frontend type ${name}`);
    return checker.getDeclaredTypeOfSymbol(symbol);
  }

  return { checker, namedType };
}

function schemaProperties(document, schemaName) {
  const properties = document?.components?.schemas?.[schemaName]?.properties;
  if (
    !properties ||
    typeof properties !== "object" ||
    Array.isArray(properties)
  ) {
    fail(`OpenAPI schema ${schemaName} has no object properties`);
  }
  return properties;
}

function comparePropertyNames(label, openApiProperties, type, checker) {
  const openApiNames = Object.keys(openApiProperties).sort();
  const frontendNames = checker
    .getPropertiesOfType(type)
    .map(({ name }) => name)
    .sort();
  const missingFromFrontend = openApiNames.filter(
    (name) => !frontendNames.includes(name),
  );
  const missingFromOpenApi = frontendNames.filter(
    (name) => !openApiNames.includes(name),
  );
  if (missingFromFrontend.length || missingFromOpenApi.length) {
    fail(
      `${label} property mismatch; missing from frontend: ${missingFromFrontend.join(", ") || "none"}; missing from OpenAPI: ${missingFromOpenApi.join(", ") || "none"}`,
    );
  }
}

function comparePropertyTypes(
  label,
  openApiProperties,
  type,
  checker,
  document,
) {
  const symbols = new Map(
    checker.getPropertiesOfType(type).map((symbol) => [symbol.name, symbol]),
  );
  for (const [name, schema] of Object.entries(openApiProperties)) {
    const symbol = symbols.get(name);
    const declaration = symbol?.valueDeclaration ?? symbol?.declarations?.[0];
    if (!symbol || !declaration) fail(`${label}.${name} cannot be resolved`);
    const frontendType = checker.getNonNullableType(
      checker.getTypeOfSymbolAtLocation(symbol, declaration),
    );
    if (schema?.$ref) {
      const referencedName = schema.$ref.split("/").at(-1);
      const referencedProperties = schemaProperties(document, referencedName);
      comparePropertyNames(
        `${label}.${name}`,
        referencedProperties,
        frontendType,
        checker,
      );
      continue;
    }
    if (
      schema?.type === "string" &&
      (frontendType.flags & ts.TypeFlags.StringLike) === 0
    ) {
      fail(
        `${label}.${name} is ${checker.typeToString(frontendType)} in the frontend but string in OpenAPI`,
      );
    }
  }
}

export async function validateContract(options) {
  const document = await loadOpenApi(options.document);
  const { checker, namedType } = loadTypeScriptModel(resolve(options.client));
  const mappings = [
    ["QuoteResponse", "PricingQuote"],
    ["PricingBreakdownResponse", "PricingBreakdown"],
  ];
  for (const [schemaName, typeName] of mappings) {
    const properties = schemaProperties(document, schemaName);
    const type = namedType(typeName);
    comparePropertyNames(typeName, properties, type, checker);
    comparePropertyTypes(typeName, properties, type, checker, document);
  }
}

try {
  const options = parseArguments(process.argv.slice(2));
  await validateContract(options);
  console.log(
    `API-CONTRACT-001 passed: ${options.client} matches ${options.document}`,
  );
} catch (cause) {
  console.error(cause instanceof Error ? cause.message : String(cause));
  process.exitCode = 1;
}

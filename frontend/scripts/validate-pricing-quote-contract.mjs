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

function schemaObject(document, schemaName) {
  const schema = document?.components?.schemas?.[schemaName];
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) {
    fail(`OpenAPI schema ${schemaName} is unavailable`);
  }
  return schema;
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

function compareRequiredProperties(label, schema, type, checker) {
  const openApiRequired = [...(schema.required ?? [])].sort();
  const frontendRequired = checker
    .getPropertiesOfType(type)
    .filter((symbol) => (symbol.flags & ts.SymbolFlags.Optional) === 0)
    .map(({ name }) => name)
    .sort();
  const requiredOnlyByOpenApi = openApiRequired.filter(
    (name) => !frontendRequired.includes(name),
  );
  const requiredOnlyByFrontend = frontendRequired.filter(
    (name) => !openApiRequired.includes(name),
  );
  if (requiredOnlyByOpenApi.length || requiredOnlyByFrontend.length) {
    fail(
      `${label} required-property mismatch; required only by OpenAPI: ${requiredOnlyByOpenApi.join(", ") || "none"}; required only by frontend: ${requiredOnlyByFrontend.join(", ") || "none"}`,
    );
  }
}

const formatAliases = new Map([
  ["uuid", "Uuid"],
  ["date", "IsoDate"],
  ["date-time", "IsoInstant"],
  ["int32", "Int32"],
  ["int64", "Int64"],
]);

function declaredFormat(declaration) {
  if (!declaration?.type) return undefined;
  const declarationText = declaration.type.getText();
  return [...formatAliases.entries()].find(([, alias]) =>
    new RegExp(`\\b${alias}\\b`).test(declarationText),
  )?.[0];
}

function compareFormat(label, schema, declaration) {
  const openApiFormat = formatAliases.has(schema.format)
    ? schema.format
    : undefined;
  const frontendFormat = declaredFormat(declaration);
  if (openApiFormat !== frontendFormat) {
    fail(
      `${label} format mismatch; frontend: ${frontendFormat ?? "none"}; OpenAPI: ${openApiFormat ?? "none"}`,
    );
  }
}

function includesTypeFlag(type, flag) {
  return (
    (type.flags & flag) !== 0 ||
    (type.isUnion() &&
      type.types.every((member) => includesTypeFlag(member, flag)))
  );
}

function isNullable(type) {
  return (
    (type.flags & ts.TypeFlags.Null) !== 0 ||
    (type.isUnion() &&
      type.types.some((member) => (member.flags & ts.TypeFlags.Null) !== 0))
  );
}

function stringLiteralValues(type) {
  if ((type.flags & ts.TypeFlags.StringLiteral) !== 0) return [type.value];
  if (!type.isUnion()) return undefined;
  const values = type.types.flatMap((member) =>
    (member.flags & ts.TypeFlags.StringLiteral) !== 0 ? [member.value] : [],
  );
  return values.length === type.types.length ? values.sort() : undefined;
}

function compareEnum(label, schema, type) {
  const openApiValues = Array.isArray(schema.enum)
    ? [...schema.enum].sort()
    : undefined;
  const frontendValues = stringLiteralValues(type);
  if (
    openApiValues?.length !== frontendValues?.length ||
    openApiValues?.some((value, index) => value !== frontendValues?.[index])
  ) {
    if (openApiValues || frontendValues) {
      fail(
        `${label} enum mismatch; frontend: ${frontendValues?.join(", ") ?? "unconstrained"}; OpenAPI: ${openApiValues?.join(", ") ?? "unconstrained"}`,
      );
    }
  }
}

function compareSchemaType(
  label,
  schema,
  type,
  checker,
  document,
  declaration,
) {
  const frontendNullable = isNullable(type);
  if (Boolean(schema.nullable) !== frontendNullable) {
    fail(
      `${label} nullable mismatch; frontend: ${frontendNullable}; OpenAPI: ${Boolean(schema.nullable)}`,
    );
  }
  const nonNullableType = checker.getNonNullableType(type);

  if (schema?.$ref) {
    const referencedName = schema.$ref.split("/").at(-1);
    compareObjectSchema(
      label,
      schemaObject(document, referencedName),
      nonNullableType,
      checker,
      document,
    );
    return;
  }

  if (schema?.type === "array") {
    if (!checker.isArrayType(nonNullableType)) {
      fail(
        `${label} is ${checker.typeToString(nonNullableType)} in the frontend but array in OpenAPI`,
      );
    }
    const elementType = checker.getTypeArguments(nonNullableType)[0];
    if (!schema.items || !elementType) {
      fail(`${label} array items cannot be resolved`);
    }
    compareSchemaType(
      `${label}[]`,
      schema.items,
      elementType,
      checker,
      document,
    );
    return;
  }

  const expectedFlag = {
    string: ts.TypeFlags.StringLike,
    integer: ts.TypeFlags.NumberLike,
    number: ts.TypeFlags.NumberLike,
    boolean: ts.TypeFlags.BooleanLike,
  }[schema?.type];
  if (!expectedFlag) {
    fail(`${label} uses unsupported OpenAPI type ${schema?.type ?? "unknown"}`);
  }
  if (!includesTypeFlag(nonNullableType, expectedFlag)) {
    fail(
      `${label} is ${checker.typeToString(nonNullableType)} in the frontend but ${schema.type} in OpenAPI`,
    );
  }
  compareFormat(label, schema, declaration);
  if (schema.type === "string") compareEnum(label, schema, nonNullableType);
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
    const frontendType = checker.getTypeOfSymbolAtLocation(symbol, declaration);
    compareSchemaType(
      `${label}.${name}`,
      schema,
      frontendType,
      checker,
      document,
      declaration,
    );
  }
}

function compareObjectSchema(label, schema, type, checker, document) {
  const properties = schema.properties;
  if (
    !properties ||
    typeof properties !== "object" ||
    Array.isArray(properties)
  ) {
    fail(`${label} OpenAPI object has no properties`);
  }
  comparePropertyNames(label, properties, type, checker);
  compareRequiredProperties(label, schema, type, checker);
  comparePropertyTypes(label, properties, type, checker, document);
}

export async function validateContract(options) {
  const document = await loadOpenApi(options.document);
  const { checker, namedType } = loadTypeScriptModel(resolve(options.client));
  const mappings = [
    ["QuoteResponse", "PricingQuote"],
    ["PricingBreakdownResponse", "PricingBreakdown"],
    ["PricingBreakdownResponse", "PricingSimulation"],
    ["ItemResponse", "SettlementItem"],
    ["PreviewResponse", "SettlementPreview"],
    ["SettlementResponse", "Settlement"],
    ["EntryResponse", "StatementEntry"],
    ["PageResponse", "StatementPage"],
    ["AccessToken", "AccessToken"],
    ["CurrentUser", "CurrentUser"],
    ["Response", "Receivable"],
  ];
  for (const [schemaName, typeName] of mappings) {
    const type = namedType(typeName);
    compareObjectSchema(
      typeName,
      schemaObject(document, schemaName),
      type,
      checker,
      document,
    );
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

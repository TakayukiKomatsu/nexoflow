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

  function namedValueType(name) {
    for (const statement of sourceFile.statements) {
      if (!ts.isVariableStatement(statement)) continue;
      const declaration = statement.declarationList.declarations.find(
        (candidate) =>
          ts.isIdentifier(candidate.name) && candidate.name.text === name,
      );
      if (declaration) return checker.getTypeAtLocation(declaration.name);
    }
    fail(`frontend model does not export value ${name}`);
  }

  function literalProperty(type, propertyName, label) {
    const symbol = checker.getPropertyOfType(type, propertyName);
    const declaration = symbol?.valueDeclaration ?? symbol?.declarations?.[0];
    if (!symbol || !declaration)
      fail(`${label}.${propertyName} is unavailable`);
    const propertyType = checker.getTypeOfSymbolAtLocation(symbol, declaration);
    if ((propertyType.flags & ts.TypeFlags.StringLiteral) === 0) {
      fail(`${label}.${propertyName} must be a string literal`);
    }
    return propertyType.value;
  }

  function operationDefinitions() {
    const operationsType = namedValueType("API_OPERATIONS");
    return new Map(
      checker.getPropertiesOfType(operationsType).map((symbol) => {
        const declaration = symbol.valueDeclaration ?? symbol.declarations?.[0];
        if (!declaration) fail(`API_OPERATIONS.${symbol.name} is unavailable`);
        const operationType = checker.getTypeOfSymbolAtLocation(
          symbol,
          declaration,
        );
        return [
          symbol.name,
          {
            method: literalProperty(
              operationType,
              "method",
              `API_OPERATIONS.${symbol.name}`,
            ),
            path: literalProperty(
              operationType,
              "path",
              `API_OPERATIONS.${symbol.name}`,
            ),
          },
        ];
      }),
    );
  }

  return { checker, namedType, operationDefinitions };
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
  const declaredTypes = Array.isArray(schema.type)
    ? schema.type
    : [schema.type];
  const openApiNullable =
    Boolean(schema.nullable) || declaredTypes.includes("null");
  const nonNullSchemaTypes = declaredTypes.filter(
    (declaredType) => declaredType && declaredType !== "null",
  );
  if (nonNullSchemaTypes.length > 1) {
    fail(`${label} uses an unsupported OpenAPI type union`);
  }
  const schemaType =
    nonNullSchemaTypes[0] ??
    (Array.isArray(schema.enum) &&
    schema.enum.length > 0 &&
    schema.enum.every((value) => typeof value === "string")
      ? "string"
      : undefined);
  if (openApiNullable !== frontendNullable) {
    fail(
      `${label} nullable mismatch; frontend: ${frontendNullable}; OpenAPI: ${openApiNullable}`,
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

  if (schemaType === "array") {
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
      declaration,
    );
    return;
  }

  const expectedFlag = {
    string: ts.TypeFlags.StringLike,
    integer: ts.TypeFlags.NumberLike,
    number: ts.TypeFlags.NumberLike,
    boolean: ts.TypeFlags.BooleanLike,
  }[schemaType];
  if (!expectedFlag) {
    fail(`${label} uses unsupported OpenAPI type ${schemaType ?? "unknown"}`);
  }
  if (!includesTypeFlag(nonNullableType, expectedFlag)) {
    fail(
      `${label} is ${checker.typeToString(nonNullableType)} in the frontend but ${schemaType} in OpenAPI`,
    );
  }
  compareFormat(label, schema, declaration);
  if (schemaType === "string") compareEnum(label, schema, nonNullableType);
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

function compareRequestObjectSchema(label, schema, type, checker, document) {
  const properties = schema.properties;
  if (
    !properties ||
    typeof properties !== "object" ||
    Array.isArray(properties)
  ) {
    fail(`${label} OpenAPI request object has no properties`);
  }
  const symbols = checker.getPropertiesOfType(type);
  const frontendNames = symbols.map(({ name }) => name);
  const missingFromOpenApi = frontendNames.filter(
    (name) => !Object.hasOwn(properties, name),
  );
  const openApiRequired = new Set(schema.required ?? []);
  const requiredOnlyByOpenApi = [...openApiRequired].filter(
    (name) => !frontendNames.includes(name),
  );
  const optionalityMismatch = symbols
    .filter(
      (symbol) =>
        ((symbol.flags & ts.SymbolFlags.Optional) === 0) !==
        openApiRequired.has(symbol.name),
    )
    .map(({ name }) => name);
  if (
    missingFromOpenApi.length ||
    requiredOnlyByOpenApi.length ||
    optionalityMismatch.length
  ) {
    fail(
      `${label} request mismatch; missing from OpenAPI: ${missingFromOpenApi.join(", ") || "none"}; required only by OpenAPI: ${requiredOnlyByOpenApi.join(", ") || "none"}; optionality mismatch: ${optionalityMismatch.join(", ") || "none"}`,
    );
  }
  const frontendProperties = Object.fromEntries(
    frontendNames.map((name) => [name, properties[name]]),
  );
  comparePropertyTypes(label, frontendProperties, type, checker, document);
}

function referenceName(schema, label) {
  if (typeof schema?.$ref !== "string") {
    fail(`${label} must use a component schema reference`);
  }
  return schema.$ref.split("/").at(-1);
}

function jsonSchema(content, label) {
  const schema =
    content?.["application/json"]?.schema ?? content?.["*/*"]?.schema;
  if (!schema) fail(`${label} has no JSON-compatible schema`);
  return schema;
}

function compareParameterGroup(
  label,
  parameters,
  location,
  typeName,
  namedType,
  checker,
  document,
) {
  const located = parameters.filter((parameter) => parameter.in === location);
  if (!typeName) {
    if (located.length) {
      fail(`${label} has unexpected ${location} parameter ${located[0].name}`);
    }
    return;
  }
  const schema = {
    type: "object",
    properties: Object.fromEntries(
      located.map((parameter) => [parameter.name, parameter.schema]),
    ),
    required: located
      .filter(({ required }) => required)
      .map(({ name }) => name),
  };
  compareObjectSchema(
    `${label} ${location} parameters`,
    schema,
    namedType(typeName),
    checker,
    document,
  );
}

function compareOperations(document, model) {
  const mappings = [
    {
      key: "login",
      bodySchema: "LoginRequest",
      bodyType: "LoginRequest",
      responseSchema: "AccessToken",
    },
    {
      key: "currentUser",
      responseSchema: "CurrentUser",
    },
    {
      key: "simulate",
      bodySchema: "SimulationRequest",
      bodyType: "PricingSimulationRequest",
      responseSchema: "PricingBreakdownResponse",
    },
    {
      key: "createReceivable",
      bodySchema: "ReceivableRequest",
      bodyType: "ReceivableRequest",
      responseSchema: "ReceivableResponse",
    },
    {
      key: "createQuote",
      bodySchema: "QuoteRequest",
      bodyType: "QuoteRequest",
      responseSchema: "QuoteResponse",
    },
    {
      key: "previewSettlement",
      bodySchema: "QuoteIdsRequest",
      bodyType: "QuoteIdsRequest",
      responseSchema: "PreviewResponse",
    },
    {
      key: "settle",
      bodySchema: "QuoteIdsRequest",
      bodyType: "QuoteIdsRequest",
      headerType: "SettlementHeaders",
      responseSchema: "SettlementResponse",
    },
    {
      key: "settlement",
      pathType: "SettlementPathParameters",
      responseSchema: "SettlementResponse",
    },
    {
      key: "statement",
      queryType: "StatementFilters",
      responseSchema: "PageResponse",
    },
  ];
  const definitions = model.operationDefinitions();
  const unmapped = [...definitions.keys()].filter(
    (name) => !mappings.some(({ key }) => key === name),
  );
  if (unmapped.length || definitions.size !== mappings.length) {
    fail(
      `API_OPERATIONS mapping mismatch; unmapped frontend operations: ${unmapped.join(", ") || "none"}`,
    );
  }

  for (const mapping of mappings) {
    const definition = definitions.get(mapping.key);
    if (!definition) fail(`API_OPERATIONS.${mapping.key} is unavailable`);
    const fullPath = `/api/v1${definition.path}`;
    const pathItem = document?.paths?.[fullPath];
    const operation = pathItem?.[definition.method.toLowerCase()];
    const label = `${definition.method} ${fullPath}`;
    if (!operation) fail(`${label} is unavailable in OpenAPI`);

    const parameters = [
      ...(Array.isArray(pathItem.parameters) ? pathItem.parameters : []),
      ...(Array.isArray(operation.parameters) ? operation.parameters : []),
    ];
    compareParameterGroup(
      label,
      parameters,
      "path",
      mapping.pathType,
      model.namedType,
      model.checker,
      document,
    );
    compareParameterGroup(
      label,
      parameters,
      "query",
      mapping.queryType,
      model.namedType,
      model.checker,
      document,
    );
    compareParameterGroup(
      label,
      parameters,
      "header",
      mapping.headerType,
      model.namedType,
      model.checker,
      document,
    );

    if (mapping.bodyType) {
      if (operation.requestBody?.required !== true) {
        fail(`${label} request body must be required`);
      }
      const bodySchema = jsonSchema(operation.requestBody.content, label);
      const bodySchemaName = referenceName(bodySchema, `${label} request body`);
      if (bodySchemaName !== mapping.bodySchema) {
        fail(
          `${label} request schema mismatch; frontend expects ${mapping.bodySchema}, OpenAPI uses ${bodySchemaName}`,
        );
      }
      compareRequestObjectSchema(
        `${label} request body`,
        schemaObject(document, bodySchemaName),
        model.namedType(mapping.bodyType),
        model.checker,
        document,
      );
    } else if (operation.requestBody) {
      fail(`${label} has an unexpected request body`);
    }

    const successResponse = Object.entries(operation.responses ?? {}).find(
      ([status]) => /^2\d\d$/.test(status),
    )?.[1];
    const responseName = referenceName(
      jsonSchema(successResponse?.content, `${label} success response`),
      `${label} success response`,
    );
    if (responseName !== mapping.responseSchema) {
      fail(
        `${label} response schema mismatch; frontend expects ${mapping.responseSchema}, OpenAPI uses ${responseName}`,
      );
    }
  }
}

export async function validateContract(options) {
  const document = await loadOpenApi(options.document);
  const model = loadTypeScriptModel(resolve(options.client));
  const { checker, namedType } = model;
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
    ["ReceivableResponse", "Receivable"],
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
  compareOperations(document, model);
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

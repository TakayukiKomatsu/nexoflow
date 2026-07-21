import { readFile } from "node:fs/promises";

const requiredQuoteFields = [
  "id: string;",
  "receivableId: string;",
  "productType: string;",
  "dueDate: string;",
  "pricing: PricingBreakdown;",
  "expiresAt: string;",
  "status: string;",
];
const forbiddenBreakdownFields = ["productType", "dueDate"];
const clientSource = await readFile(new URL("../src/api/client.ts", import.meta.url), "utf8");

function fail(message) {
  console.error(`API-CONTRACT-001 failed: ${message}`);
  process.exit(1);
}

function extractObjectType(name) {
  const match = clientSource.match(
    new RegExp(`export\\s+type\\s+${name}\\s*=\\s*\\{([\\s\\S]*?)^\\};`, "m"),
  );

  if (!match) {
    fail(`could not find an object declaration for ${name} in frontend/src/api/client.ts`);
  }

  return match[1];
}

const quoteBody = extractObjectType("PricingQuote");
const missingQuoteFields = requiredQuoteFields.filter((field) => !quoteBody.includes(field));
if (missingQuoteFields.length > 0) {
  fail(`PricingQuote is missing required top-level fields: ${missingQuoteFields.join(", ")}`);
}

const pricingBreakdownDeclaration = clientSource.match(
  /^export\s+type\s+PricingBreakdown\s*=\s*([\s\S]*?);$/m,
);
if (!pricingBreakdownDeclaration) {
  fail("could not find the PricingBreakdown declaration in frontend/src/api/client.ts");
}

const pricingBreakdown = pricingBreakdownDeclaration[1];
const omittedFields = [...pricingBreakdown.matchAll(/"(productType|dueDate)"/g)].map(
  ([, field]) => field,
);
const forbiddenProperties = forbiddenBreakdownFields.filter((field) =>
  new RegExp(`\\b${field}\\s*:`).test(pricingBreakdown),
);

if (forbiddenProperties.length > 0) {
  fail(
    `PricingBreakdown must not declare quote metadata fields: ${forbiddenProperties.join(", ")}`,
  );
}

if (pricingBreakdown.startsWith("Omit<")) {
  const missingOmissions = forbiddenBreakdownFields.filter(
    (field) => !omittedFields.includes(field),
  );
  if (missingOmissions.length > 0) {
    fail(`PricingBreakdown must omit inherited quote metadata fields: ${missingOmissions.join(", ")}`);
  }
} else if (!pricingBreakdown.startsWith("{")) {
  fail("PricingBreakdown must be an object type or explicitly omit inherited quote metadata");
}

console.log("API-CONTRACT-001 passed: frontend quote shape matches OpenAPI quote boundary");

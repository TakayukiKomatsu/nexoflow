#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const openApiPath = path.resolve(
  process.env.SRM_OPENAPI_DOCUMENT
    ?? path.join(repoRoot, 'backend/build/generated/openapi/srm-openapi.json'),
);
const inventoryPath = path.resolve(
  process.env.SRM_API_INVENTORY_DOCUMENT
    ?? path.join(repoRoot, 'docs/architecture/api-endpoints.md'),
);
const httpMethods = new Set(['get', 'post', 'put', 'patch', 'delete']);

function readOpenApi(documentPath) {
  if (!fs.existsSync(documentPath)) {
    throw new Error(
      `canonical OpenAPI document is missing: ${documentPath}; run backend:exportOpenApi first`,
    );
  }
  const document = JSON.parse(fs.readFileSync(documentPath, 'utf8'));
  if (document === null || typeof document !== 'object' || document.paths === null
      || typeof document.paths !== 'object' || Array.isArray(document.paths)) {
    throw new Error(`canonical OpenAPI document has no paths object: ${documentPath}`);
  }
  return document;
}

function openApiOperations(document) {
  const operations = new Set();
  for (const [operationPath, pathItem] of Object.entries(document.paths)) {
    if (!operationPath.startsWith('/api/v1/') || pathItem === null || typeof pathItem !== 'object') {
      continue;
    }
    for (const method of Object.keys(pathItem)) {
      if (httpMethods.has(method.toLowerCase())) {
        operations.add(`${method.toUpperCase()} ${operationPath}`);
      }
    }
  }
  return operations;
}

try {
  const implemented = openApiOperations(readOpenApi(openApiPath));
  const inventory = fs.readFileSync(inventoryPath, 'utf8');
  const documented = new Set(
    [...inventory.matchAll(/`(GET|POST|PUT|PATCH|DELETE) (\/api\/v1\/[^`]+)`/g)]
      .map((match) => `${match[1]} ${match[2]}`),
  );
  const missing = [...implemented].filter((operation) => !documented.has(operation)).sort();
  const stale = [...documented].filter((operation) => !implemented.has(operation)).sort();

  if (missing.length > 0 || stale.length > 0) {
    console.error('DOC-OPENAPI-006 failed: documented endpoint inventory differs from canonical OpenAPI operations');
    for (const operation of missing) console.error(`  undocumented OpenAPI operation: ${operation}`);
    for (const operation of stale) console.error(`  stale documentation: ${operation}`);
    process.exit(1);
  }

  console.log(`DOC-OPENAPI-006 passed: ${implemented.size} canonical OpenAPI operations exactly match the documented API inventory`);
} catch (error) {
  console.error(`DOC-OPENAPI-006 failed: ${error.message}`);
  process.exit(1);
}

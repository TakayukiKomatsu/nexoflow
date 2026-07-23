#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourceRoot = path.join(repoRoot, 'backend/src/main/java/com/srm/creditengine');
const inventoryPath = path.join(repoRoot, 'docs/architecture/api-endpoints.md');

function javaFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name);
    return entry.isDirectory() ? javaFiles(target) : entry.name.endsWith('Controller.java') ? [target] : [];
  });
}

const implemented = new Set();
for (const file of javaFiles(sourceRoot)) {
  const source = fs.readFileSync(file, 'utf8');
  const classIndex = source.indexOf('class ');
  const classPrefix = classIndex >= 0 ? source.slice(0, classIndex) : '';
  const classMapping = [...classPrefix.matchAll(/@RequestMapping\("([^"]+)"\)/g)].at(-1)?.[1] ?? '';
  const methodPattern = /@(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?/g;
  for (const match of source.matchAll(methodPattern)) {
    const method = match[1].toUpperCase();
    const methodPath = match[2] ?? '';
    const endpoint = `${classMapping}${methodPath}`.replace(/\/+/, '/');
    if (endpoint.startsWith('/api/v1/')) implemented.add(`${method} ${endpoint}`);
  }
}

const inventory = fs.readFileSync(inventoryPath, 'utf8');
const documented = new Set([...inventory.matchAll(/`(GET|POST|PUT|PATCH|DELETE) (\/api\/v1\/[^`]+)`/g)].map((match) => `${match[1]} ${match[2]}`));
const missing = [...implemented].filter((endpoint) => !documented.has(endpoint)).sort();
const stale = [...documented].filter((endpoint) => !implemented.has(endpoint)).sort();

if (missing.length > 0 || stale.length > 0) {
  console.error('DOC-OPENAPI-006 failed: documented endpoint inventory differs from controller mappings');
  for (const endpoint of missing) console.error(`  undocumented implementation: ${endpoint}`);
  for (const endpoint of stale) console.error(`  stale documentation: ${endpoint}`);
  process.exit(1);
}

console.log(`DOC-OPENAPI-006 passed: ${implemented.size} controller endpoints exactly match the documented API inventory`);

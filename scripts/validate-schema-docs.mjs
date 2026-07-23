#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sqlDir = path.join(repoRoot, 'backend/src/main/resources/db/migration');
const javaDir = path.join(repoRoot, 'backend/src/main/java/db/migration');
const erPath = path.join(repoRoot, 'docs/architecture/er-diagram.mmd');
const inventoryPath = path.join(repoRoot, 'docs/architecture/schema-inventory.md');

const migrationFiles = [
  ...fs.readdirSync(sqlDir).filter((name) => name.endsWith('.sql')).map((name) => path.join(sqlDir, name)),
  ...fs.readdirSync(javaDir).filter((name) => name.endsWith('.java')).map((name) => path.join(javaDir, name)),
].sort();
const migrationText = migrationFiles.map((file) => fs.readFileSync(file, 'utf8')).join('\n').toLowerCase();
const erText = fs.readFileSync(erPath, 'utf8');
const inventoryText = fs.readFileSync(inventoryPath, 'utf8');

function splitTopLevel(definition) {
  const parts = [];
  let depth = 0;
  let start = 0;
  for (let index = 0; index < definition.length; index += 1) {
    const character = definition[index];
    if (character === '(') depth += 1;
    if (character === ')') depth -= 1;
    if (character === ',' && depth === 0) {
      parts.push(definition.slice(start, index).trim());
      start = index + 1;
    }
  }
  parts.push(definition.slice(start).trim());
  return parts.filter(Boolean);
}

const schema = new Map();
const constraintSignatures = new Set();
const createTablePattern = /create\s+table\s+([a-z_][a-z0-9_]*)\s*\(([\s\S]*?)\)\s*;/g;
for (const match of migrationText.matchAll(createTablePattern)) {
  const table = match[1];
  const columns = new Map();
  for (const definition of splitTopLevel(match[2])) {
    const columnMatch = definition.match(/^([a-z_][a-z0-9_]*)\s+/);
    const isTableConstraint = /^(constraint\s+\w+\s+)?(primary\s+key|unique|foreign\s+key|check)\b/.test(definition);
    if (columnMatch && !isTableConstraint) {
      const column = columnMatch[1];
      columns.set(column, definition);
      if (/\bprimary\s+key\b/.test(definition)) constraintSignatures.add(`${table}.primary(${column})`);
      if (/\bunique\b/.test(definition)) constraintSignatures.add(`${table}.unique(${column})`);
      const reference = definition.match(/\breferences\s+([a-z_][a-z0-9_]*)\s*\(\s*([a-z_][a-z0-9_]*)\s*\)/);
      if (reference) constraintSignatures.add(`${table}.fk(${column}->${reference[1]}.${reference[2]})`);
      if (/\bcheck\s*\(/.test(definition)) constraintSignatures.add(`${table}.check(${column})`);
      continue;
    }
    const primary = definition.match(/primary\s+key\s*\(([^)]+)\)/);
    if (primary) constraintSignatures.add(`${table}.primary(${primary[1].replace(/\s+/g, '')})`);
    const unique = definition.match(/\bunique\s*\(([^)]+)\)/);
    if (unique) constraintSignatures.add(`${table}.unique(${unique[1].replace(/\s+/g, '')})`);
    if (/\bcheck\s*\(/.test(definition)) constraintSignatures.add(`${table}.check(table)`);
  }
  schema.set(table, columns);
}

for (const match of migrationText.matchAll(/alter\s+table\s+([a-z_][a-z0-9_]*)\s+add\s+column\s+([a-z_][a-z0-9_]*)\s+([^;"\n]+)/g)) {
  const [, table, column, definition] = match;
  if (!schema.has(table)) throw new Error(`alter-table migration refers to unknown table ${table}`);
  schema.get(table).set(column, `${column} ${definition}`);
}

const erSchema = new Map();
const erBlockPattern = /^ {4}([a-z_][a-z0-9_]*) \{\n([\s\S]*?)^ {4}\}/gm;
for (const match of erText.matchAll(erBlockPattern)) {
  const attributes = new Map();
  for (const line of match[2].split('\n').map((value) => value.trim()).filter(Boolean)) {
    const [, column, ...markers] = line.split(/\s+/);
    attributes.set(column, markers.join(' '));
  }
  erSchema.set(match[1], attributes);
}

const failures = [];
for (const [table, columns] of schema) {
  const documented = erSchema.get(table);
  if (!documented) {
    failures.push(`migration table missing from ER: ${table}`);
    continue;
  }
  for (const [column, definition] of columns) {
    if (!documented.has(column)) failures.push(`migration column missing from ER: ${table}.${column}`);
    const markers = documented.get(column) ?? '';
    if (/\bprimary\s+key\b/.test(definition) && !markers.includes('PK')) failures.push(`ER lacks PK marker: ${table}.${column}`);
    if (/\bunique\b/.test(definition) && !markers.includes('UK')) failures.push(`ER lacks UK marker: ${table}.${column}`);
    if (/\breferences\b/.test(definition) && !markers.includes('FK')) failures.push(`ER lacks FK marker: ${table}.${column}`);
  }
  for (const column of documented.keys()) {
    if (!columns.has(column)) failures.push(`ER column has no migration authority: ${table}.${column}`);
  }
}
for (const table of erSchema.keys()) {
  if (!schema.has(table)) failures.push(`ER table has no migration authority: ${table}`);
}

const namedArtifacts = new Set();
for (const pattern of [
  /\bconstraint\s+([a-z_][a-z0-9_]*)/g,
  /create\s+(?:unique\s+)?index\s+([a-z_][a-z0-9_]*)/g,
  /create\s+trigger\s+([a-z_][a-z0-9_]*)/g,
]) {
  for (const match of migrationText.matchAll(pattern)) namedArtifacts.add(match[1]);
}
for (const artifact of namedArtifacts) {
  if (!inventoryText.includes(`\`${artifact}\``)) failures.push(`schema inventory omits named constraint/index/trigger: ${artifact}`);
}
for (const signature of constraintSignatures) {
  if (!inventoryText.includes(`\`${signature}\``)) failures.push(`schema inventory omits structural constraint: ${signature}`);
}
for (const file of migrationFiles) {
  const relative = path.relative(repoRoot, file);
  if (!inventoryText.includes(relative)) failures.push(`schema inventory lacks DDL/migration link: ${relative}`);
}

if (failures.length > 0) {
  console.error('DOC-SCHEMA-002 failed:');
  for (const failure of failures) console.error(`  ${failure}`);
  process.exit(1);
}

console.log(`DOC-SCHEMA-002 passed: ${schema.size} tables, migration columns, structural constraints, indexes, and Java-migration triggers match the ER inventory`);

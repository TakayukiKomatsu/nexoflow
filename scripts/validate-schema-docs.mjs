#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sqlDir = path.resolve(
  process.env.SRM_SCHEMA_SQL_DIR
    ?? path.join(repoRoot, 'backend/src/main/resources/db/migration'),
);
const javaDir = path.resolve(
  process.env.SRM_SCHEMA_JAVA_DIR
    ?? path.join(repoRoot, 'backend/src/main/java/db/migration'),
);
const erPath = path.resolve(
  process.env.SRM_SCHEMA_ER_DOCUMENT
    ?? path.join(repoRoot, 'docs/architecture/er-diagram.mmd'),
);
const inventoryPath = path.resolve(
  process.env.SRM_SCHEMA_INVENTORY_DOCUMENT
    ?? path.join(repoRoot, 'docs/architecture/schema-inventory.md'),
);

function filesWithExtension(directory, extension) {
  return fs.readdirSync(directory)
    .filter((name) => name.endsWith(extension))
    .map((name) => path.join(directory, name));
}

const sqlFiles = filesWithExtension(sqlDir, '.sql');
const javaFiles = filesWithExtension(javaDir, '.java');
const migrationFiles = [...sqlFiles, ...javaFiles].sort();

function javaExecuteArguments(source) {
  const argumentsFound = [];
  const marker = 'statement.execute(';
  let searchFrom = 0;
  while (searchFrom < source.length) {
    const markerIndex = source.indexOf(marker, searchFrom);
    if (markerIndex < 0) break;
    const start = markerIndex + marker.length;
    let depth = 1;
    let index = start;
    let mode = 'code';
    while (index < source.length && depth > 0) {
      if (mode === 'text') {
        if (source.startsWith('"""', index)) {
          mode = 'code';
          index += 3;
        } else {
          index += 1;
        }
        continue;
      }
      if (mode === 'string') {
        if (source[index] === '\\') index += 2;
        else if (source[index] === '"') {
          mode = 'code';
          index += 1;
        } else index += 1;
        continue;
      }
      if (source.startsWith('"""', index)) {
        mode = 'text';
        index += 3;
      } else if (source[index] === '"') {
        mode = 'string';
        index += 1;
      } else if (source[index] === '(') {
        depth += 1;
        index += 1;
      } else if (source[index] === ')') {
        depth -= 1;
        index += 1;
      } else {
        index += 1;
      }
    }
    if (depth !== 0) throw new Error('unterminated statement.execute(...) in Java migration');
    argumentsFound.push(source.slice(start, index - 1));
    searchFrom = index;
  }
  return argumentsFound;
}

function javaLiteralValues(expression) {
  const values = [];
  let index = 0;
  while (index < expression.length) {
    if (expression.startsWith('"""', index)) {
      const end = expression.indexOf('"""', index + 3);
      if (end < 0) throw new Error('unterminated Java text block in migration');
      values.push(expression.slice(index + 3, end));
      index = end + 3;
    } else if (expression[index] === '"') {
      let end = index + 1;
      while (end < expression.length) {
        if (expression[end] === '\\') end += 2;
        else if (expression[end] === '"') break;
        else end += 1;
      }
      values.push(JSON.parse(expression.slice(index, end + 1)));
      index = end + 1;
    } else {
      index += 1;
    }
  }
  return values;
}

function javaStringVariables(source) {
  const variables = new Map();
  for (const match of source.matchAll(/\bString\s+([a-z_$][a-z0-9_$]*)\s*=\s*([\s\S]*?);/gi)) {
    const values = javaLiteralValues(match[2]);
    if (values.length === 1) variables.set(match[1], values[0]);
    if (values.length > 1) variables.set(match[1], `[${values.join('|')}]`);
  }
  return variables;
}

function javaStringExpression(argument, variables) {
  const pieces = [];
  let index = 0;
  while (index < argument.length) {
    if (/\s|\+/.test(argument[index])) {
      index += 1;
      continue;
    }
    if (argument.startsWith('"""', index)) {
      const end = argument.indexOf('"""', index + 3);
      if (end < 0) throw new Error('unterminated Java text block in migration');
      pieces.push(argument.slice(index + 3, end));
      index = end + 3;
      continue;
    }
    if (argument[index] === '"') {
      let end = index + 1;
      while (end < argument.length) {
        if (argument[end] === '\\') end += 2;
        else if (argument[end] === '"') break;
        else end += 1;
      }
      pieces.push(JSON.parse(argument.slice(index, end + 1)));
      index = end + 1;
      continue;
    }
    const identifier = argument.slice(index).match(/^[a-z_$][a-z0-9_$]*/i)?.[0];
    if (identifier) {
      pieces.push(` ${variables.get(identifier) ?? identifier} `);
      index += identifier.length;
      continue;
    }
    index += 1;
  }
  return pieces.join('');
}

const sqlText = sqlFiles.map((file) => fs.readFileSync(file, 'utf8')).join('\n');
const javaSqlText = javaFiles.flatMap((file) => {
  const source = fs.readFileSync(file, 'utf8');
  const variables = javaStringVariables(source);
  return javaExecuteArguments(source).map((argument) => `${javaStringExpression(argument, variables)};`);
}).join('\n');
const migrationText = `${sqlText}\n${javaSqlText}`.toLowerCase();
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

function normalizedColumns(columns) {
  return columns.split(',').map((column) => column.trim()).join(',');
}

function normalizedExpression(expression) {
  return expression.replace(/\s+/g, '');
}

const schema = new Map();
const constraintSignatures = new Set();

function recordColumnConstraints(table, column, definition) {
  if (/\bprimary\s+key\b/.test(definition)) constraintSignatures.add(`${table}.primary(${column})`);
  if (/\bunique\b/.test(definition)) constraintSignatures.add(`${table}.unique(${column})`);
  const reference = definition.match(/\breferences\s+([a-z_][a-z0-9_]*)\s*\(\s*([a-z_][a-z0-9_]*)\s*\)/);
  if (reference) constraintSignatures.add(`${table}.fk(${column}->${reference[1]}.${reference[2]})`);
  if (/\bcheck\s*\(/.test(definition)) constraintSignatures.add(`${table}.check(${column})`);
}

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
      recordColumnConstraints(table, column, definition);
      continue;
    }
    const primary = definition.match(/primary\s+key\s*\(([^)]+)\)/);
    if (primary) constraintSignatures.add(`${table}.primary(${normalizedColumns(primary[1])})`);
    const unique = definition.match(/\bunique\s*\(([^)]+)\)/);
    if (unique) constraintSignatures.add(`${table}.unique(${normalizedColumns(unique[1])})`);
    if (/\bcheck\s*\(/.test(definition)) constraintSignatures.add(`${table}.check(table)`);
  }
  schema.set(table, columns);
}

for (const match of migrationText.matchAll(/alter\s+table\s+([a-z_][a-z0-9_]*)\s+add\s+column\s+([a-z_][a-z0-9_]*)\s+([\s\S]*?);/g)) {
  const [, table, column, definition] = match;
  if (!schema.has(table)) throw new Error(`alter-table migration refers to unknown table ${table}`);
  schema.get(table).set(column, `${column} ${definition}`);
  recordColumnConstraints(table, column, definition);
}

for (const match of migrationText.matchAll(/alter\s+table\s+([a-z_][a-z0-9_]*)\s+add\s+constraint\s+([a-z_][a-z0-9_]*)\s+([\s\S]*?);/g)) {
  const [, table, , definition] = match;
  const foreignKey = definition.match(/foreign\s+key\s*\(([^)]+)\)\s+references\s+([a-z_][a-z0-9_]*)\s*\(([^)]+)\)/);
  if (foreignKey) {
    constraintSignatures.add(
      `${table}.fk(${normalizedColumns(foreignKey[1])}->${foreignKey[2]}.${normalizedColumns(foreignKey[3])})`,
    );
  }
  const unique = definition.match(/^\s*unique\s*\(([^)]+)\)/);
  if (unique) constraintSignatures.add(`${table}.unique(${normalizedColumns(unique[1])})`);
  const check = definition.match(/^\s*check\s*\(([\s\S]*)\)\s*$/);
  if (check) constraintSignatures.add(`${table}.check(${normalizedExpression(check[1])})`);
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

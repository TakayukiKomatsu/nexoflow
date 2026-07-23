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
const migrationFiles = [...sqlFiles, ...javaFiles].sort((left, right) => {
  const version = (file) => Number.parseInt(path.basename(file).match(/^V(\d+)__/)?.[1] ?? '0', 10);
  return version(left) - version(right) || left.localeCompare(right);
});

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

function javaLoopGeneratedAlterColumnStatements(source) {
  const columnsBlock = source.match(
    /List<String\[\]>\s+columns\s*=\s*List\.of\(([\s\S]*?)\);/,
  )?.[1];
  const alteration = source.match(
    /String\s+alteration\s*=\s*[\s\S]*?\?\s*" alter column "\s*\+\s*column\[1\]\s*\+\s*" type ([^"]+)"\s*:\s*" alter column "\s*\+\s*column\[1\]\s*\+\s*" ([^"]+)"/,
  );
  const executesGeneratedAlteration = /statement\.execute\(\s*"alter table "\s*\+\s*column\[0\]\s*\+\s*alteration\s*\)/.test(
    source,
  );
  if (!columnsBlock || !alteration || !executesGeneratedAlteration) return [];

  const postgresType = alteration[1].trim().toLowerCase();
  const fallbackType = alteration[2].trim().toLowerCase();
  if (postgresType !== fallbackType) {
    throw new Error(
      `generated alter-column branches disagree: ${postgresType} versus ${fallbackType}`,
    );
  }

  return [...columnsBlock.matchAll(
    /new\s+String\[\]\s*\{\s*"([a-z_][a-z0-9_]*)"\s*,\s*"([a-z_][a-z0-9_]*)"\s*\}/gi,
  )].map(
    ([, table, column]) => `alter table ${table} alter column ${column} type ${postgresType};`,
  );
}

const migrationText = migrationFiles.map((file) => {
  if (file.endsWith('.sql')) return fs.readFileSync(file, 'utf8');
  const source = fs.readFileSync(file, 'utf8');
  const variables = javaStringVariables(source);
  const literalStatements = javaExecuteArguments(source)
    .map((argument) => `${javaStringExpression(argument, variables)};`)
    .join('\n');
  return [
    literalStatements,
    ...javaLoopGeneratedAlterColumnStatements(source),
  ].join('\n');
}).join('\n').toLowerCase();
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
const namedConstraintEvents = [];

function sqlColumnType(definition) {
  const type = definition.replace(/^[a-z_][a-z0-9_]*\s+/, '').trim();
  const numeric = type.match(/^numeric\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)/);
  if (numeric) {
    return {
      inventory: `numeric(${numeric[1]},${numeric[2]})`,
      er: `decimal_${numeric[1]}_${numeric[2]}`,
      financial: true,
    };
  }
  if (/^double\s+precision\b/.test(type)) return { inventory: 'double precision', er: 'double_precision' };
  if (/^bigint\b/.test(type)) return { inventory: 'bigint', er: 'bigint' };
  if (/^(?:integer|int)\b/.test(type)) return { inventory: 'integer', er: 'int' };
  if (/^uuid\b/.test(type)) return { inventory: 'uuid', er: 'uuid' };
  if (/^boolean\b/.test(type)) return { inventory: 'boolean', er: 'boolean' };
  const timestamp = type.match(
    /^(timestamp|timestamptz)(?:\s*\(\s*(\d+)\s*\))?(?:\s+(with|without)\s+time\s+zone)?(?=\s|$)/,
  );
  if (timestamp) {
    const timezone = timestamp[1] === 'timestamptz' || timestamp[3] === 'with';
    return timezone
      ? { inventory: 'timestamp with time zone', er: 'timestamptz' }
      : { inventory: 'timestamp without time zone', er: 'timestamp' };
  }
  if (/^date\b/.test(type)) return { inventory: 'date', er: 'date' };
  const varchar = type.match(
    /^(?:varchar|character\s+varying)(?:\s*\(\s*(\d+)\s*\))?(?=\s|$)/,
  );
  if (varchar) {
    return varchar[1]
      ? { inventory: `varchar(${varchar[1]})`, er: `varchar_${varchar[1]}` }
      : { inventory: 'varchar', er: 'varchar' };
  }
  const character = type.match(
    /^(?:char|character)(?:\s*\(\s*(\d+)\s*\))?(?=\s|$)/,
  );
  if (character) {
    const length = character[1] ?? '1';
    return { inventory: `char(${length})`, er: `char_${length}` };
  }
  if (/^text\b/.test(type)) return { inventory: 'text', er: 'text' };
  if (/^(?:json|jsonb)\b/.test(type)) return { inventory: 'json', er: 'json' };
  return null;
}

function columnConstraintSignatures(table, column, definition) {
  const signatures = [];
  if (/\bprimary\s+key\b/.test(definition)) signatures.push(`${table}.primary(${column})`);
  if (/\bunique\b/.test(definition)) signatures.push(`${table}.unique(${column})`);
  const reference = definition.match(/\breferences\s+([a-z_][a-z0-9_]*)\s*\(\s*([a-z_][a-z0-9_]*)\s*\)/);
  if (reference) signatures.push(`${table}.fk(${column}->${reference[1]}.${reference[2]})`);
  const check = definition.match(/\bcheck\s*\(([\s\S]*)\)\s*$/);
  if (check) signatures.push(`${table}.check(${normalizedExpression(check[1])})`);
  return signatures;
}

function tableConstraintSignature(table, definition) {
  const primary = definition.match(/primary\s+key\s*\(([^)]+)\)/);
  if (primary) return `${table}.primary(${normalizedColumns(primary[1])})`;
  const unique = definition.match(/\bunique\s*\(([^)]+)\)/);
  if (unique) return `${table}.unique(${normalizedColumns(unique[1])})`;
  const foreignKey = definition.match(/foreign\s+key\s*\(([^)]+)\)\s+references\s+([a-z_][a-z0-9_]*)\s*\(([^)]+)\)/);
  if (foreignKey) {
    return `${table}.fk(${normalizedColumns(foreignKey[1])}->${foreignKey[2]}.${normalizedColumns(foreignKey[3])})`;
  }
  const check = definition.match(/\bcheck\s*\(([\s\S]*)\)\s*$/);
  if (check) return `${table}.check(${normalizedExpression(check[1])})`;
  return null;
}

function recordColumnConstraints(table, column, definition) {
  for (const signature of columnConstraintSignatures(table, column, definition)) {
    constraintSignatures.add(signature);
  }
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
    const signature = tableConstraintSignature(table, definition);
    if (signature) constraintSignatures.add(signature);
    const named = definition.match(/^constraint\s+([a-z_][a-z0-9_]*)\s+/);
    if (named && signature) {
      namedConstraintEvents.push({ index: match.index, table, name: named[1], signature });
    }
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
  const [, table, name, definition] = match;
  const signature = tableConstraintSignature(table, definition);
  if (signature) {
    constraintSignatures.add(signature);
    namedConstraintEvents.push({ index: match.index, table, name, signature });
  }
}

for (const match of migrationText.matchAll(/alter\s+table\s+([a-z_][a-z0-9_]*)\s+drop\s+constraint\s+(?:if\s+exists\s+)?([a-z_][a-z0-9_]*)\s*;/g)) {
  namedConstraintEvents.push({ index: match.index, table: match[1], name: match[2], signature: null });
}

const historicalNamedSignatures = new Set(
  namedConstraintEvents.map(({ signature }) => signature).filter(Boolean),
);
for (const signature of historicalNamedSignatures) constraintSignatures.delete(signature);
const finalNamedConstraints = new Map();
for (const event of namedConstraintEvents.sort((left, right) => left.index - right.index)) {
  const key = `${event.table}.${event.name}`;
  if (event.signature) finalNamedConstraints.set(key, event.signature);
  else finalNamedConstraints.delete(key);
}
for (const signature of finalNamedConstraints.values()) constraintSignatures.add(signature);

function replaceColumnType(definition, nextType) {
  const column = definition.match(/^([a-z_][a-z0-9_]*)\s+/)?.[1];
  if (!column) return definition;
  const remainder = definition.slice(column.length).trim();
  const constraintOffset = remainder.search(
    /\s+(?=(?:not\s+null|null|primary\s+key|unique|references|check|default)\b)/,
  );
  const constraints = constraintOffset < 0 ? '' : remainder.slice(constraintOffset);
  return `${column} ${nextType.trim()}${constraints}`;
}

for (const match of migrationText.matchAll(/alter\s+table\s+([a-z_][a-z0-9_]*)\s+alter\s+column\s+([a-z_][a-z0-9_]*)\s+(?:set\s+data\s+)?type\s+([\s\S]*?);/g)) {
  const [, table, column, nextTypeWithUsing] = match;
  const columns = schema.get(table);
  if (!columns?.has(column)) throw new Error(`alter-column migration refers to unknown column ${table}.${column}`);
  const nextType = nextTypeWithUsing.replace(/\s+using\s+[\s\S]*$/i, '');
  columns.set(column, replaceColumnType(columns.get(column), nextType));
}

const nullabilityEvents = [];
for (const match of migrationText.matchAll(/alter\s+table\s+([a-z_][a-z0-9_]*)\s+alter\s+column\s+([a-z_][a-z0-9_]*)\s+(set|drop)\s+not\s+null\s*;/g)) {
  nullabilityEvents.push({ index: match.index, table: match[1], column: match[2], action: match[3] });
}
for (const event of nullabilityEvents.sort((left, right) => left.index - right.index)) {
  const columns = schema.get(event.table);
  if (!columns?.has(event.column)) {
    throw new Error(`alter-column migration refers to unknown column ${event.table}.${event.column}`);
  }
  let definition = columns.get(event.column).replace(/\s+not\s+null\b/gi, '');
  if (event.action === 'set') definition = `${definition} not null`;
  columns.set(event.column, definition);
}

const financialColumnSignatures = new Set();
for (const [table, columns] of schema) {
  for (const [column, definition] of columns) {
    const type = sqlColumnType(definition);
    if (!type?.financial) continue;
    const nullability = /\b(?:not\s+null|primary\s+key)\b/.test(definition) ? 'not-null' : 'nullable';
    financialColumnSignatures.add(`${table}.numeric(${column}:${type.inventory}:${nullability})`);
  }
}

const erSchema = new Map();
const erBlockPattern = /^ {4}([a-z_][a-z0-9_]*) \{\n([\s\S]*?)^ {4}\}/gm;
for (const match of erText.matchAll(erBlockPattern)) {
  const attributes = new Map();
  for (const line of match[2].split('\n').map((value) => value.trim()).filter(Boolean)) {
    const [type, column, ...markers] = line.split(/\s+/);
    attributes.set(column, { type, markers: markers.join(' ') });
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
    const attribute = documented.get(column);
    const markers = attribute?.markers ?? '';
    const migrationType = sqlColumnType(definition);
    if (attribute && migrationType && attribute.type !== migrationType.er) {
      failures.push(
        `ER type mismatch: ${table}.${column} migration ${migrationType.er}, ER ${attribute.type}`,
      );
    }
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

const namedArtifacts = new Set(
  [...finalNamedConstraints.keys()].map((key) => key.slice(key.indexOf('.') + 1)),
);
const artifactEvents = [];
for (const match of migrationText.matchAll(/create\s+(?:unique\s+)?index\s+([a-z_][a-z0-9_]*)/g)) {
  artifactEvents.push({ index: match.index, name: match[1], present: true });
}
for (const match of migrationText.matchAll(/drop\s+index\s+(?:if\s+exists\s+)?([a-z_][a-z0-9_]*)/g)) {
  artifactEvents.push({ index: match.index, name: match[1], present: false });
}
for (const match of migrationText.matchAll(/create\s+trigger\s+([a-z_][a-z0-9_]*)/g)) {
  artifactEvents.push({ index: match.index, name: match[1], present: true });
}
for (const match of migrationText.matchAll(/drop\s+trigger\s+(?:if\s+exists\s+)?([a-z_][a-z0-9_]*)/g)) {
  artifactEvents.push({ index: match.index, name: match[1], present: false });
}
for (const event of artifactEvents.sort((left, right) => left.index - right.index)) {
  if (event.present) namedArtifacts.add(event.name);
  else namedArtifacts.delete(event.name);
}
for (const artifact of namedArtifacts) {
  if (!inventoryText.includes(`\`${artifact}\``)) failures.push(`schema inventory omits named constraint/index/trigger: ${artifact}`);
}
for (const section of inventoryText.matchAll(
  /^(?:- )?(?:Named constraints|Constraints|Indexes|PostgreSQL triggers):\s*([\s\S]*?)(?=\n\s*\n|(?![\s\S]))/gm,
)) {
  for (const documented of section[1].matchAll(/`([a-z_][a-z0-9_]*)`/g)) {
    if (!namedArtifacts.has(documented[1])) {
      failures.push(`schema inventory documents absent named constraint/index/trigger: ${documented[1]}`);
    }
  }
}
for (const signature of constraintSignatures) {
  if (!inventoryText.includes(`\`${signature}\``)) failures.push(`schema inventory omits structural constraint: ${signature}`);
}
for (const match of inventoryText.matchAll(/`([a-z_][a-z0-9_]*\.(?:primary|unique|fk|check)\([^`]+\))`/g)) {
  const signature = match[1];
  const table = signature.slice(0, signature.indexOf('.'));
  if (schema.has(table) && !constraintSignatures.has(signature)) {
    failures.push(`schema inventory documents absent structural constraint: ${signature}`);
  }
}
for (const signature of financialColumnSignatures) {
  if (!inventoryText.includes(`\`${signature}\``)) failures.push(`schema inventory omits financial column: ${signature}`);
}
for (const match of inventoryText.matchAll(/`([a-z_][a-z0-9_]*\.numeric\([^`]+\))`/g)) {
  const signature = match[1];
  const table = signature.slice(0, signature.indexOf('.'));
  if (schema.has(table) && !financialColumnSignatures.has(signature)) {
    failures.push(`schema inventory documents absent financial column: ${signature}`);
  }
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

console.log(`DOC-SCHEMA-002 passed: ${schema.size} tables, exact ER textual/temporal types, exact financial numeric storage/nullability, structural constraints, indexes, and Java-migration triggers match the inventory`);

#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, "..");
const defaultTemplate = resolve(
  repositoryRoot,
  "backend/src/main/resources/sql/settlement-statement.sql",
);
const templateArgument = process.argv.indexOf("--template");
const templatePath =
  process.env.SRM_STATEMENT_SQL_TEMPLATE ??
  (templateArgument >= 0 ? process.argv[templateArgument + 1] : defaultTemplate);

if (!templatePath) {
  fail("--template requires a path");
}

const requiredMarkers = [
  "from",
  "to",
  "assignorId",
  "assetCurrency",
  "settlementCurrency",
  "productType",
];
const template = readFileSync(templatePath, "utf8");
validateTemplate(template);

const timeWindow = {
  from: "timestamp '2030-01-10 00:00:00'",
  to: "timestamp '2030-01-20 00:00:00'",
};
const cases = [
  {
    name: "baseline",
    values: timeWindow,
  },
  {
    name: "assignor",
    values: {
      ...timeWindow,
      assignorId: "md5('rep:assignor:1')::uuid",
    },
  },
  {
    name: "asset_currency",
    values: {
      ...timeWindow,
      assetCurrency: "'USD'",
    },
  },
  {
    name: "settlement_currency",
    values: {
      ...timeWindow,
      settlementCurrency: "'USD'",
    },
  },
  {
    name: "product_type",
    values: {
      ...timeWindow,
      productType: "'POST_DATED_CHEQUE'",
    },
  },
  {
    name: "combined",
    values: {
      ...timeWindow,
      assignorId: "md5('rep:assignor:1')::uuid",
      assetCurrency: "'BRL'",
      settlementCurrency: "'USD'",
      productType: "'POST_DATED_CHEQUE'",
    },
  },
];

const output = [
  "-- Generated from the production-owned settlement-statement SQL template.",
  `-- Template: ${relativeDisplayPath(templatePath)}`,
  "-- Every count and EXPLAIN below renders that template; no query body is copied here.",
  "\\pset format unaligned",
  "\\pset tuples_only on",
];

for (const evidenceCase of cases) {
  const countQuery = render(template, {
    ...evidenceCase.values,
    limit: "20000",
    offset: "0",
  });
  const explainQuery = render(template, {
    ...evidenceCase.values,
    limit: "51",
    offset: "0",
  });
  output.push(
    "",
    `\\echo SRM_FILTER_CASE|${evidenceCase.name}`,
    `select 'SRM_FILTER_COUNT|${evidenceCase.name}|' || count(*)::text`,
    "from (",
    indent(countQuery),
    ") evidence_rows;",
    `\\echo SRM_PLAN_CASE|${evidenceCase.name}`,
    "explain (analyze, buffers, settings, wal, format text)",
    `${explainQuery};`,
  );
}

process.stdout.write(`${output.join("\n")}\n`);

function validateTemplate(value) {
  const found = [];
  for (const line of value.split(/\r?\n/)) {
    const match = line.match(/^\s*\/\*\?([A-Za-z][A-Za-z0-9]*)\*\/\s*(.+)$/);
    if (!match) continue;
    if (found.includes(match[1])) {
      fail(`duplicate SQL template filter marker: ${match[1]}`);
    }
    found.push(match[1]);
    requireOccurrences(match[2], `:${match[1]}`, 1);
  }
  if (
    found.length !== requiredMarkers.length ||
    found.some((marker, index) => marker !== requiredMarkers[index])
  ) {
    fail(
      `SQL template filter markers must be exactly ${requiredMarkers.join(", ")}; ` +
        `found ${found.join(", ")}`,
    );
  }
  requireOccurrences(value, ":limit", 1);
  requireOccurrences(value, ":offset", 1);
  const allowedParameters = new Set([...requiredMarkers, "limit", "offset"]);
  for (const match of value.matchAll(/(?<!:):([A-Za-z][A-Za-z0-9]*)/g)) {
    if (!allowedParameters.has(match[1])) {
      fail(`unknown SQL template parameter: :${match[1]}`);
    }
  }
}

function render(value, parameters) {
  const renderedLines = [];
  for (const line of value.split(/\r?\n/)) {
    const match = line.match(/^\s*\/\*\?([A-Za-z][A-Za-z0-9]*)\*\/\s*(.+)$/);
    if (!match) {
      renderedLines.push(line);
      continue;
    }
    const [, key, clause] = match;
    if (Object.hasOwn(parameters, key)) {
      renderedLines.push(replaceRequired(clause, `:${key}`, parameters[key]));
    }
  }
  let rendered = replaceRequired(
    renderedLines.join("\n"),
    ":limit",
    parameters.limit,
  );
  rendered = replaceRequired(rendered, ":offset", parameters.offset);
  for (const parameter of [...requiredMarkers, "limit", "offset"]) {
    if (rendered.includes(`:${parameter}`)) {
      fail(`unresolved SQL template parameter: :${parameter}`);
    }
  }
  return rendered.trim();
}

function replaceRequired(value, target, replacement) {
  requireOccurrences(value, target, 1);
  return value.replace(target, replacement);
}

function requireOccurrences(value, target, expected) {
  const actual = value.split(target).length - 1;
  if (actual !== expected) {
    fail(
      `SQL template must contain ${target} exactly ${expected} time(s); found ${actual}`,
    );
  }
}

function indent(value) {
  return value
    .split("\n")
    .map((line) => `    ${line}`)
    .join("\n");
}

function relativeDisplayPath(value) {
  const normalizedRoot = `${repositoryRoot}/`;
  return value.startsWith(normalizedRoot)
    ? value.slice(normalizedRoot.length)
    : value;
}

function fail(message) {
  process.stderr.write(`REPORT-SQL-001 failed: ${message}\n`);
  process.exit(1);
}

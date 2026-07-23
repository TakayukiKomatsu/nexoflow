import { readdirSync, readFileSync } from "node:fs";
import { basename, dirname, extname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const financialFields = new Set([
  "faceAmount",
  "baseRate",
  "spread",
  "termInMonths",
  "discountedAmount",
  "fxRate",
  "settlementAmount",
]);
const arithmeticOperators = new Set([
  ts.SyntaxKind.PlusToken,
  ts.SyntaxKind.MinusToken,
  ts.SyntaxKind.AsteriskToken,
  ts.SyntaxKind.SlashToken,
  ts.SyntaxKind.PercentToken,
  ts.SyntaxKind.AsteriskAsteriskToken,
]);
const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const defaultSourceRoot = resolve(scriptDirectory, "../src");

function sourceRootFromArguments(arguments_) {
  if (arguments_.length === 0) return defaultSourceRoot;
  if (
    arguments_.length !== 2 ||
    arguments_[0] !== "--source-root" ||
    !arguments_[1]
  ) {
    throw new Error(
      "AUTHORITY-001 usage: validate-authoritative-pricing [--source-root PATH]",
    );
  }
  return resolve(arguments_[1]);
}

function isProductionTypeScript(fileName) {
  const extension = extname(fileName);
  if (extension !== ".ts" && extension !== ".tsx") return false;
  const name = basename(fileName);
  return (
    !name.endsWith(".d.ts") &&
    !name.includes(".test.") &&
    !name.includes(".spec.") &&
    name !== "setup.ts" &&
    name !== "setup.tsx"
  );
}

function productionSourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) => {
      const path = resolve(directory, entry.name);
      if (entry.isDirectory()) {
        return ["test", "tests", "__tests__"].includes(entry.name)
          ? []
          : productionSourceFiles(path);
      }
      return entry.isFile() && isProductionTypeScript(path) ? [path] : [];
    })
    .sort();
}

const sourceFiles = productionSourceFiles(
  sourceRootFromArguments(process.argv.slice(2)),
);

function accessesFinancialField(node) {
  let found = false;

  function visit(descendant) {
    if (
      ts.isPropertyAccessExpression(descendant) &&
      financialFields.has(descendant.name.text)
    ) {
      found = true;
      return;
    }
    if (
      ts.isElementAccessExpression(descendant) &&
      ts.isStringLiteral(descendant.argumentExpression) &&
      financialFields.has(descendant.argumentExpression.text)
    ) {
      found = true;
      return;
    }
    ts.forEachChild(descendant, visit);
  }

  visit(node);
  return found;
}

const violations = [];
for (const fileName of sourceFiles) {
  const source = readFileSync(fileName, "utf8");
  const sourceFile = ts.createSourceFile(
    fileName,
    source,
    ts.ScriptTarget.Latest,
    true,
    fileName.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  );

  function visit(node) {
    if (
      ts.isBinaryExpression(node) &&
      arithmeticOperators.has(node.operatorToken.kind) &&
      (accessesFinancialField(node.left) || accessesFinancialField(node.right))
    ) {
      const { line, character } = sourceFile.getLineAndCharacterOfPosition(
        node.getStart(sourceFile),
      );
      violations.push({
        fileName,
        line: line + 1,
        column: character + 1,
        expression: node.getText(sourceFile),
      });
    }
    ts.forEachChild(node, visit);
  }

  visit(sourceFile);
}

if (violations.length > 0) {
  for (const { fileName, line, column, expression } of violations) {
    console.error(
      `AUTHORITY-001 violation: ${fileName}:${line}:${column}: ${expression}`,
    );
  }
  process.exitCode = 1;
} else {
  console.log("AUTHORITY-001 passed: no browser-side financial arithmetic");
}

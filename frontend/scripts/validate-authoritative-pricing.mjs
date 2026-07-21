import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
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
const sourceFiles = ["src/App.tsx", "src/api/client.ts"].map((path) =>
  resolve(scriptDirectory, "..", path),
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

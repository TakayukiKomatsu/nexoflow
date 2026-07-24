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
  ts.SyntaxKind.PlusEqualsToken,
  ts.SyntaxKind.MinusEqualsToken,
  ts.SyntaxKind.AsteriskEqualsToken,
  ts.SyntaxKind.SlashEqualsToken,
  ts.SyntaxKind.PercentEqualsToken,
]);
const numericConversions = new Set([
  "Number",
  "parseFloat",
  "parseInt",
  "BigInt",
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

  const taintedIdentifiers = new Set();

  function expressionIsTainted(node) {
    if (!node) return false;
    if (ts.isIdentifier(node)) return taintedIdentifiers.has(node.text);
    if (
      ts.isPropertyAccessExpression(node) &&
      financialFields.has(node.name.text)
    ) {
      return true;
    }
    if (
      ts.isElementAccessExpression(node) &&
      ts.isStringLiteral(node.argumentExpression) &&
      financialFields.has(node.argumentExpression.text)
    ) {
      return true;
    }
    if (
      ts.isParenthesizedExpression(node) ||
      ts.isAsExpression(node) ||
      ts.isTypeAssertionExpression(node) ||
      ts.isNonNullExpression(node)
    ) {
      return expressionIsTainted(node.expression);
    }
    if (
      ts.isCallExpression(node) &&
      ts.isIdentifier(node.expression) &&
      numericConversions.has(node.expression.text)
    ) {
      return node.arguments.some(expressionIsTainted);
    }
    if (ts.isBinaryExpression(node)) {
      return expressionIsTainted(node.left) || expressionIsTainted(node.right);
    }
    if (ts.isConditionalExpression(node)) {
      return (
        expressionIsTainted(node.whenTrue) ||
        expressionIsTainted(node.whenFalse)
      );
    }
    return false;
  }

  let discoveredAlias = true;
  while (discoveredAlias) {
    discoveredAlias = false;
    function discover(node) {
      if (ts.isBindingElement(node) && ts.isIdentifier(node.name)) {
        const sourceName =
          node.propertyName && ts.isIdentifier(node.propertyName)
            ? node.propertyName.text
            : node.name.text;
        if (
          financialFields.has(sourceName) &&
          !taintedIdentifiers.has(node.name.text)
        ) {
          taintedIdentifiers.add(node.name.text);
          discoveredAlias = true;
        }
      }
      if (
        ts.isVariableDeclaration(node) &&
        ts.isIdentifier(node.name) &&
        expressionIsTainted(node.initializer) &&
        !taintedIdentifiers.has(node.name.text)
      ) {
        taintedIdentifiers.add(node.name.text);
        discoveredAlias = true;
      }
      if (
        ts.isBinaryExpression(node) &&
        node.operatorToken.kind === ts.SyntaxKind.EqualsToken &&
        ts.isIdentifier(node.left) &&
        expressionIsTainted(node.right) &&
        !taintedIdentifiers.has(node.left.text)
      ) {
        taintedIdentifiers.add(node.left.text);
        discoveredAlias = true;
      }
      if (
        ts.isParameter(node) &&
        ts.isIdentifier(node.name) &&
        financialFields.has(node.name.text) &&
        !taintedIdentifiers.has(node.name.text)
      ) {
        taintedIdentifiers.add(node.name.text);
        discoveredAlias = true;
      }
      ts.forEachChild(node, discover);
    }
    discover(sourceFile);
  }

  function recordViolation(node) {
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

  function visit(node) {
    if (
      ts.isBinaryExpression(node) &&
      arithmeticOperators.has(node.operatorToken.kind) &&
      (expressionIsTainted(node.left) ||
        expressionIsTainted(node.right) ||
        accessesFinancialField(node.left) ||
        accessesFinancialField(node.right))
    ) {
      recordViolation(node);
    } else if (
      ts.isCallExpression(node) &&
      ((ts.isIdentifier(node.expression) &&
        numericConversions.has(node.expression.text)) ||
        (ts.isPropertyAccessExpression(node.expression) &&
          ts.isIdentifier(node.expression.expression) &&
          node.expression.expression.text === "Math")) &&
      node.arguments.some(expressionIsTainted)
    ) {
      recordViolation(node);
    } else if (
      (ts.isPrefixUnaryExpression(node) || ts.isPostfixUnaryExpression(node)) &&
      [
        ts.SyntaxKind.PlusToken,
        ts.SyntaxKind.MinusToken,
        ts.SyntaxKind.PlusPlusToken,
        ts.SyntaxKind.MinusMinusToken,
      ].includes(node.operator) &&
      expressionIsTainted(node.operand)
    ) {
      recordViolation(node);
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

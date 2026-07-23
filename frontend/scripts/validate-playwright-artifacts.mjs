import { readdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const defaultRoots = [
  resolve(scriptDirectory, "../playwright-report"),
  resolve(scriptDirectory, "../test-results"),
];
const redactedArtifact =
  "Artifact removed: sensitive test evidence was detected.\n";
const sensitivePatterns = [
  {
    label: "authorization credential",
    expression:
      /\bauthorization\b["']?\s*[:=]\s*["']?(?:bearer\s+)?[A-Za-z0-9._~+/=-]{12,}/i,
  },
  {
    label: "JSON web token",
    expression: /\beyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/,
  },
  {
    label: "password credential",
    expression:
      /\b(?:[A-Z0-9_]*PASSWORD|password)\b["']?\s*[:=]\s*["'`]?(?!<redacted>|\*{3})[^\s"'`,;<>]{6,}/i,
  },
  {
    label: "idempotency credential",
    expression:
      /\bidempotency-key\b["']?\s*[:=]\s*["']?[A-Za-z0-9._~+/=-]{8,}/i,
  },
];

function parseRoots(arguments_) {
  if (arguments_.length === 0) return defaultRoots;
  const roots = [];
  for (let index = 0; index < arguments_.length; index += 2) {
    if (arguments_[index] !== "--root" || !arguments_[index + 1]) {
      throw new Error("usage: validate-playwright-artifacts [--root PATH ...]");
    }
    roots.push(resolve(arguments_[index + 1]));
  }
  return roots;
}

async function filesUnder(root) {
  let entries;
  try {
    entries = await readdir(root, { withFileTypes: true });
  } catch (cause) {
    if (cause?.code === "ENOENT") return [];
    throw cause;
  }
  const paths = await Promise.all(
    entries.map(async (entry) => {
      const path = resolve(root, entry.name);
      if (entry.isDirectory()) return filesUnder(path);
      return entry.isFile() ? [path] : [];
    }),
  );
  return paths.flat();
}

async function scan(roots) {
  const files = (await Promise.all(roots.map(filesUnder))).flat();
  const findings = [];
  for (const file of files) {
    const contents = (await readFile(file)).toString("utf8");
    const labels = sensitivePatterns
      .filter(({ expression }) => expression.test(contents))
      .map(({ label }) => label);
    if (labels.length) findings.push({ file, labels });
  }
  if (findings.length) {
    await Promise.all(
      findings.map(({ file }) => writeFile(file, redactedArtifact, "utf8")),
    );
    for (const { file, labels } of findings) {
      console.error(
        `E2E-EVIDENCE-001 blocked ${labels.join(", ")} in ${file}; artifact redacted`,
      );
    }
    process.exitCode = 1;
    return;
  }
  console.log(`E2E-EVIDENCE-001 passed: scanned ${files.length} artifacts`);
}

try {
  await scan(parseRoots(process.argv.slice(2)));
} catch (cause) {
  console.error(
    `E2E-EVIDENCE-001 failed: ${cause instanceof Error ? cause.message : String(cause)}`,
  );
  process.exitCode = 1;
}

import { readFile } from "node:fs/promises";

const [reportPath, policyPath] = process.argv.slice(2);
const report = JSON.parse(await readFile(reportPath, "utf8"));
const policy = JSON.parse(await readFile(policyPath, "utf8"));
const allowed = new Set(policy.allowed);
const rejected = Object.entries(report)
  .filter(([, metadata]) => !String(metadata.licenses ?? "UNKNOWN")
    .split(/\s+OR\s+|\s+AND\s+/)
    .every((license) => allowed.has(license.replace(/[()]/g, ""))))
  .map(([name, metadata]) => `${name}: ${metadata.licenses ?? "UNKNOWN"}`);

if (rejected.length) {
  console.error(`Disallowed production licenses:\n${rejected.join("\n")}`);
  process.exit(1);
}
console.log(`Frontend production licenses approved: ${Object.keys(report).length}`);

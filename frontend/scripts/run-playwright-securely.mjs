import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const frontendDirectory = resolve(scriptDirectory, "..");
const playwrightCli = resolve(
  frontendDirectory,
  "node_modules/@playwright/test/cli.js",
);
const scanner = resolve(scriptDirectory, "validate-playwright-artifacts.mjs");

const testRun = spawnSync(
  process.execPath,
  [playwrightCli, "test", ...process.argv.slice(2)],
  { cwd: frontendDirectory, env: process.env, stdio: "inherit" },
);
const evidenceScan = spawnSync(process.execPath, [scanner], {
  cwd: frontendDirectory,
  env: process.env,
  stdio: "inherit",
});

process.exitCode = testRun.status || evidenceScan.status || 0;

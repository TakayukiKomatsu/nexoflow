// @vitest-environment node

import { execFile } from "node:child_process";
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import { promisify } from "node:util";
import { afterEach, expect, it } from "vitest";

const execute = promisify(execFile);
const validator = resolve("scripts/validate-authoritative-pricing.mjs");
const temporaryDirectories: string[] = [];

async function validate(sourceRoot: string) {
  return execute(process.execPath, [validator, "--source-root", sourceRoot]);
}

afterEach(async () => {
  await Promise.all(
    temporaryDirectories
      .splice(0)
      .map((directory) => rm(directory, { force: true, recursive: true })),
  );
});

it("scans extracted production modules while excluding test infrastructure", async () => {
  const directory = await mkdtemp(resolve(tmpdir(), "srm-authority-"));
  temporaryDirectories.push(directory);
  const sourceRoot = resolve(directory, "src");
  await mkdir(resolve(sourceRoot, "components"), { recursive: true });
  await mkdir(resolve(sourceRoot, "test"), { recursive: true });
  const arithmetic = "quote.settlementAmount * 2";
  await writeFile(
    resolve(sourceRoot, "components/PricingBreakdown.test.tsx"),
    `export const ignoredTest = ${arithmetic};`,
    "utf8",
  );
  await writeFile(
    resolve(sourceRoot, "test/setup.ts"),
    `export const ignoredSetup = ${arithmetic};`,
    "utf8",
  );
  await writeFile(
    resolve(sourceRoot, "pricing.d.ts"),
    `declare const ignoredDeclaration: typeof ${arithmetic};`,
    "utf8",
  );
  await writeFile(
    resolve(sourceRoot, "components/PricingBreakdown.tsx"),
    "export const amount = quote.settlementAmount;",
    "utf8",
  );

  await expect(validate(sourceRoot)).resolves.toEqual(
    expect.objectContaining({
      stdout: expect.stringContaining("AUTHORITY-001 passed"),
    }),
  );

  await writeFile(
    resolve(sourceRoot, "components/PricingBreakdown.tsx"),
    `export const forbiddenTotal = ${arithmetic};`,
    "utf8",
  );

  await expect(validate(sourceRoot)).rejects.toEqual(
    expect.objectContaining({
      stderr: expect.stringContaining("components/PricingBreakdown.tsx"),
    }),
  );

  for (const mutation of [
    "const amount = quote.settlementAmount; export const forbidden = amount * 2;",
    "const { settlementAmount } = quote; export const forbidden = settlementAmount / 2;",
    "const amount = quote.settlementAmount; export const forbidden = Number(amount);",
    'const amount = quote.settlementAmount ?? "0"; export const forbidden = amount * 2;',
    'const amount = condition ? quote.settlementAmount : "0"; export const forbidden = amount * 2;',
  ]) {
    await writeFile(
      resolve(sourceRoot, "components/PricingBreakdown.tsx"),
      mutation,
      "utf8",
    );
    await expect(validate(sourceRoot)).rejects.toEqual(
      expect.objectContaining({
        stderr: expect.stringContaining("components/PricingBreakdown.tsx"),
      }),
    );
  }
});

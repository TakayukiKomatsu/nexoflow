// @vitest-environment node

import { execFile } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import { promisify } from "node:util";
import { afterEach, describe, expect, it } from "vitest";

const execute = promisify(execFile);
const script = resolve("scripts/validate-pricing-quote-contract.mjs");
const fixture = resolve("scripts/fixtures/pricing-openapi.json");
const client = resolve("src/api/client.ts");
const temporaryDirectories: string[] = [];

async function validate(document: string) {
  return execute(process.execPath, [
    script,
    "--document",
    document,
    "--client",
    client,
  ]);
}

afterEach(async () => {
  await Promise.all(
    temporaryDirectories.splice(0).map((directory) =>
      rm(directory, { recursive: true, force: true }),
    ),
  );
});

describe("runtime OpenAPI contract validator", () => {
  it("accepts the generated quote and pricing breakdown contract", async () => {
    await expect(validate(fixture)).resolves.toEqual(
      expect.objectContaining({
        stdout: expect.stringContaining("API-CONTRACT-001 passed"),
      }),
    );
  });

  it("fails when the OpenAPI document drifts from the frontend model", async () => {
    const directory = await mkdtemp(
      resolve(tmpdir(), "srm-openapi-contract-"),
    );
    temporaryDirectories.push(directory);
    const document = JSON.parse(await readFile(fixture, "utf8")) as {
      components: {
        schemas: { QuoteResponse: { properties: Record<string, unknown> } };
      };
    };
    delete document.components.schemas.QuoteResponse.properties.createdBy;
    const drifted = resolve(directory, "openapi.json");
    await writeFile(drifted, JSON.stringify(document), "utf8");

    await expect(validate(drifted)).rejects.toEqual(
      expect.objectContaining({
        stderr: expect.stringContaining("createdBy"),
      }),
    );
  });
});

// @vitest-environment node

import { execFile } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import { promisify } from "node:util";
import { afterEach, describe, expect, it } from "vitest";
import playwrightConfig from "../playwright.config";

const execute = promisify(execFile);
const scanner = resolve("scripts/validate-playwright-artifacts.mjs");
const temporaryDirectories: string[] = [];

afterEach(async () => {
  await Promise.all(
    temporaryDirectories
      .splice(0)
      .map((directory) => rm(directory, { recursive: true, force: true })),
  );
});

describe("Playwright evidence security", () => {
  it("does not record authenticated browser state", () => {
    expect(playwrightConfig.use).toEqual(
      expect.objectContaining({
        screenshot: "off",
        trace: "off",
        video: "off",
      }),
    );
  });

  it("fails closed and redacts artifacts containing credentials", async () => {
    const directory = await mkdtemp(resolve(tmpdir(), "srm-e2e-artifacts-"));
    temporaryDirectories.push(directory);
    const artifact = resolve(directory, "report.html");
    await writeFile(
      artifact,
      "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJvcCJ9.signature",
      "utf8",
    );

    await expect(
      execute(process.execPath, [scanner, "--root", directory]),
    ).rejects.toEqual(
      expect.objectContaining({
        stderr: expect.stringContaining("authorization credential"),
      }),
    );
    expect(await readFile(artifact, "utf8")).toBe(
      "Artifact removed: sensitive test evidence was detected.\n",
    );
  });

  it("accepts evidence without authentication material", async () => {
    const directory = await mkdtemp(resolve(tmpdir(), "srm-e2e-artifacts-"));
    temporaryDirectories.push(directory);
    await writeFile(
      resolve(directory, "summary.txt"),
      "operator critical path passed",
      "utf8",
    );

    await expect(
      execute(process.execPath, [scanner, "--root", directory]),
    ).resolves.toEqual(
      expect.objectContaining({
        stdout: expect.stringContaining("E2E-EVIDENCE-001 passed"),
      }),
    );
  });
});

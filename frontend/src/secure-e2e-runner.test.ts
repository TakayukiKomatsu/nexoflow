// @vitest-environment node

import { execFile } from "node:child_process";
import { pathToFileURL } from "node:url";
import { resolve } from "node:path";
import { promisify } from "node:util";
import { expect, it } from "vitest";

const execute = promisify(execFile);
const resultModule = pathToFileURL(
  resolve("scripts/secure-child-result.mjs"),
).href;

it("fails closed when either secure E2E child cannot complete normally", async () => {
  const evaluation = `
    const { secureRunnerExitCode } = await import(${JSON.stringify(resultModule)});
    const ok = { error: undefined, signal: null, status: 0 };
    const scenarios = [
      secureRunnerExitCode(ok, ok),
      secureRunnerExitCode({ ...ok, status: 7 }, ok),
      secureRunnerExitCode({ error: { code: "ENOENT" }, signal: null, status: null }, ok),
      secureRunnerExitCode({ error: undefined, signal: "SIGTERM", status: null }, ok),
      secureRunnerExitCode({ error: undefined, signal: null, status: null }, ok),
      secureRunnerExitCode(ok, { error: { code: "ENOENT" }, signal: null, status: null }),
      secureRunnerExitCode(ok, { error: undefined, signal: "SIGKILL", status: null }),
    ];
    process.stdout.write(JSON.stringify(scenarios));
  `;

  const { stdout } = await execute(process.execPath, [
    "--input-type=module",
    "--eval",
    evaluation,
  ]);

  expect(JSON.parse(stdout)).toEqual([0, 7, 1, 1, 1, 1, 1]);
});

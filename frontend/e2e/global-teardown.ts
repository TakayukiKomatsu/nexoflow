import { execSync } from "child_process";
import { fileURLToPath } from "url";
import * as path from "path";

/**
 * Shut down the Compose stack and remove volumes after every test run.
 * A non-zero exit from `docker compose down` is re-thrown so the teardown
 * fails visibly instead of silently leaving a running stack.
 */
export default async function globalTeardown(): Promise<void> {
  // Resolve project root: this file is at frontend/e2e/, go two levels up.
  const fileDir = path.dirname(fileURLToPath(import.meta.url));
  const projectRoot = path.resolve(fileDir, "..", "..");

  try {
    execSync("docker compose down -v --remove-orphans", {
      cwd: projectRoot,
      stdio: "inherit",
    });
  } catch (err) {
    console.error(
      "\n[E2E teardown] docker compose down FAILED — stack may be left running\n",
      err,
    );
    throw err;
  }
}

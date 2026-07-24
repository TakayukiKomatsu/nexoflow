// @vitest-environment node

import { createServer as createHttpServer } from "node:http";
import { resolve } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { createServer as createViteServer, type ViteDevServer } from "vite";

describe("native Vite runtime", () => {
  let vite: ViteDevServer | undefined;
  let backend: ReturnType<typeof createHttpServer> | undefined;
  const previousBackendOrigin = process.env.VITE_BACKEND_ORIGIN;

  afterEach(async () => {
    await vite?.close();
    await new Promise<void>((resolveClose, reject) => {
      if (!backend?.listening) {
        resolveClose();
        return;
      }
      backend.close((error) => (error ? reject(error) : resolveClose()));
    });
    vite = undefined;
    backend = undefined;
    if (previousBackendOrigin === undefined) {
      delete process.env.VITE_BACKEND_ORIGIN;
    } else {
      process.env.VITE_BACKEND_ORIGIN = previousBackendOrigin;
    }
  });

  it("forwards /api/v1 requests to the configured backend", async () => {
    backend = createHttpServer((request, response) => {
      response.setHeader("Content-Type", "application/json");
      response.end(JSON.stringify({ path: request.url }));
    });
    await new Promise<void>((resolveListen) =>
      backend!.listen(0, "127.0.0.1", resolveListen),
    );
    const backendAddress = backend.address();
    if (!backendAddress || typeof backendAddress === "string") {
      throw new Error("Test backend did not bind to a TCP port.");
    }
    process.env.VITE_BACKEND_ORIGIN = `http://127.0.0.1:${backendAddress.port}`;

    vite = await createViteServer({
      configFile: resolve(process.cwd(), "vite.config.ts"),
      server: { host: "127.0.0.1", port: 0, strictPort: true },
    });
    await vite.listen();
    const viteAddress = vite.httpServer?.address();
    if (!viteAddress || typeof viteAddress === "string") {
      throw new Error("Vite did not bind to a TCP port.");
    }

    const response = await fetch(
      `http://127.0.0.1:${viteAddress.port}/api/v1/users/me`,
    );

    expect(response.headers.get("content-type")).toContain("application/json");
    await expect(response.json()).resolves.toEqual({
      path: "/api/v1/users/me",
    });
  });
});

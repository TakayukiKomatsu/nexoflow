import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, process.cwd(), "");
  const backendOrigin =
    environment.VITE_BACKEND_ORIGIN ?? "http://127.0.0.1:8080";
  const protocol = new URL(backendOrigin).protocol;
  if (protocol !== "http:" && protocol !== "https:") {
    throw new Error("VITE_BACKEND_ORIGIN must be an HTTP(S) origin.");
  }

  return {
    plugins: [react()],
    server: {
      proxy: {
        "/api": {
          target: backendOrigin,
        },
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts",
      include: ["src/**/*.test.{ts,tsx}"],
      coverage: {
        provider: "v8",
        reporter: ["text-summary", "json-summary", "lcov"],
        include: [
          "src/App.tsx",
          "src/SettlementWorkspace.tsx",
          "src/api/client.ts",
        ],
        thresholds: {
          lines: 95,
          functions: 95,
          branches: 95,
          statements: 95,
        },
      },
    },
  };
});

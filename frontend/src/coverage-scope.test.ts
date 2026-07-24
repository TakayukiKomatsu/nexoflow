// @vitest-environment node

import { expect, it } from "vitest";
import { productionCoverageScope } from "../vite.config";

it("places every production TypeScript module inside the coverage gate", () => {
  expect(productionCoverageScope).toEqual({
    include: ["src/**/*.{ts,tsx}"],
    exclude: [
      "src/**/*.test.{ts,tsx}",
      "src/**/*.d.ts",
      "src/main.tsx",
      "src/test/**",
    ],
  });
});

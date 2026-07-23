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

type SchemaFixture = {
  $ref?: string;
  enum?: string[];
  format?: string;
  items?: SchemaFixture;
  nullable?: boolean;
  type?: string;
};

type ContractFixture = {
  paths: Record<
    string,
    Record<
      string,
      {
        parameters?: Array<{
          in: string;
          name: string;
          required: boolean;
          schema: SchemaFixture;
        }>;
        requestBody?: {
          content: {
            "application/json": { schema: SchemaFixture };
          };
          required: boolean;
        };
        responses: Record<
          string,
          { content: { "application/json": { schema: SchemaFixture } } }
        >;
      }
    >
  >;
  components: {
    schemas: Record<
      string,
      { properties: Record<string, SchemaFixture>; required?: string[] }
    >;
  };
};

async function validate(document: string) {
  return execute(process.execPath, [
    script,
    "--document",
    document,
    "--client",
    client,
  ]);
}

async function driftDocument(
  mutate: (document: ContractFixture) => void,
): Promise<string> {
  const directory = await mkdtemp(resolve(tmpdir(), "srm-openapi-contract-"));
  temporaryDirectories.push(directory);
  const document = JSON.parse(
    await readFile(fixture, "utf8"),
  ) as ContractFixture;
  mutate(document);
  const drifted = resolve(directory, "openapi.json");
  await writeFile(drifted, JSON.stringify(document), "utf8");
  return drifted;
}

afterEach(async () => {
  await Promise.all(
    temporaryDirectories
      .splice(0)
      .map((directory) => rm(directory, { recursive: true, force: true })),
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
    const drifted = await driftDocument((document) => {
      delete document.components.schemas.QuoteResponse.properties.createdBy;
    });

    await expect(validate(drifted)).rejects.toEqual(
      expect.objectContaining({
        stderr: expect.stringContaining("createdBy"),
      }),
    );
  });

  it("rejects primitive, optionality, array-reference, enum, and format drift", async () => {
    const cases: Array<{
      expected: string;
      mutate: (document: ContractFixture) => void;
    }> = [
      {
        expected: "settlementAmount",
        mutate: (document) => {
          document.components.schemas.PricingBreakdownResponse.properties.settlementAmount.type =
            "number";
        },
      },
      {
        expected: "createdBy",
        mutate: (document) => {
          document.components.schemas.QuoteResponse.required =
            document.components.schemas.QuoteResponse.required?.filter(
              (name) => name !== "createdBy",
            );
        },
      },
      {
        expected: "items",
        mutate: (document) => {
          const items =
            document.components.schemas.SettlementResponse.properties.items
              .items;
          if (items) items.$ref = "#/components/schemas/QuoteResponse";
        },
      },
      {
        expected: "entryType",
        mutate: (document) => {
          document.components.schemas.EntryResponse.properties.entryType.enum =
            ["SETTLEMENT", "REVERSAL", "VOIDED"];
        },
      },
      {
        expected: "dueDate",
        mutate: (document) => {
          document.components.schemas.QuoteResponse.properties.dueDate.format =
            "date-time";
        },
      },
    ];

    for (const testCase of cases) {
      const drifted = await driftDocument(testCase.mutate);
      await expect(validate(drifted)).rejects.toEqual(
        expect.objectContaining({
          stderr: expect.stringContaining(testCase.expected),
        }),
      );
    }
  });

  it("rejects endpoint, request-body, query, header, and response drift", async () => {
    const cases: Array<{
      expected: string;
      mutate: (document: ContractFixture) => void;
    }> = [
      {
        expected: "pricing-simulations",
        mutate: (document) => {
          document.paths["/api/v1/pricing-simulations"].put =
            document.paths["/api/v1/pricing-simulations"].post;
          delete document.paths["/api/v1/pricing-simulations"].post;
        },
      },
      {
        expected: "settlementCurrency",
        mutate: (document) => {
          delete document.components.schemas.SimulationRequest.properties
            .settlementCurrency;
        },
      },
      {
        expected: "Idempotency-Key",
        mutate: (document) => {
          const parameter =
            document.paths["/api/v1/settlements"].post.parameters?.[0];
          if (parameter) parameter.required = false;
        },
      },
      {
        expected: "assignorId",
        mutate: (document) => {
          const parameters =
            document.paths["/api/v1/settlement-statements"].get.parameters;
          if (parameters) {
            document.paths["/api/v1/settlement-statements"].get.parameters =
              parameters.filter((parameter) => parameter.name !== "assignorId");
          }
        },
      },
      {
        expected: "SettlementResponse",
        mutate: (document) => {
          document.paths["/api/v1/settlements"].post.responses["201"].content[
            "application/json"
          ].schema.$ref = "#/components/schemas/PreviewResponse";
        },
      },
    ];

    for (const testCase of cases) {
      const drifted = await driftDocument(testCase.mutate);
      await expect(validate(drifted)).rejects.toEqual(
        expect.objectContaining({
          stderr: expect.stringContaining(testCase.expected),
        }),
      );
    }
  });
});

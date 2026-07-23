import type { PricingSimulationRequest } from "../api/client";

export type FormValues = PricingSimulationRequest & {
  assignorId: string;
  issueDate: string;
};

export const initialValues: FormValues = {
  assignorId: "",
  faceAmount: "1000.00",
  faceCurrency: "BRL",
  productType: "MERCANTILE_INVOICE",
  issueDate: new Date().toISOString().slice(0, 10),
  dueDate: "2030-02-14",
  settlementCurrency: "BRL",
};

export type FieldErrors = Partial<Record<keyof FormValues, string>>;

export type Feedback = {
  text: string;
  kind: "success" | "error";
};

export function pricingValidation(values: FormValues): FieldErrors {
  const errors: FieldErrors = {};
  if (
    !/^\d{1,15}(\.\d{1,4})?$/.test(values.faceAmount) ||
    Number(values.faceAmount) <= 0
  ) {
    errors.faceAmount =
      "Enter a positive amount with up to four decimal places.";
  }
  if (!/^[A-Z]{3}$/.test(values.faceCurrency)) {
    errors.faceCurrency = "Enter a three-letter uppercase face currency.";
  }
  if (!/^[A-Z]{3}$/.test(values.settlementCurrency)) {
    errors.settlementCurrency =
      "Enter a three-letter uppercase settlement currency.";
  }
  if (!values.dueDate) errors.dueDate = "Enter a due date.";
  return errors;
}

export function registrationValidation(values: FormValues): FieldErrors {
  const errors = pricingValidation(values);
  if (!values.issueDate) errors.issueDate = "Enter an issue date.";
  if (
    values.issueDate &&
    values.dueDate &&
    values.dueDate <= values.issueDate
  ) {
    errors.dueDate = "Due date must be after issue date.";
  }
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
      values.assignorId,
    )
  ) {
    errors.assignorId = "Enter a valid assignor UUID.";
  }
  return errors;
}

export function pricingFingerprint(values: FormValues): string {
  return JSON.stringify([
    values.assignorId,
    values.productType,
    values.faceAmount,
    values.faceCurrency,
    values.issueDate,
    values.dueDate,
    values.settlementCurrency,
  ]);
}

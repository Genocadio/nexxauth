"use client";

import { useCallback, useState } from "react";
import type { z } from "zod";
import { getErrorMessage } from "@/types/errors";

type FieldErrors = Record<string, string>;

export interface UseFormResult<TInput, TOutput> {
  values: TInput;
  errors: FieldErrors;
  submitError: string | null;
  /** Update a single field; also clears that field's error. */
  setValue: <K extends keyof TInput>(key: K, value: TInput[K]) => void;
  /** Replace the values wholesale (e.g. when editing an existing record). */
  setValues: (values: TInput) => void;
  /** Set field errors manually (e.g. conditional rules across modes). */
  setErrors: (errors: FieldErrors) => void;
  setSubmitError: (message: string | null) => void;
  reset: (values?: TInput) => void;
  /** Validate, then run `fn` with the validated values. */
  handleSubmit: (fn: (values: TOutput) => Promise<unknown> | void) => (e?: { preventDefault(): void }) => Promise<void>;
}

function flattenZodErrors(error: z.ZodError): FieldErrors {
  const errors: FieldErrors = {};
  for (const issue of error.issues) {
    const key = issue.path.join(".") || "_form";
    if (!errors[key]) errors[key] = issue.message;
  }
  return errors;
}

/**
 * Minimal typed form state: keeps `values` + `errors`, validates with a zod
 * schema on submit, and hands submit errors back to the UI. Shared by every
 * form in the app so validation + error display behave identically.
 */
export function useForm<TInput, TOutput = TInput>(
  schema: z.ZodType<TOutput, TInput>,
  initialValues: TInput,
): UseFormResult<TInput, TOutput> {
  const [values, setValuesState] = useState<TInput>(initialValues);
  const [errors, setErrorsState] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  const setValue = useCallback(<K extends keyof TInput>(key: K, value: TInput[K]) => {
    setValuesState((prev) => ({ ...prev, [key]: value }));
    setErrorsState((prev) => {
      if (!(key in prev)) return prev;
      const next = { ...prev };
      delete next[key as string];
      return next;
    });
  }, []);

  const setValues = useCallback((next: TInput) => {
    setValuesState(next);
    setErrorsState({});
  }, []);

  const setErrors = useCallback((next: FieldErrors) => setErrorsState(next), []);

  const reset = useCallback((next?: TInput) => {
    setValuesState(next ?? initialValues);
    setErrorsState({});
    setSubmitError(null);
  }, [initialValues]);

  const handleSubmit = useCallback(
    (fn: (data: TOutput) => Promise<unknown> | void) =>
      async (e?: { preventDefault(): void }) => {
        e?.preventDefault();
        setSubmitError(null);
        const result = schema.safeParse(values);
        if (!result.success) {
          setErrorsState(flattenZodErrors(result.error));
          return;
        }
        setErrorsState({});
        try {
          await fn(result.data);
        } catch (error) {
          setSubmitError(getErrorMessage(error));
        }
      },
    [schema, values],
  );

  return { values, errors, submitError, setValue, setValues, setErrors, setSubmitError, reset, handleSubmit };
}

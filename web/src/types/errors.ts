/**
 * The backend always answers errors with the same JSON shape.
 */
export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  requestId?: string;
  /** Present on 400 validation errors: field name -> human readable message. */
  fieldErrors?: Record<string, string>;
}

/**
 * Normalised client-side error thrown by the API layer. `status` is the HTTP
 * status (0 when the request never reached the server), `message` is safe to
 * show to the user and `fieldErrors` maps form fields to messages.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors?: Record<string, string>;
  readonly requestId?: string;

  constructor(status: number, message: string, fieldErrors?: Record<string, string>, requestId?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.requestId = requestId;
  }
}

/** Turn any thrown value into a user-facing message. */
export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error && error.message) return error.message;
  return "Something went wrong. Please try again.";
}

export const UI_RENDER_FAILURE = "SRM UI render failure" as const;

/**
 * Emits the one approved UI failure signal without accepting runtime data.
 * Keeping the API payload-free prevents errors, component state, and user data
 * from reaching browser logs.
 */
export function reportUiRenderFailure(): void {
  console.error(UI_RENDER_FAILURE);
}

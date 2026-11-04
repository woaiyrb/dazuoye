/**
 * Helpers for the JSON-encoded integer columns.
 *
 * MySQL has no native array type, so the columns that used to be
 * MySQL `Int[]` (`questionOrder`, `optionalQuestionIds`,
 * `gradingContextQuestionIds`) are stored as JSON arrays. These helpers keep
 * the rest of the code base working with plain `number[]` values.
 */

/** Narrow an unknown JSON value into a `number[]`. */
export function toIntArray(value: unknown): number[] {
  if (Array.isArray(value)) {
    return value.filter((entry): entry is number => typeof entry === "number");
  }

  if (typeof value === "string") {
    try {
      const parsed: unknown = JSON.parse(value);
      if (Array.isArray(parsed)) {
        return parsed.filter(
          (entry): entry is number => typeof entry === "number",
        );
      }
    } catch {
      return [];
    }
  }

  return [];
}

/** Serialise a `number[]` for a nullable JSON column. */
export function toJsonIntArray(value: number[] | null | undefined): number[] {
  return value ?? [];
}

/**
 * `questionOrder` is stored as a JSON array (MySQL has no native array type),
 * so callers may hand us a raw Prisma JSON value as well as a plain number[].
 */
import { toIntArray } from "../../../common/utils/json-array.util";

export function sanitizeQuestionOrder(order?: unknown): number[] {
  return toIntArray(order).filter((id) => Number.isFinite(id));
}

export function normalizeQuestionOrder(
  validIds: readonly number[],
  order?: unknown,
): number[] {
  const validSet = new Set(validIds);
  const seen = new Set<number>();
  const normalized: number[] = [];

  for (const id of sanitizeQuestionOrder(order)) {
    if (validSet.has(id) && !seen.has(id)) {
      normalized.push(id);
      seen.add(id);
    }
  }

  for (const id of validIds) {
    if (!seen.has(id)) {
      normalized.push(id);
      seen.add(id);
    }
  }

  return normalized;
}

export function applyQuestionOrder<T extends { id: number }>(
  items: readonly T[],
  order?: unknown,
): T[] {
  const itemMap = new Map(items.map((item) => [item.id, item]));

  return normalizeQuestionOrder(
    items.map((item) => item.id),
    order,
  )
    .map((id) => itemMap.get(id))
    .filter((item): item is T => item !== undefined);
}

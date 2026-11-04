const REQUEST_TIMEOUT_MS = 60_000;

export class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

export async function apiRequest(path, options = {}) {
  const controller = new AbortController();
  const timeout = window.setTimeout(
    () => controller.abort(),
    options.timeoutMs || REQUEST_TIMEOUT_MS,
  );

  try {
    const response = await fetch(path, {
      credentials: "include",
      ...options,
      signal: controller.signal,
      headers: {
        Accept: "application/json",
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...options.headers,
      },
    });

    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json")
      ? await response.json()
      : await response.text();

    if (!response.ok) {
      const message =
        (body && typeof body === "object" && (body.message || body.error)) ||
        `请求失败（HTTP ${response.status}）`;
      throw new ApiError(String(message), response.status, body);
    }
    return body;
  } catch (error) {
    if (error?.name === "AbortError") {
      throw new ApiError("请求超时，请稍后重试。", 0, error);
    }
    throw error;
  } finally {
    window.clearTimeout(timeout);
  }
}

export function getUser() {
  return apiRequest("/api/v1/user-session");
}

export async function getAssignments() {
  const result = await apiRequest("/api/v2/assignments").catch(() => []);
  return Array.isArray(result) ? result.map(normalizeAssignment) : [];
}

export async function getAssignment(id) {
  const result = await apiRequest(`/api/v2/assignments/${id}`);
  return normalizeAssignment(result);
}

export async function getAttempts(id) {
  const result = await apiRequest(
    `/api/v2/assignments/${id}/attempts`,
  ).catch(() => []);
  return Array.isArray(result) ? result : [];
}

export async function getAssignmentInsights(id) {
  return apiRequest(`/api/v2/assignments/${id}/insights`).catch(() => null);
}

export async function getClasses() {
  const result = await apiRequest("/api/v1/classes").catch(() => []);
  return Array.isArray(result) ? result : [];
}

export async function getKnowledgePoints() {
  const result = await apiRequest("/api/v1/knowledge-points").catch(() => []);
  return Array.isArray(result) ? result : [];
}

export async function getAiStatus() {
  return apiRequest("/api/v1/ai-status").catch(() => ({
    grading: false,
    chat: false,
    authoring: false,
  }));
}

export function normalizeAssignment(input) {
  if (!input || typeof input !== "object") return input;
  const assignment = { ...input };
  for (const field of [
    "name",
    "introduction",
    "instructions",
    "gradingCriteriaOverview",
  ]) {
    assignment[field] = decodeMaybeBase64(assignment[field]);
  }
  assignment.questions = (assignment.questions || []).map((question) => {
    const normalized = { ...question };
    for (const field of [
      "question",
      "authorComment",
      "feedback",
      "answer",
    ]) {
      normalized[field] = decodeMaybeBase64(normalized[field]);
    }
    normalized.choices = (normalized.choices || []).map((choice) => ({
      ...choice,
      choice: decodeMaybeBase64(choice.choice),
    }));
    normalized.scoring = normalizeScoring(normalized.scoring);
    return normalized;
  });
  return assignment;
}

function normalizeScoring(scoring) {
  if (!scoring || typeof scoring !== "object") return scoring;
  return {
    ...scoring,
    rubrics: (scoring.rubrics || []).map((rubric) => ({
      ...rubric,
      rubricQuestion: decodeMaybeBase64(rubric.rubricQuestion),
      criteria: (rubric.criteria || []).map((criterion) => ({
        ...criterion,
        description: decodeMaybeBase64(criterion.description),
      })),
    })),
  };
}

function decodeMaybeBase64(value) {
  if (typeof value !== "string" || !looksLikeBase64(value)) return value;
  try {
    const binary = window.atob(value);
    const bytes = Uint8Array.from(binary, (character) =>
      character.charCodeAt(0),
    );
    const decoded = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    return decoded.includes("\u0000") ? value : decoded;
  } catch {
    return value;
  }
}

function looksLikeBase64(value) {
  const compact = value.replace(/\s/g, "");
  return compact.length >= 16 && /^[A-Za-z0-9+/]+={0,2}$/.test(compact);
}

export function formatDate(value) {
  if (!value) return "暂无";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "暂无";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "short",
    day: "numeric",
  }).format(date);
}

export function relativeTime(value) {
  if (!value) return "暂无活动";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "暂无活动";
  const minutes = Math.max(0, Math.round((Date.now() - date.getTime()) / 60_000));
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.round(hours / 24);
  return days < 30 ? `${days} 天前` : formatDate(value);
}

/* eslint-disable unicorn/no-null */
import { Inject, Injectable } from "@nestjs/common";
import { WINSTON_MODULE_PROVIDER } from "nest-winston";
import { Logger } from "winston";
import { logAiInvocation } from "../utils/ai-invocation-log.util";
import { DeepTutorClientService } from "./deeptutor-client.service";

/**
 * DeepTutor's reusable teaching capabilities, called over its real backend
 * protocol:
 *
 * - `deep_question` on /ws for structured question generation.
 * - /ws/questions/judge for streamed answer assessment.
 *
 * Connection recovery, authentication, keepalive, and protocol framing live in
 * DeepTutorClientService so every AI path behaves consistently.
 */
export const DEEPTUTOR_SKILLS_MODEL_KEY = "deeptutor-skills";

export interface DeepTutorQuestionRequest {
  topic: string;
  numQuestions: number;
  difficulty: "easy" | "medium" | "hard";
  questionTypes?: string[];
  content?: string;
  learningObjectives?: string;
  mcSubtype?: string;
}

export interface DeepTutorQuestion {
  questionId: string;
  question: string;
  questionType: string;
  options: Record<string, string> | undefined;
  correctAnswer: string;
  explanation: string;
  difficulty: string;
  concentration?: string;
}

export interface DeepTutorJudgeRequest {
  question: string;
  questionType: string;
  options?: Record<string, string>;
  correctAnswer?: string;
  explanation?: string;
  userAnswer: string;
  language?: string;
  images?: DeepTutorJudgeImage[];
}

export interface DeepTutorJudgeImage {
  base64?: string;
  url?: string;
  filename?: string;
  mimeType?: string;
}

export type DeepTutorVerdict = "correct" | "partial" | "incorrect" | "unknown";

export interface DeepTutorJudgeResult {
  verdict: DeepTutorVerdict;
  feedback: string;
}

@Injectable()
export class DeepTutorSkillsService {
  private readonly logger: Logger;

  constructor(
    private readonly client: DeepTutorClientService,
    @Inject(WINSTON_MODULE_PROVIDER) parentLogger: Logger,
  ) {
    this.logger = parentLogger.child({ context: DeepTutorSkillsService.name });
  }

  get isEnabled(): boolean {
    return process.env.DEEPTUTOR_SKILLS_ENABLED !== "false";
  }

  private get questionTimeoutMs(): number {
    return Number(process.env.DEEPTUTOR_SKILLS_QUESTION_TIMEOUT_MS) || 300_000;
  }

  private get judgeTimeoutMs(): number {
    return Number(process.env.DEEPTUTOR_SKILLS_JUDGE_TIMEOUT_MS) || 150_000;
  }

  async generateQuestions(
    request: DeepTutorQuestionRequest,
  ): Promise<DeepTutorQuestion[]> {
    const topic = this.composeTopic(request);
    if (!topic) {
      throw new Error("DeepTutor question generation requires a topic");
    }

    const config: Record<string, unknown> = {
      mode: "custom",
      topic,
      num_questions: Math.max(1, Math.min(50, request.numQuestions)),
      difficulty: request.difficulty,
    };
    if (request.questionTypes?.length) {
      config.question_types = request.questionTypes;
    }

    const response = await this.client.runTurn(
      {
        content: topic,
        capability: "deep_question",
        config,
        language: "zh",
        persist_user_message: false,
        capability_once: true,
      },
      {
        purpose: "question generation",
        timeoutMs: this.questionTimeoutMs,
        keepAlive: true,
        accumulateText: false,
      },
    );

    const result = response.result;
    const metadata =
      asRecord(result?.metadata) ?? asRecord(result?.summary) ?? {};
    const summary = asRecord(metadata.summary) ?? metadata;
    const rawResults = Array.isArray(summary.results) ? summary.results : [];
    const questions = rawResults
      .map((entry) => asRecord(entry)?.qa_pair)
      .map((qa) => asRecord(qa))
      .filter((qa): qa is Record<string, unknown> => qa !== undefined)
      .map((qa) => this.toQuestion(qa));

    if (questions.length === 0) {
      throw new Error(
        `deeptutor.question_generation.empty ` +
          `(completed=${String(summary.completed ?? 0)}, ` +
          `failed=${String(summary.failed ?? 0)})`,
      );
    }

    logAiInvocation(this.logger, {
      modelKey: DEEPTUTOR_SKILLS_MODEL_KEY,
      purpose: "question_generation",
      prompt: topic.slice(0, 500),
      response: `generated ${questions.length} questions`,
    });

    return questions;
  }

  async judgeAnswer(
    request: DeepTutorJudgeRequest,
  ): Promise<DeepTutorJudgeResult> {
    const payload = {
      question: request.question,
      question_type: request.questionType || "short_answer",
      options: request.options ?? null,
      correct_answer: request.correctAnswer ?? "",
      explanation: request.explanation ?? "",
      user_answer: request.userAnswer ?? "",
      user_answer_images:
        request.images && request.images.length > 0
          ? request.images.map((image) => ({
              base64: image.base64,
              url: image.url,
              filename: image.filename,
              mime_type: image.mimeType,
            }))
          : null,
      language: request.language || "zh",
    };

    const response = await this.client.runSocket({
      path: "/ws/questions/judge",
      payload,
      purpose: "judge",
      timeoutMs: this.judgeTimeoutMs,
      accumulateText: true,
      keepAlive: false,
      recover: true,
    });
    const feedback = response.content.trim();
    const result: DeepTutorJudgeResult = {
      verdict: parseVerdict(feedback),
      feedback,
    };

    logAiInvocation(this.logger, {
      modelKey: DEEPTUTOR_SKILLS_MODEL_KEY,
      purpose: "answer_judging",
      prompt: request.question.slice(0, 500),
      response: `${result.verdict}: ${result.feedback.slice(0, 300)}`,
    });

    return result;
  }

  private composeTopic(request: DeepTutorQuestionRequest): string {
    const parts: string[] = [];
    if (request.learningObjectives?.trim()) {
      parts.push(`学习目标：${request.learningObjectives.trim()}`);
    }
    if (request.content?.trim()) {
      parts.push(`参考材料：${request.content.trim().slice(0, 4000)}`);
    }
    if (parts.length === 0 && request.topic?.trim()) {
      parts.push(request.topic.trim());
    } else if (request.topic?.trim() && parts.length > 0) {
      parts.unshift(`主题：${request.topic.trim()}`);
    }
    if (request.mcSubtype) {
      const preference = MC_SUBTYPE_PREFERENCE[request.mcSubtype];
      if (preference) {
        parts.push(`出题偏好：${preference}`);
      }
    }
    return parts.join("\n").trim();
  }

  private toQuestion(qa: Record<string, unknown>): DeepTutorQuestion {
    const options = asRecord(qa.options);
    return {
      questionId: String(qa.question_id ?? ""),
      question: String(qa.question ?? "").trim(),
      questionType: String(qa.question_type ?? "short_answer"),
      options: options
        ? Object.fromEntries(
            Object.entries(options).map(([key, value]) => [key, String(value)]),
          )
        : undefined,
      correctAnswer: String(qa.correct_answer ?? "").trim(),
      explanation: String(qa.explanation ?? "").trim(),
      difficulty: String(qa.difficulty ?? ""),
      concentration: qa.concentration ? String(qa.concentration) : undefined,
    };
  }
}

const MC_SUBTYPE_PREFERENCE: Record<string, string> = {
  short: "选择题题干简短，考查单一概念",
  quantitative: "选择题以计算或数量推理为主",
  long: "选择题题干较长，包含较完整的情境",
  scenario: "选择题以真实情境或案例为背景",
};

/**
 * A partial verdict earns half the points; an unparsable verdict is treated
 * as partial so a formatting glitch does not automatically fail a learner.
 */
export function verdictToPoints(
  verdict: DeepTutorVerdict,
  maxPoints: number,
): number {
  switch (verdict) {
    case "correct": {
      return maxPoints;
    }
    case "incorrect": {
      return 0;
    }
    default: {
      return Math.round(maxPoints / 2);
    }
  }
}

/**
 * DeepTutor's judge opens with one verdict line:
 * "✅ 正确 / ⚠️ 部分正确 / ❌ 不正确" (or its localized equivalent).
 */
export function parseVerdict(feedback: string): DeepTutorVerdict {
  const firstLine = (feedback || "").trim().split("\n")[0] ?? "";
  if (firstLine.includes("✅")) return "correct";
  if (firstLine.includes("⚠")) return "partial";
  if (firstLine.includes("❌")) return "incorrect";
  return "unknown";
}

/**
 * Question generation stores the reference answer inside the first rubric
 * criterion so the grading side can send it back to the judge.
 */
const REFERENCE_ANSWER_PREFIX = "参考答案要点：";

export function buildReferenceAnswerCriterion(correctAnswer: string): string {
  return `${REFERENCE_ANSWER_PREFIX}${correctAnswer.slice(0, 1500)}`;
}

export function extractReferenceAnswer(
  scoringCriteria: unknown,
): string | undefined {
  const scoring = scoringCriteria as
    | { rubrics?: { criteria?: { description?: string }[] }[] }
    | undefined;
  const rubrics = scoring?.rubrics;
  if (!Array.isArray(rubrics)) return undefined;

  const answers: string[] = [];
  for (const rubric of rubrics) {
    for (const criterion of rubric.criteria ?? []) {
      const description = criterion?.description ?? "";
      const match = description.match(
        new RegExp(`${REFERENCE_ANSWER_PREFIX}(.+)`, "s"),
      );
      if (match) {
        answers.push(match[1].trim());
      }
    }
  }
  return answers.length > 0 ? answers.join("\n") : undefined;
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}

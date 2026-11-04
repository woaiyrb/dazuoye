import { Inject, Injectable } from "@nestjs/common";
import { WINSTON_MODULE_PROVIDER } from "nest-winston";
import { sanitize } from "isomorphic-dompurify";
import { Logger } from "winston";
import { HumanMessage } from "@langchain/core/messages";
import {
  IModerationService,
  ModerationVerdict,
} from "../interfaces/moderation.interface";
import { logAiInvocation } from "../utils/ai-invocation-log.util";
import { parseSevereCategories } from "./moderation.service";
import { DeepTutorLlmService } from "./deeptutor-llm.service";

/**
 * Content moderation routed through DeepTutor instead of the OpenAI
 * moderations endpoint. DeepTutor is asked to classify the content against the
 * same category list and answer with JSON, so the caller contract (allow /
 * allow_with_log / block_severe) is unchanged.
 *
 * The check fails open: if DeepTutor is unreachable the content is allowed,
 * matching the previous behaviour on moderation errors.
 */
const MODERATION_MODEL_KEY = "deeptutor-moderation";

const CATEGORY_LIST = [
  "harassment",
  "harassment/threatening",
  "hate",
  "hate/threatening",
  "illicit",
  "illicit/violent",
  "self-harm",
  "self-harm/instructions",
  "self-harm/intent",
  "sexual",
  "sexual/minors",
  "violence",
  "violence/graphic",
];

const MODERATION_PROMPT = `You are a content safety classifier for a school homework platform.
Classify the provided content against these categories: ${CATEGORY_LIST.join(", ")}.

Respond with JSON only, in exactly this shape:
{"categories": ["<category>", ...]}

Rules:
- Only include categories that clearly apply.
- If nothing applies, return {"categories": []}.
- Do not add commentary, explanations, or markdown fences.`;

@Injectable()
export class DeepTutorModerationService implements IModerationService {
  private readonly logger: Logger;
  private readonly severeCategories: Set<string>;

  constructor(
    private readonly deepTutor: DeepTutorLlmService,
    @Inject(WINSTON_MODULE_PROVIDER) parentLogger: Logger,
  ) {
    this.logger = parentLogger.child({
      context: DeepTutorModerationService.name,
    });
    this.severeCategories = parseSevereCategories(
      process.env.MODERATION_SEVERE_CATEGORIES ?? "",
      this.logger,
    );
  }

  async assessContent(
    content: string,
    imageUrls?: string[],
  ): Promise<ModerationVerdict> {
    const allow: ModerationVerdict = {
      action: "allow",
      flaggedCategories: [],
      severeCategories: [],
    };

    const hasText = !!content;
    const images = imageUrls ?? [];
    if (!hasText && images.length === 0) return allow;

    const flagged = new Set<string>();

    if (hasText) {
      for (const category of await this.classify(content)) {
        flagged.add(category);
      }
    }
    if (images.length > 0) {
      for (const category of await this.classify(
        "Classify the attached image(s) for unsafe content.",
        images,
      )) {
        flagged.add(category);
      }
    }

    const flaggedCategories = [...flagged]
      .filter((category) => CATEGORY_LIST.includes(category))
      .sort();
    const severeCategories = flaggedCategories.filter((category) =>
      this.severeCategories.has(category),
    );

    logAiInvocation(this.logger, {
      modelKey: MODERATION_MODEL_KEY,
      purpose: "moderation",
      prompt: content,
      response: JSON.stringify(flaggedCategories),
    });

    if (severeCategories.length > 0) {
      return { action: "block_severe", flaggedCategories, severeCategories };
    }
    if (flaggedCategories.length > 0) {
      return {
        action: "allow_with_log",
        flaggedCategories,
        severeCategories: [],
      };
    }
    return allow;
  }

  private async classify(
    text: string,
    imageUrls?: string[],
  ): Promise<string[]> {
    try {
      const result = imageUrls?.length
        ? await this.deepTutor.invokeWithImage(
            `${MODERATION_PROMPT}\n\nContent to classify follows.`,
            imageUrls,
            { temperature: 0, maxTokens: 200 },
          )
        : await this.deepTutor.invoke(
            [
              new HumanMessage(
                `${MODERATION_PROMPT}\n\nContent to classify:\n"""\n${text}\n"""`,
              ),
            ],
            { temperature: 0, maxTokens: 200 },
          );

      const match = result.content.match(/\{[\s\S]*\}/);
      if (!match) return [];

      const parsed = JSON.parse(match[0]) as { categories?: unknown };
      return Array.isArray(parsed.categories)
        ? parsed.categories.filter(
            (entry): entry is string => typeof entry === "string",
          )
        : [];
    } catch (error) {
      this.logger.error("deeptutor.moderation.failed", {
        error: error instanceof Error ? error.message : String(error),
      });
      return [];
    }
  }

  async validateContent(content: string): Promise<boolean> {
    const verdict = await this.assessContent(content);
    if (verdict.action === "allow_with_log") {
      this.logger.warn("authoring.moderation.flagged", {
        categories: verdict.flaggedCategories,
      });
    }
    return verdict.action !== "block_severe";
  }

  sanitizeContent(content: string): string {
    if (!content) return "";
    try {
      return sanitize(content);
    } catch (error) {
      this.logger.error(
        `Error sanitizing content: ${
          error instanceof Error ? error.message : "Unknown error"
        }`,
      );
      return content;
    }
  }
}

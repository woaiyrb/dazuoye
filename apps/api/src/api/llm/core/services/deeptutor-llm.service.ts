import { HumanMessage } from "@langchain/core/messages";
import { Inject, Injectable } from "@nestjs/common";
import { WINSTON_MODULE_PROVIDER } from "nest-winston";
import { Logger } from "winston";
import type { ZodTypeAny } from "zod";
import { TOKEN_COUNTER } from "../../llm.constants";
import {
  IMultimodalLlmProvider,
  LlmRequestOptions,
  LlmResponse,
  LlmStructuredResponse,
} from "../interfaces/llm-provider.interface";
import { ITokenCounter } from "../interfaces/token-counter.interface";
import { toImageDataList } from "../utils/multimodal-image.util";
import { DeepTutorClientService } from "./deeptutor-client.service";

/**
 * Provider key. Every feature assignment / AIUsage row in the database refers
 * to the model by this string.
 */
export const DEEPTUTOR_PROVIDER_KEY = "deeptutor";

interface RenderedPrompt {
  text: string;
  attachments: Array<Record<string, unknown>>;
}

/**
 * DeepTutor's application protocol is the AI provider for this application.
 *
 * Generic text work uses the `chat` capability over DeepTutor's unified
 * WebSocket. DeepTutor owns model selection, provider credentials, tools,
 * safety policy, and usage accounting. This adapter only translates prompts
 * into a turn, accumulates streamed content, and validates structured output.
 */
@Injectable()
export class DeepTutorLlmService implements IMultimodalLlmProvider {
  static readonly DEFAULT_MODEL = "deeptutor";
  readonly key = DEEPTUTOR_PROVIDER_KEY;

  private readonly logger: Logger;

  constructor(
    @Inject(TOKEN_COUNTER) private readonly tokenCounter: ITokenCounter,
    private readonly client: DeepTutorClientService,
    @Inject(WINSTON_MODULE_PROVIDER) parentLogger: Logger,
  ) {
    this.logger = parentLogger.child({ context: DeepTutorLlmService.name });
  }

  private get timeoutMs(): number {
    return Number(process.env.DEEPTUTOR_TURN_TIMEOUT_MS) || 10 * 60 * 1000;
  }

  async invoke(
    messages: HumanMessage[],
    options?: LlmRequestOptions,
  ): Promise<LlmResponse> {
    const rendered = this.renderMessages(messages);
    return this.runPrompt(rendered.text, rendered.attachments, options, "chat");
  }

  async invokeStructured<T>(
    messages: HumanMessage[],
    schema: ZodTypeAny,
    options?: LlmRequestOptions,
  ): Promise<LlmStructuredResponse<T>> {
    const formatInstruction = new HumanMessage(
      "Return exactly one valid JSON value and nothing else. " +
        "Do not wrap it in markdown fences. Preserve every required key and " +
        "use JSON null for missing optional values.",
    );
    const rendered = this.renderMessages([formatInstruction, ...messages]);
    const result = await this.runPrompt(
      rendered.text,
      rendered.attachments,
      options,
      "structured chat",
    );

    let parsedJson: unknown;
    try {
      parsedJson = JSON.parse(result.content);
    } catch {
      const fenced =
        result.content.match(/```(?:json)?\s*([\S\s]*?)```/i)?.[1] ??
        result.content;
      const match = fenced.match(/({[\S\s]*}|\[[\S\s]*])/);
      if (!match) {
        throw new Error(
          "DeepTutor structured output was not valid JSON: " +
            result.content.slice(0, 400),
        );
      }
      parsedJson = JSON.parse(match[1]);
    }

    return {
      parsed: schema.parse(parsedJson) as T,
      tokenUsage: result.tokenUsage,
    };
  }

  async invokeWithImage(
    textContent: string,
    imageData: string | string[],
    options?: LlmRequestOptions,
  ): Promise<LlmResponse> {
    const attachments = toImageDataList(imageData).map((entry, index) =>
      this.toImageAttachment(entry, index),
    );
    return this.runPrompt(textContent, attachments, options, "multimodal chat");
  }

  private async runPrompt(
    text: string,
    attachments: Array<Record<string, unknown>>,
    options: LlmRequestOptions | undefined,
    purpose: string,
  ): Promise<LlmResponse> {
    const inputTokens = this.tokenCounter.countTokens(text);
    const startedAt = Date.now();

    this.logger.info("deeptutor.invoke.start", {
      endpoint: this.client.webSocketBaseUrl,
      protocol: "turn-v2",
      purpose,
      input_tokens: inputTokens,
      input_full_length: text.length,
      input_snippet: text.slice(0, 400),
      image_count: attachments.length,
    });

    try {
      const response = await this.client.runTurn(
        {
          content: text,
          capability: "chat",
          config: {},
          language: process.env.DEEPTUTOR_RESPONSE_LANGUAGE || "zh",
          attachments,
        },
        {
          purpose,
          timeoutMs: options?.timeoutMs ?? this.timeoutMs,
        },
      );

      const content = response.content.trim();
      if (!content) {
        throw new Error("DeepTutor returned an empty response");
      }
      const outputTokens = this.tokenCounter.countTokens(content);

      this.logger.info("deeptutor.invoke.complete", {
        purpose,
        duration_ms: Date.now() - startedAt,
        output_full_length: content.length,
        output_snippet: content.slice(0, 400),
        output_tokens: outputTokens,
      });

      return {
        content,
        tokenUsage: {
          input: inputTokens,
          output: outputTokens,
        },
      };
    } catch (error) {
      this.logger.error("deeptutor.invoke.failed", {
        purpose,
        duration_ms: Date.now() - startedAt,
        error: error instanceof Error ? error.message : String(error),
      });
      throw error;
    }
  }

  private renderMessages(messages: HumanMessage[]): RenderedPrompt {
    const sections: string[] = [];
    const attachments: Array<Record<string, unknown>> = [];

    for (const [messageIndex, message] of messages.entries()) {
      const content = message.content;
      if (typeof content === "string") {
        sections.push(
          messages.length === 1
            ? content
            : `[message ${messageIndex + 1}]\n${content}`,
        );
        continue;
      }

      const textParts: string[] = [];
      for (const part of content) {
        if (typeof part === "string") {
          textParts.push(part);
          continue;
        }
        if (!part || typeof part !== "object") {
          textParts.push(String(part));
          continue;
        }
        if ("text" in part) {
          textParts.push(String(part.text));
          continue;
        }
        if ("image_url" in part) {
          const rawImage = part.image_url;
          const url =
            typeof rawImage === "string"
              ? rawImage
              : rawImage && typeof rawImage === "object" && "url" in rawImage
                ? String(rawImage.url)
                : "";
          if (url) {
            attachments.push(this.toImageAttachment(url, attachments.length));
          }
          continue;
        }
        textParts.push(JSON.stringify(part));
      }

      sections.push(
        messages.length === 1
          ? textParts.join("\n")
          : `[message ${messageIndex + 1}]\n${textParts.join("\n")}`,
      );
    }

    return { text: sections.join("\n\n").trim(), attachments };
  }

  private toImageAttachment(
    value: string,
    index: number,
  ): Record<string, unknown> {
    const filename = `image-${index + 1}`;
    if (/^https?:\/\//i.test(value)) {
      return { type: "image", url: value, filename };
    }
    if (value.startsWith("data:")) {
      const [header, base64 = ""] = value.split(",", 2);
      const mimeType = header.match(/^data:([^;]+)/i)?.[1] || "image/jpeg";
      return {
        type: "image",
        base64,
        filename: `${filename}.${this.extensionForMime(mimeType)}`,
        mime_type: mimeType,
      };
    }
    return {
      type: "image",
      base64: value,
      filename: `${filename}.jpg`,
      mime_type: "image/jpeg",
    };
  }

  private extensionForMime(mimeType: string): string {
    return (
      {
        "image/gif": "gif",
        "image/jpeg": "jpg",
        "image/png": "png",
        "image/webp": "webp",
      }[mimeType] ?? "png"
    );
  }
}

import { execFile } from "node:child_process";
import { stat } from "node:fs/promises";
import * as net from "node:net";
import { Inject, Injectable } from "@nestjs/common";
import axios from "axios";
import { WINSTON_MODULE_PROVIDER } from "nest-winston";
import WebSocket from "ws";
import { Logger } from "winston";

export const DEEPTUTOR_PROTOCOL_VERSION = "2.0";
export const DEFAULT_DEEPTUTOR_BASE_URL = "http://127.0.0.1:8001";

const KEEPALIVE_INTERVAL_MS = 20_000;
const KNOWN_DEEPTUTOR_EXECUTABLES = [
  "D:\\Python311\\Scripts\\deeptutor.exe",
  "D:\\Python\\Scripts\\deeptutor.exe",
];
const KNOWN_DEEPTUTOR_HOMES = ["D:\\deeptutor"];

export type DeepTutorHealthState = "ok" | "misconfigured" | "unreachable";

export interface DeepTutorHealth {
  state: DeepTutorHealthState;
  endpoint: string;
  checkedAt: string;
  latencyMs?: number;
  error?: string;
}

export interface DeepTutorWireEvent {
  type?: string;
  content?: string;
  message?: string;
  error_code?: string;
  retryable?: boolean;
  metadata?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface DeepTutorSocketOptions {
  path: string;
  payload: unknown;
  purpose: string;
  timeoutMs: number;
  /**
   * Add the v2 protocol_version header to outgoing commands. Raw endpoints
   * such as the quiz judge receive the legacy request shape.
   */
  protocolVersion?: string;
  keepAlive?: boolean;
  accumulateText?: boolean;
  /**
   * Complete on result instead of waiting for done. Prefer done; this exists
   * for legacy endpoints whose result is their terminal frame.
   */
  resolveOnResult?: boolean;
  recover?: boolean;
  onEvent?: (event: DeepTutorWireEvent) => void;
}

export interface DeepTutorSocketResult {
  content: string;
  result?: DeepTutorWireEvent;
  lastEvent?: DeepTutorWireEvent;
  receivedTerminalEvent: boolean;
}

export interface DeepTutorTurnRequest {
  content: string;
  capability?: string;
  config?: Record<string, unknown>;
  language?: string;
  attachments?: Array<Record<string, unknown>>;
  [key: string]: unknown;
}

/**
 * The single transport for the real DeepTutor backend.
 *
 * DeepTutor exposes its application protocol over FastAPI WebSockets at /ws.
 * It does not expose an OpenAI-compatible /v1/chat/completions endpoint by
 * default. Keeping URL resolution, authentication, recovery, frame handling,
 * and keepalive in one service prevents individual features from silently
 * drifting back to a made-up HTTP wire format.
 */
@Injectable()
export class DeepTutorClientService {
  private readonly logger: Logger;
  private recoveryInFlight: Promise<boolean> | undefined;

  constructor(@Inject(WINSTON_MODULE_PROVIDER) parentLogger: Logger) {
    this.logger = parentLogger.child({
      context: DeepTutorClientService.name,
    });
  }

  get httpBaseUrl(): string {
    return (process.env.DEEPTUTOR_BASE_URL || DEFAULT_DEEPTUTOR_BASE_URL)
      .trim()
      .replace(/\/+$/, "");
  }

  get webSocketBaseUrl(): string {
    const explicit = process.env.DEEPTUTOR_WS_URL?.trim();
    if (explicit) {
      return explicit.replace(/\/+$/, "");
    }

    try {
      const url = new URL(this.httpBaseUrl);
      url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
      url.pathname = url.pathname.replace(/\/+$/, "");
      url.search = "";
      url.hash = "";
      return url.toString().replace(/\/+$/, "");
    } catch {
      return "ws://127.0.0.1:8001";
    }
  }

  get isConfigured(): boolean {
    try {
      const url = new URL(this.httpBaseUrl);
      return url.protocol === "http:" || url.protocol === "https:";
    } catch {
      return false;
    }
  }

  /**
   * Readiness is deliberately non-mutating: health endpoints must never start
   * an external process as a side effect of a probe.
   */
  async checkReadiness(timeoutMs = 3000): Promise<DeepTutorHealth> {
    const checkedAt = new Date().toISOString();
    if (!this.isConfigured) {
      return {
        state: "misconfigured",
        endpoint: this.httpBaseUrl,
        checkedAt,
        error: "DEEPTUTOR_BASE_URL must be an http:// or https:// URL",
      };
    }

    const endpoint = this.toHttpUrl("/health/ready");
    const startedAt = Date.now();
    try {
      const response = await axios.get(endpoint, {
        timeout: timeoutMs,
        validateStatus: () => true,
        headers: this.httpHeaders(),
      });
      const latencyMs = Date.now() - startedAt;
      if (response.status >= 200 && response.status < 300) {
        return {
          state: "ok",
          endpoint,
          checkedAt,
          latencyMs,
        };
      }
      return {
        state:
          response.status === 401 || response.status === 403
            ? "misconfigured"
            : "unreachable",
        endpoint,
        checkedAt,
        latencyMs,
        error: `DeepTutor readiness returned HTTP ${response.status}`,
      };
    } catch (error) {
      return {
        state: "unreachable",
        endpoint,
        checkedAt,
        latencyMs: Date.now() - startedAt,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }

  async runTurn(
    request: DeepTutorTurnRequest,
    options: Omit<
      DeepTutorSocketOptions,
      "path" | "payload" | "protocolVersion"
    >,
  ): Promise<DeepTutorSocketResult> {
    return this.runSocket({
      ...options,
      path: "/ws",
      protocolVersion: DEEPTUTOR_PROTOCOL_VERSION,
      keepAlive: options.keepAlive ?? true,
      accumulateText: options.accumulateText ?? true,
      payload: {
        type: "start_turn",
        protocol_version: DEEPTUTOR_PROTOCOL_VERSION,
        capability: "chat",
        config: {},
        persist_user_message: false,
        capability_once: true,
        ...request,
      },
    });
  }

  runSocket(options: DeepTutorSocketOptions): Promise<DeepTutorSocketResult> {
    if (options.recover === false) {
      return this.runSocketOnce(options);
    }
    return this.runWithRecovery(options);
  }

  private async runWithRecovery(
    options: DeepTutorSocketOptions,
  ): Promise<DeepTutorSocketResult> {
    try {
      return await this.runSocketOnce(options);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (!this.isConnectionFailure(message)) {
        throw error;
      }
      if (!(await this.recoverIfBackendDown(message))) {
        throw error;
      }
      this.logger.info("deeptutor.retry_after_recovery", {
        purpose: options.purpose,
      });
      return this.runSocketOnce(options);
    }
  }

  private runSocketOnce(
    options: DeepTutorSocketOptions,
  ): Promise<DeepTutorSocketResult> {
    const endpoint = this.toWebSocketUrl(options.path);
    const logEndpoint = this.redactUrl(endpoint);

    return new Promise<DeepTutorSocketResult>((resolve, reject) => {
      const ws = new WebSocket(endpoint);
      let settled = false;
      let content = "";
      let result: DeepTutorWireEvent | undefined;
      let lastEvent: DeepTutorWireEvent | undefined;
      let keepAlive: NodeJS.Timeout | undefined;

      const finish = (error?: Error, value?: DeepTutorSocketResult) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        if (keepAlive) clearInterval(keepAlive);
        try {
          ws.close();
        } catch {
          // A socket may already be closed by the peer.
        }
        if (error) reject(error);
        else resolve(value );
      };

      const timer = setTimeout(() => {
        finish(
          new Error(
            `deeptutor.${options.purpose}.timeout after ${options.timeoutMs}ms`,
          ),
        );
      }, options.timeoutMs);

      const terminal = (event: DeepTutorWireEvent): boolean => {
        if (event.type === "done") return true;
        return options.resolveOnResult === true && event.type === "result";
      };

      ws.on("open", () => {
        const payload =
          options.protocolVersion && this.isRecord(options.payload)
            ? {
                ...options.payload,
                protocol_version: options.protocolVersion,
              }
            : options.payload;

        this.logger.info("deeptutor.socket.start", {
          purpose: options.purpose,
          endpoint: logEndpoint,
          timeout_ms: options.timeoutMs,
        });
        ws.send(JSON.stringify(payload));

        if (options.keepAlive) {
          keepAlive = setInterval(() => {
            if (ws.readyState !== WebSocket.OPEN) return;
            ws.send(
              JSON.stringify({
                type: "ping",
                protocol_version: DEEPTUTOR_PROTOCOL_VERSION,
              }),
            );
          }, KEEPALIVE_INTERVAL_MS);
          keepAlive.unref();
        }
      });

      ws.on("message", (raw: WebSocket.RawData) => {
        let event: DeepTutorWireEvent;
        try {
          event = JSON.parse(raw.toString()) as DeepTutorWireEvent;
        } catch {
          return;
        }

        lastEvent = event;
        options.onEvent?.(event);

        if (
          options.accumulateText &&
          (event.type === "content" || event.type === "text")
        ) {
          content += String(event.content ?? "");
        }
        if (event.type === "result") {
          result = event;
        }

        if (event.type === "error" || event.type === "protocol_error") {
          const description = event.content || event.message;
          const detail = [
            event.error_code ? `[${event.error_code}]` : "",
            description || (event.error_code ? "" : JSON.stringify(event)),
          ]
            .filter(Boolean)
            .join(" ")
            .slice(0, 400);
          finish(new Error(`deeptutor.${options.purpose}.failed: ${detail}`));
          return;
        }

        if (terminal(event)) {
          const fallbackContent =
            typeof event.content === "string" ? event.content : "";
          if (!content && fallbackContent) content = fallbackContent;
          this.logger.info("deeptutor.socket.complete", {
            purpose: options.purpose,
            characters: content.length,
            terminal_event: event.type,
          });
          finish(undefined, {
            content,
            result,
            lastEvent,
            receivedTerminalEvent: true,
          });
        }
      });

      ws.on("error", (error: Error) => {
        finish(
          new Error(
            `deeptutor.${options.purpose}.connection_failed: ${error.message}`,
          ),
        );
      });

      ws.on("close", () => {
        if (settled) return;
        finish(
          new Error(
            `deeptutor.${options.purpose}.connection_closed ` +
              `(${content.length} chars received)`,
          ),
        );
      });
    });
  }

  private async recoverIfBackendDown(message: string): Promise<boolean> {
    if (process.env.DEEPTUTOR_AUTO_START === "false") return false;
    this.recoveryInFlight ??= this.ensureBackendRunning().finally(() => {
      this.recoveryInFlight = undefined;
    });

    this.logger.warn("deeptutor.backend_unreachable", {
      error: message.slice(0, 240),
      action: "attempting automatic restart",
    });
    return this.recoveryInFlight;
  }

  private async ensureBackendRunning(): Promise<boolean> {
    const target = this.webSocketTarget();
    if (!target) return false;
    if (await this.isTcpReachable(target.host, target.port, 1500)) {
      return true;
    }

    const executable =
      process.env.DEEPTUTOR_EXE ||
      (await this.firstExisting(KNOWN_DEEPTUTOR_EXECUTABLES));
    if (!executable) {
      this.logger.warn(
        "deeptutor.autostart_skipped (set DEEPTUTOR_EXE to enable it)",
      );
      return false;
    }

    const home =
      process.env.DEEPTUTOR_HOME ||
      (await this.firstExisting(KNOWN_DEEPTUTOR_HOMES));

    await this.execQuietly(
      executable,
      ["stop", ...(home ? ["--home", home] : [])],
      60_000,
    );

    const arguments_ = [
      "start",
      ...(home ? ["--home", home] : []),
      "--detach",
      "--no-browser",
    ];
    this.logger.info("deeptutor.autostart", {
      executable,
      has_home: Boolean(home),
    });
    void this.execQuietly(executable, arguments_, 180_000);

    for (let attempt = 0; attempt < 40; attempt++) {
      await sleep(3000);
      if (await this.isTcpReachable(target.host, target.port, 1500)) {
        await sleep(3000);
        this.logger.info("deeptutor.autostart_ready", {
          host: target.host,
          port: target.port,
          attempts: attempt + 1,
        });
        return true;
      }
    }
    return false;
  }

  private execQuietly(
    executable: string,
    arguments_: string[],
    timeoutMs: number,
  ): Promise<void> {
    return new Promise((resolve) => {
      execFile(
        executable,
        arguments_,
        { timeout: timeoutMs, windowsHide: true },
        (error) => {
          if (error) {
            this.logger.warn("deeptutor.command_failed", {
              command: arguments_[0] ?? "",
              error: error.message,
            });
          }
          resolve();
        },
      );
    });
  }

  private async firstExisting(paths: string[]): Promise<string | undefined> {
    for (const candidate of paths) {
      try {
        await stat(candidate);
        return candidate;
      } catch {
        // Keep probing the known local locations.
      }
    }
    return undefined;
  }

  private webSocketTarget(): { host: string; port: number } | undefined {
    try {
      const url = new URL(this.webSocketBaseUrl);
      return {
        host: url.hostname,
        port: Number(
          url.port ||
            (url.protocol === "wss:" ? 443 : url.protocol === "ws:" ? 80 : 0),
        ),
      };
    } catch {
      return undefined;
    }
  }

  private isTcpReachable(
    host: string,
    port: number,
    timeoutMs: number,
  ): Promise<boolean> {
    return new Promise((resolve) => {
      const socket = net.connect({ host, port });
      const done = (reachable: boolean) => {
        socket.destroy();
        resolve(reachable);
      };
      socket.setTimeout(timeoutMs);
      socket.once("connect", () => done(true));
      socket.once("timeout", () => done(false));
      socket.once("error", () => done(false));
    });
  }

  private httpHeaders(): Record<string, string> {
    const apiKey = process.env.DEEPTUTOR_API_KEY?.trim();
    return apiKey ? { Authorization: `Bearer ${apiKey}` } : {};
  }

  private toHttpUrl(path: string): string {
    return this.joinUrl(this.httpBaseUrl, path);
  }

  private toWebSocketUrl(path: string): string {
    let base = this.webSocketBaseUrl;
    if (base.endsWith("/ws") && path.startsWith("/ws")) {
      base = base.slice(0, -3);
    }
    const endpoint = this.joinUrl(base, path);
    const apiKey = process.env.DEEPTUTOR_API_KEY?.trim();
    if (!apiKey) return endpoint;

    const url = new URL(endpoint);
    url.searchParams.set("token", apiKey);
    return url.toString();
  }

  private joinUrl(base: string, path: string): string {
    return `${base.replace(/\/+$/, "")}/${path.replace(/^\/+/, "")}`;
  }

  private redactUrl(value: string): string {
    try {
      const url = new URL(value);
      if (url.searchParams.has("token")) {
        url.searchParams.set("token", "***");
      }
      return url.toString();
    } catch {
      return value;
    }
  }

  private isConnectionFailure(message: string): boolean {
    return /econnrefused|econnreset|epipe|socket hang up|connection_closed|connection_failed/i.test(
      message,
    );
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return !!value && typeof value === "object" && !Array.isArray(value);
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

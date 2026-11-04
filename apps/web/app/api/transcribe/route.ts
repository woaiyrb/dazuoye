import { getBaseApiPath } from "@/config/constants";
import { TranscriptSegment } from "@/config/types";
import { createRateLimiter } from "@/lib/rate-limit";
import { createHash } from "node:crypto";
import { NextResponse } from "next/server";

/** DeepTutor enforces the same 25 MB limit; reject before uploading. */
const MAX_AUDIO_BYTES = 25 * 1024 * 1024;
const RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000;
const RATE_LIMIT_MAX_REQUESTS = 20;
const RATE_LIMIT_MAX_TRACKED_SESSIONS = 5000;

/** Speech-to-text uses DeepTutor's own FastAPI voice route. */
const TRANSCRIPTION_BASE_URL = (
  process.env.NEXT_PUBLIC_DEEPTUTOR_BASE_URL ||
  process.env.DEEPTUTOR_BASE_URL ||
  "http://127.0.0.1:8001"
).replace(/\/+$/, "");
const TRANSCRIPTION_URL = `${TRANSCRIPTION_BASE_URL}/api/voice/stt`;

const transcriptionRateLimiter = createRateLimiter({
  windowMs: RATE_LIMIT_WINDOW_MS,
  maxRequests: RATE_LIMIT_MAX_REQUESTS,
  maxTrackedKeys: RATE_LIMIT_MAX_TRACKED_SESSIONS,
});

function hasSessionCookie(cookieHeader: string): boolean {
  return cookieHeader
    .split(";")
    .map((part) => part.trim())
    .some(
      (part) =>
        part.startsWith("authentication=") &&
        part.length > "authentication=".length,
    );
}

/** Identifies a caller in logs and rate-limit buckets without recording who they are. */
function callerKey(userId: string): string {
  return createHash("sha256").update(userId).digest("hex").slice(0, 16);
}

/**
 * Asks the API whose session this is. The cookie only carries a session: whether
 * it is valid, and who it belongs to, is never inferred from its value.
 */
async function resolveUserId(cookieHeader: string): Promise<string | null> {
  const response = await fetch(`${getBaseApiPath("v1")}/user-session`, {
    headers: { Cookie: cookieHeader },
  });

  if (!response.ok) return null;

  const session = (await response.json()) as { userId?: unknown };
  return typeof session.userId === "string" && session.userId.length > 0
    ? session.userId
    : null;
}

export async function POST(req: Request) {
  const startedAt = Date.now();

  console.info("transcribe.request.received", {
    contentLength: req.headers.get("content-length"),
  });

  const cookieHeader = req.headers.get("cookie");
  if (!cookieHeader || !hasSessionCookie(cookieHeader)) {
    console.warn("transcribe.request.rejected", { reason: "no_session" });
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  let userId: string | null;
  try {
    userId = await resolveUserId(cookieHeader);
  } catch (error) {
    // The session service is the only thing that can authorize this call, so a
    // failure to reach it has to fail closed rather than let the request run.
    console.error("transcribe.request.failed", {
      reason: "session_check_unavailable",
      durationMs: Date.now() - startedAt,
      errorName: error instanceof Error ? error.name : typeof error,
      errorMessage: error instanceof Error ? error.message : String(error),
    });
    return NextResponse.json(
      { error: "Transcription unavailable" },
      { status: 503 },
    );
  }

  if (!userId) {
    console.warn("transcribe.request.rejected", { reason: "invalid_session" });
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const session = callerKey(userId);

  try {
    if (transcriptionRateLimiter.isRateLimited(session)) {
      console.warn("transcribe.request.rejected", {
        session,
        reason: "rate_limited",
      });
      return NextResponse.json({ error: "Too many requests" }, { status: 429 });
    }

    let audioFile: FormDataEntryValue | null = null;
    try {
      audioFile = (await req.formData()).get("audio");
    } catch (error) {
      console.warn("transcribe.request.rejected", {
        session,
        reason: "unreadable_form_data",
        errorMessage: error instanceof Error ? error.message : String(error),
      });
      return NextResponse.json({ error: "Invalid request" }, { status: 400 });
    }

    if (!audioFile || !(audioFile instanceof Blob) || audioFile.size === 0) {
      console.warn("transcribe.request.rejected", {
        session,
        reason: "missing_audio",
      });
      return NextResponse.json({ error: "Invalid request" }, { status: 400 });
    }

    if (audioFile.type && !audioFile.type.startsWith("audio/")) {
      console.warn("transcribe.request.rejected", {
        session,
        reason: "unsupported_media_type",
        mediaType: audioFile.type,
      });
      return NextResponse.json({ error: "Invalid request" }, { status: 400 });
    }

    if (audioFile.size > MAX_AUDIO_BYTES) {
      console.warn("transcribe.request.rejected", {
        session,
        reason: "audio_too_large",
        bytes: audioFile.size,
      });
      return NextResponse.json(
        { error: "Recording is too long" },
        { status: 413 },
      );
    }

    const deepTutorFormData = new FormData();
    deepTutorFormData.append("file", audioFile, "recording.webm");
    deepTutorFormData.append(
      "language",
      process.env.DEEPTUTOR_TRANSCRIPTION_LANGUAGE || "zh",
    );

    console.info("transcribe.speech_to_text.requested", {
      session,
      provider: "deeptutor",
      bytes: audioFile.size,
    });

    const apiKey = process.env.DEEPTUTOR_API_KEY?.trim();
    const response = await fetch(TRANSCRIPTION_URL, {
      method: "POST",
      headers: apiKey ? { Authorization: `Bearer ${apiKey}` } : undefined,
      body: deepTutorFormData,
    });

    if (!response.ok) {
      // Keep the upstream detail in the logs; the browser gets a generic error.
      const errorText = await response.text();
      console.error("transcribe.speech_to_text.failed", {
        session,
        status: response.status,
        durationMs: Date.now() - startedAt,
        errorText: errorText.slice(0, 500),
      });
      return NextResponse.json(
        { error: "Transcription failed" },
        { status: 502 },
      );
    }

    const upstream = (await response.json()) as { text?: unknown };
    const text = typeof upstream.text === "string" ? upstream.text : "";
    const segments: TranscriptSegment[] =
      text.length > 0
        ? [
            {
              start: 0,
              end: 0,
              text,
              avg_logprob: 0,
              no_speech_prob: 0,
            },
          ]
        : [];
    const data = { text, segments };

    console.info("transcribe.request.succeeded", {
      session,
      durationMs: Date.now() - startedAt,
      // Lengths only: the transcript is learner content.
      characters: data.text.length,
      segments: Array.isArray(data.segments) ? data.segments.length : 0,
    });

    return NextResponse.json(data);
  } catch (error) {
    console.error("transcribe.request.failed", {
      session,
      durationMs: Date.now() - startedAt,
      errorName: error instanceof Error ? error.name : typeof error,
      errorMessage: error instanceof Error ? error.message : String(error),
      stack: error instanceof Error ? error.stack : undefined,
    });
    return NextResponse.json(
      { error: "Transcription failed" },
      { status: 500 },
    );
  }
}

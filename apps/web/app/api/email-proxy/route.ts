import { timingSafeEqual } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";

type EmailProxyPayload = {
  from?: unknown;
  destination?: unknown;
  subject?: unknown;
  htmlBody?: unknown;
};

function constantTimeEquals(actual: string, expected: string): boolean {
  const actualBuffer = Buffer.from(actual);
  const expectedBuffer = Buffer.from(expected);

  if (actualBuffer.length !== expectedBuffer.length) {
    return false;
  }

  return timingSafeEqual(actualBuffer, expectedBuffer);
}

function isNonEmptyString(value: unknown, maxLength: number): value is string {
  return typeof value === "string" && value.trim().length > 0 && value.length <= maxLength;
}

function jsonResponse(body: object, status: number) {
  return NextResponse.json(body, {
    status,
    headers: {
      "Cache-Control": "no-store",
      Pragma: "no-cache",
    },
  });
}

export async function POST(request: NextRequest) {
  const expectedToken = process.env.EMAIL_PROXY_BEARER_TOKEN;
  const resendApiKey = process.env.RESEND_API_KEY;
  const configuredSender = process.env.EMAIL_PROXY_FROM;

  if (!expectedToken || !resendApiKey || !configuredSender) {
    console.error("Email proxy is not configured with all required runtime secrets");
    return jsonResponse({ error: "Service unavailable" }, 503);
  }

  const authHeader = request.headers.get("authorization") ?? "";
  const suppliedToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";

  if (!constantTimeEquals(suppliedToken, expectedToken)) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  let body: EmailProxyPayload;
  try {
    body = (await request.json()) as EmailProxyPayload;
  } catch {
    return jsonResponse({ error: "Invalid JSON payload" }, 400);
  }

  if (
    !isNonEmptyString(body.destination, 320) ||
    !isNonEmptyString(body.subject, 998) ||
    !isNonEmptyString(body.htmlBody, 250_000)
  ) {
    return jsonResponse({ error: "Invalid email payload" }, 400);
  }

  // Always use the configured sender (EMAIL_PROXY_FROM) — never accept
  // an arbitrary sender from the request body to prevent spoofing.
  const sender = configuredSender;

  try {
    const resendResponse = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${resendApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from: sender,
        to: [body.destination],
        subject: body.subject,
        html: body.htmlBody,
      }),
    });

    if (!resendResponse.ok) {
      console.error("Resend API rejected the email proxy request", {
        status: resendResponse.status,
      });
      return jsonResponse({ error: "Email delivery failed" }, 502);
    }

    const data = (await resendResponse.json()) as { id?: string };
    return jsonResponse({ success: true, id: data.id ?? null }, 200);
  } catch (error) {
    console.error("Email proxy request failed", {
      errorName: error instanceof Error ? error.name : "UnknownError",
    });
    return jsonResponse({ error: "Email delivery failed" }, 502);
  }
}

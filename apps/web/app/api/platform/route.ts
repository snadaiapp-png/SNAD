import { NextRequest, NextResponse } from "next/server";

function backendBaseUrl(): string | null {
  const value = process.env.SANAD_API_BASE_URL?.trim();
  return value ? value.replace(/\/+$/, "") : null;
}

async function proxy(request: NextRequest): Promise<NextResponse> {
  const backend = backendBaseUrl();
  const targetPath = request.nextUrl.searchParams.get("target")?.trim();

  if (!targetPath || !targetPath.startsWith("/api/v1/")) {
    return NextResponse.json({ status: 400, error: "Bad Request", message: "Invalid API target" }, { status: 400 });
  }

  if (!backend) {
    return NextResponse.json(
      { status: 503, error: "Service Unavailable", message: "SANAD_API_BASE_URL is not configured" },
      { status: 503 },
    );
  }

  const target = new URL(`${backend}${targetPath}`);
  request.nextUrl.searchParams.forEach((value, key) => {
    if (key !== "target") target.searchParams.append(key, value);
  });

  const headers = new Headers();
  const accept = request.headers.get("accept");
  const contentType = request.headers.get("content-type");
  if (accept) headers.set("accept", accept);
  if (contentType) headers.set("content-type", contentType);

  const response = await fetch(target, {
    method: request.method,
    headers,
    body: ["GET", "HEAD"].includes(request.method) ? undefined : await request.arrayBuffer(),
    cache: "no-store",
    redirect: "manual",
  });

  const responseHeaders = new Headers();
  const responseType = response.headers.get("content-type");
  const location = response.headers.get("location");
  if (responseType) responseHeaders.set("content-type", responseType);
  if (location) responseHeaders.set("location", location);

  return new NextResponse(await response.arrayBuffer(), {
    status: response.status,
    headers: responseHeaders,
  });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;

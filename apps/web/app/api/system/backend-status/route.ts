import { NextResponse } from "next/server";
import { checkBackendIntegration } from "@/lib/api";

export async function GET() {
  const result = await checkBackendIntegration();
  return NextResponse.json({
    configured: result.configured,
    reachable: result.reachable,
    statusCode: result.statusCode,
  });
}

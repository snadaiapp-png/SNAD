import { NextResponse, type NextRequest } from "next/server";

export const CRM_ROOT_ENTRY_COOKIE = "snad_crm_root_entry";

export function middleware(request: NextRequest) {
  const destination = request.nextUrl.clone();
  destination.pathname = "/crm/overview";
  destination.search = "";

  const response = NextResponse.redirect(destination, 307);
  response.cookies.set({
    name: CRM_ROOT_ENTRY_COOKIE,
    value: "1",
    path: "/",
    maxAge: 60,
    sameSite: "lax",
    secure: request.nextUrl.protocol === "https:",
  });
  return response;
}

export const config = {
  matcher: ["/crm"],
};

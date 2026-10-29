import { NextResponse, type NextRequest } from "next/server";

const SESSION_COOKIE = "ms_session";

/**
 * 仅做「是否带有会话 Cookie」的快速判断，真正的鉴权由 Java 完成；
 * Cookie 失效时前端收到 401 会再跳回登录页。
 */
export function middleware(req: NextRequest) {
  if (req.cookies.get(SESSION_COOKIE)?.value) {
    return NextResponse.next();
  }
  const url = req.nextUrl.clone();
  const next = req.nextUrl.pathname + req.nextUrl.search;
  url.pathname = "/login";
  url.search = next && next !== "/" ? `?next=${encodeURIComponent(next)}` : "";
  return NextResponse.redirect(url);
}

export const config = {
  // 排除 API 代理、静态资源与登录页（避免对上传请求体做中间件缓冲）
  matcher: ["/((?!api|demo|_next/static|_next/image|favicon.ico|login).*)"],
};

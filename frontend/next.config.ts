import type { NextConfig } from "next";

/**
 * 浏览器只访问 Next.js（同源），/api 与 /demo 由 Next 反向代理到 Java 后端，
 * 因此会话 Cookie 同源、无需 CORS。
 *
 * 注意：rewrites 的目标地址在 `next build` 时确定，Docker 构建时通过
 * 构建参数 API_INTERNAL_URL 传入（compose 中为 http://backend:8080）。
 */
const apiUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${apiUrl}/api/:path*` },
      { source: "/demo/:path*", destination: `${apiUrl}/demo/:path*` },
    ];
  },
};

export default nextConfig;

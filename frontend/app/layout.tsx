import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import { Providers } from "./providers";
import { Header } from "@/components/Header";

export const metadata: Metadata = {
  title: "MockStudio · 素材制作",
  description: "浏览模板、上传图片并提交模拟制作任务",
};

export const viewport: Viewport = {
  themeColor: "#0d0d10",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <Providers>
          <Header />
          <main className="main">{children}</main>
        </Providers>
      </body>
    </html>
  );
}

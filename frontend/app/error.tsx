"use client";

export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="page">
      <div className="empty" role="alert">
        <p>页面加载失败</p>
        <p className="muted small">{error.message || "无法连接后端服务，请稍后重试"}</p>
        <button type="button" className="btn btn-secondary" onClick={reset}>
          重试
        </button>
      </div>
    </div>
  );
}

"use client";

import { useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import type { Task } from "@/lib/api/types";
import { useTaskBoard, type TaskBoardOptions } from "@/lib/tasks/useTaskBoard";
import { IconRefresh } from "./Icons";
import { Modal } from "./Modal";
import { TaskCard } from "./TaskCard";

export function TaskBoard({
  templateId,
  title = "历史记录",
  showTemplate = false,
  pollIntervalMs,
}: {
  templateId?: string;
  title?: string;
  showTemplate?: boolean;
} & TaskBoardOptions) {
  const { user } = useAuth();
  const board = useTaskBoard(user?.id, templateId, { pollIntervalMs });
  const [preview, setPreview] = useState<Task | null>(null);

  return (
    <section className="panel board" aria-labelledby="board-title">
      <header className="board-header">
        <h2 id="board-title">{title}</h2>
        <div className="board-actions">
          {board.activeIds.length > 0 && (
            <span className="muted small" aria-live="polite">
              {board.activeIds.length} 个任务进行中
            </span>
          )}
          <button
            type="button"
            className={`icon-btn ${board.isSyncing ? "spinning" : ""}`}
            onClick={board.refresh}
            aria-label="刷新历史记录"
          >
            <IconRefresh />
          </button>
        </div>
      </header>

      {board.syncError && (
        <div className="banner banner-warn" role="alert">
          <span>
            状态同步失败：{board.syncError.message}。已保留当前任务信息，正在自动重试；后台任务不受影响。
          </span>
          <button type="button" className="btn btn-small" onClick={board.retrySync}>
            立即重试
          </button>
        </div>
      )}

      {!user || board.isLoading ? (
        <div className="task-grid" aria-busy="true" aria-label="加载中">
          {[0, 1, 2].map((i) => (
            <div key={i} className="task-card skeleton" />
          ))}
        </div>
      ) : board.loadError ? (
        <div className="empty">
          <p>历史记录加载失败：{board.loadError.message}</p>
          <button type="button" className="btn btn-secondary" onClick={board.refresh}>
            重新加载
          </button>
        </div>
      ) : board.items.length === 0 ? (
        <div className="empty">
          <p>还没有制作记录</p>
          <p className="muted small">{templateId ? "上传图片并点击「立即制作」开始第一个任务" : "去模板页挑一个开始吧"}</p>
        </div>
      ) : (
        <div className="task-grid">
          {board.items.map((t) => (
            <TaskCard key={t.id} task={t} showTemplate={showTemplate} onPreview={setPreview} />
          ))}
        </div>
      )}

      <Modal
        open={preview != null}
        onClose={() => setPreview(null)}
        title={preview ? `${preview.templateTitle} · 任务 #${preview.id}` : "预览"}
        className="modal-wide"
      >
        {preview?.resultUrl && <img className="preview-img" src={preview.resultUrl} alt="制作结果大图" />}
      </Modal>
    </section>
  );
}

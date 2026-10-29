"use client";

import { useRef, useState } from "react";
import type { Slot } from "@/lib/upload/useUploadSlots";
import { IconImagePlus, IconRefresh, IconTrash } from "./Icons";

export function UploadSlot({
  index,
  total,
  slot,
  accept,
  disabled,
  onSelect,
  onRemove,
  onRetry,
}: {
  index: number;
  total: number;
  slot: Slot;
  accept: string;
  disabled?: boolean;
  onSelect: (file: File) => void;
  onRemove: () => void;
  onRetry: () => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const label = total > 1 ? `第 ${index + 1} 张图片` : "图片";
  const pick = () => inputRef.current?.click();
  const preview = "previewUrl" in slot ? slot.previewUrl : undefined;

  return (
    <div
      className={`slot slot-${slot.status} ${dragOver ? "slot-drag" : ""}`}
      onDragOver={(e) => {
        e.preventDefault();
        setDragOver(true);
      }}
      onDragLeave={() => setDragOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDragOver(false);
        const f = e.dataTransfer.files?.[0];
        if (f && !disabled) onSelect(f);
      }}
    >
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        hidden
        data-testid={`slot-input-${index}`}
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) onSelect(f);
          e.target.value = ""; // 允许重复选择同一文件
        }}
      />

      {slot.status === "empty" ? (
        <button type="button" className="slot-empty" onClick={pick} disabled={disabled} aria-label={`上传${label}`}>
          <span className="slot-icon">
            <IconImagePlus />
          </span>
          <span>点击或拖拽上传{total > 1 ? ` · ${index + 1}/${total}` : ""}</span>
        </button>
      ) : (
        <div className="slot-filled">
          {preview && <img src={preview} alt={`${label}预览`} className="slot-preview" />}

          {slot.status === "uploading" && (
            <div className="slot-overlay" role="status" aria-live="polite">
              <span className="spinner" aria-hidden />
              <span>上传中 {Math.round(slot.progress * 100)}%</span>
              <progress max={1} value={slot.progress} aria-label={`${label}上传进度`} />
            </div>
          )}

          {slot.status === "error" && (
            <div className="slot-overlay slot-overlay-error" role="alert">
              <span>{slot.message}</span>
              <div className="slot-actions">
                {slot.file && (
                  <button type="button" className="btn btn-small" onClick={onRetry} aria-label={`重试上传${label}`}>
                    <IconRefresh width={14} height={14} /> 重试
                  </button>
                )}
                <button type="button" className="btn btn-small" onClick={pick} aria-label={`重新选择${label}`}>
                  重新选择
                </button>
              </div>
            </div>
          )}

          <div className="slot-toolbar">
            {slot.status === "ready" && <span className="badge badge-ok">已上传</span>}
            <span className="spacer" />
            {slot.status !== "error" && (
              <button type="button" className="icon-btn" onClick={pick} disabled={disabled} aria-label={`替换${label}`}>
                <IconRefresh />
              </button>
            )}
            <button type="button" className="icon-btn" onClick={onRemove} disabled={disabled} aria-label={`移除${label}`}>
              <IconTrash />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

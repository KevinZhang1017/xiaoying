"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { IconClose } from "./Icons";

/**
 * 基于原生 <dialog> 的模态框：自带焦点约束、Esc 关闭与背景遮罩。
 */
export function Modal({
  open,
  onClose,
  title,
  children,
  className,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  className?: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (open && !el.open) el.showModal();
    if (!open && el.open) el.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className={`modal ${className ?? ""}`}
      aria-labelledby={titleId}
      onClose={onClose}
      onClick={(e) => {
        // 点击遮罩关闭
        if (e.target === ref.current) onClose();
      }}
    >
      <div className="modal-body">
        <header className="modal-header">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="关闭">
            <IconClose />
          </button>
        </header>
        {children}
      </div>
    </dialog>
  );
}

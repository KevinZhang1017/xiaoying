"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import type { Template } from "@/lib/api/types";
import { useCreateTask } from "@/lib/tasks/useCreateTask";
import { useUploadSlots } from "@/lib/upload/useUploadSlots";
import { formatBytes, mimeLabel } from "@/lib/upload/validateFile";
import { IconBack, IconSpark } from "./Icons";
import { UploadSlot } from "./UploadSlot";

export function CreatePanel({ template }: { template: Template }) {
  const { user } = useAuth();
  const upload = useUploadSlots(template);
  const create = useCreateTask(user?.id);
  const submitting = useRef(false);
  const [notice, setNotice] = useState<string | null>(null);

  const canSubmit = upload.allReady && !create.isPending && user != null;
  const remaining = template.requiredImageCount - upload.readyCount;

  const label = create.isPending
    ? "提交中…"
    : upload.anyUploading
      ? "图片上传中…"
      : upload.allReady
        ? "立即制作"
        : `还需上传 ${remaining} 张图片`;

  const submit = () => {
    // ref 防止同一帧内的连续点击在状态更新前重复提交
    if (!canSubmit || submitting.current) return;
    submitting.current = true;
    setNotice(null);
    create.mutate(
      { templateId: template.id, assetIds: upload.assetIds },
      {
        onSuccess: (task) => setNotice(`已提交，任务 #${task.id} 已进入队列`),
        onSettled: () => {
          submitting.current = false;
        },
      },
    );
  };

  const onSlotChange = () => {
    if (create.isError) create.reset();
    setNotice(null);
  };

  return (
    <section className="panel create-panel" aria-labelledby="tpl-title">
      <header className="create-header">
        <Link href={`/?category=${template.categoryId}`} className="icon-btn" aria-label="返回模板列表">
          <IconBack />
        </Link>
        <h1 id="tpl-title">{template.title}</h1>
      </header>

      <div className="cover-frame">
        <img src={template.coverUrl} alt={`${template.title} 示例封面`} className="cover-img" />
      </div>

      <p className="desc">{template.description}</p>
      <ul className="requirements" aria-label="素材要求">
        <li>需要 {template.requiredImageCount} 张图片</li>
        <li>格式：{template.acceptedMimeTypes.map(mimeLabel).join(" / ")}</li>
        <li>单张不超过 {formatBytes(template.maxFileSizeBytes)}</li>
      </ul>

      <h2 className="section-label">上传图片</h2>
      <div className={`slots slots-${Math.min(template.requiredImageCount, 3)}`}>
        {upload.slots.map((slot, i) => (
          <UploadSlot
            key={i}
            index={i}
            total={template.requiredImageCount}
            slot={slot}
            accept={template.acceptedMimeTypes.join(",")}
            disabled={create.isPending}
            onSelect={(f) => {
              onSlotChange();
              upload.select(i, f);
            }}
            onRemove={() => {
              onSlotChange();
              upload.remove(i);
            }}
            onRetry={() => upload.retry(i)}
          />
        ))}
      </div>

      <div className="create-footer">
        {create.isError && (
          <p className="form-error" role="alert">
            提交失败：{create.error.message}
          </p>
        )}
        {notice && (
          <p className="form-ok" role="status">
            {notice}
          </p>
        )}
        <button
          type="button"
          className="btn btn-primary btn-block btn-lg"
          disabled={!canSubmit}
          aria-disabled={!canSubmit}
          onClick={submit}
        >
          {create.isPending ? <span className="spinner spinner-dark" aria-hidden /> : <IconSpark />}
          {label}
        </button>
      </div>
    </section>
  );
}

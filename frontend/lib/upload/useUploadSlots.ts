"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { isAbortError } from "@/lib/api/client";
import type { Asset, Template } from "@/lib/api/types";
import { uploadAsset, uploadErrorMessage } from "./uploadAsset";
import { validateFile } from "./validateFile";

/** 每个上传位独立的状态机：empty → uploading → ready | error。 */
export type Slot =
  | { status: "empty" }
  | { status: "uploading"; file: File; previewUrl: string; progress: number; uploadId: number }
  | { status: "ready"; file: File; previewUrl: string; asset: Asset }
  | { status: "error"; file?: File; previewUrl?: string; message: string };

const empty = (n: number): Slot[] => Array.from({ length: n }, () => ({ status: "empty" }) as Slot);

export function useUploadSlots(template: Pick<Template, "id" | "requiredImageCount" | "acceptedMimeTypes" | "maxFileSizeBytes">) {
  const [slots, setSlots] = useState<Slot[]>(() => empty(template.requiredImageCount));
  const slotsRef = useRef(slots);
  slotsRef.current = slots;
  const aborts = useRef(new Map<number, () => void>());
  const seq = useRef(0);

  const patch = useCallback((index: number, next: Slot | ((cur: Slot) => Slot)) => {
    setSlots((cur) => {
      const copy = cur.slice();
      copy[index] = typeof next === "function" ? next(cur[index]) : next;
      return copy;
    });
  }, []);

  /** 取消在途上传并释放本地预览地址。 */
  const release = useCallback((index: number, keepPreview?: string) => {
    aborts.current.get(index)?.();
    aborts.current.delete(index);
    const cur = slotsRef.current[index];
    if (cur && "previewUrl" in cur && cur.previewUrl && cur.previewUrl !== keepPreview) {
      URL.revokeObjectURL(cur.previewUrl);
    }
  }, []);

  const select = useCallback(
    (index: number, file: File) => {
      const reused = slotsRef.current[index];
      const reusePreview = reused && "file" in reused && reused.file === file ? reused.previewUrl : undefined;
      release(index, reusePreview);

      const error = validateFile(file, template);
      if (error) {
        if (reusePreview) URL.revokeObjectURL(reusePreview);
        patch(index, { status: "error", message: error });
        return;
      }

      const previewUrl = reusePreview ?? URL.createObjectURL(file);
      const uploadId = ++seq.current;
      patch(index, { status: "uploading", file, previewUrl, progress: 0, uploadId });

      const isCurrent = (s: Slot): s is Extract<Slot, { status: "uploading" }> =>
        s.status === "uploading" && s.uploadId === uploadId;
      const handle = uploadAsset(file, template.id, (ratio) =>
        patch(index, (s) => (isCurrent(s) ? { ...s, progress: ratio } : s)),
      );
      aborts.current.set(index, handle.abort);
      handle.promise
        .then((asset) => patch(index, (s) => (isCurrent(s) ? { status: "ready", file, previewUrl, asset } : s)))
        .catch((e) => {
          if (isAbortError(e)) return;
          patch(index, (s) =>
            isCurrent(s) ? { status: "error", file, previewUrl, message: uploadErrorMessage(e) } : s,
          );
        })
        .finally(() => {
          if (aborts.current.get(index) === handle.abort) aborts.current.delete(index);
        });
    },
    [patch, release, template],
  );

  const remove = useCallback(
    (index: number) => {
      release(index);
      patch(index, { status: "empty" });
    },
    [patch, release],
  );

  const retry = useCallback(
    (index: number) => {
      const s = slotsRef.current[index];
      if (s.status === "error" && s.file) select(index, s.file);
    },
    [select],
  );

  // 离开页面：取消全部在途上传并释放预览地址
  useEffect(() => {
    const abortMap = aborts.current;
    return () => {
      abortMap.forEach((abort) => abort());
      abortMap.clear();
      for (const s of slotsRef.current) {
        if ("previewUrl" in s && s.previewUrl) URL.revokeObjectURL(s.previewUrl);
      }
    };
  }, []);

  const readyAssets = slots.flatMap((s) => (s.status === "ready" ? [s.asset] : []));
  return {
    slots,
    select,
    remove,
    retry,
    allReady: readyAssets.length === slots.length,
    anyUploading: slots.some((s) => s.status === "uploading"),
    readyCount: readyAssets.length,
    assetIds: readyAssets.map((a) => a.id),
  };
}

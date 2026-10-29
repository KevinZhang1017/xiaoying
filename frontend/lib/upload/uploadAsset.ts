import { ApiError, networkError, toApiError } from "@/lib/api/client";
import type { Asset } from "@/lib/api/types";

export interface UploadHandle {
  promise: Promise<Asset>;
  abort: () => void;
}

/** 使用 XHR 上传以获得进度；返回可取消的句柄。 */
export function uploadAsset(file: File, templateId: string, onProgress?: (ratio: number) => void): UploadHandle {
  const xhr = new XMLHttpRequest();
  const promise = new Promise<Asset>((resolve, reject) => {
    xhr.open("POST", `/api/assets?templateId=${encodeURIComponent(templateId)}`);
    xhr.withCredentials = true;
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress?.(e.loaded / e.total);
    };
    xhr.onload = () => {
      let body: unknown = undefined;
      try {
        body = xhr.responseText ? JSON.parse(xhr.responseText) : undefined;
      } catch {
        body = undefined;
      }
      if (xhr.status >= 200 && xhr.status < 300) resolve(body as Asset);
      else reject(toApiError(xhr.status, body));
    };
    xhr.onerror = () => reject(networkError());
    xhr.onabort = () => reject(new DOMException("Upload aborted", "AbortError"));
    const form = new FormData();
    form.append("file", file);
    xhr.send(form);
  });
  return { promise, abort: () => xhr.abort() };
}

export function uploadErrorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.kind === "network" ? "网络异常，上传失败" : e.message;
  return "上传失败，请重试";
}

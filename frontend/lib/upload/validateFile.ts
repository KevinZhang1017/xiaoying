import type { Template } from "@/lib/api/types";

const MIME_LABEL: Record<string, string> = {
  "image/jpeg": "JPG",
  "image/png": "PNG",
};

export const mimeLabel = (m: string) => MIME_LABEL[m] ?? m;

export function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) {
    const mb = bytes / 1024 / 1024;
    return `${Number.isInteger(mb) ? mb : mb.toFixed(1)}MB`;
  }
  return `${Math.round(bytes / 1024)}KB`;
}

/** 前端预校验（服务端仍会再次校验）。返回错误文案，合法时返回 null。 */
export function validateFile(file: File, template: Pick<Template, "acceptedMimeTypes" | "maxFileSizeBytes">) {
  if (!template.acceptedMimeTypes.includes(file.type)) {
    return `仅支持 ${template.acceptedMimeTypes.map(mimeLabel).join(" / ")} 格式`;
  }
  if (file.size === 0) {
    return "文件为空，请重新选择";
  }
  if (file.size > template.maxFileSizeBytes) {
    return `图片不能超过 ${formatBytes(template.maxFileSizeBytes)}`;
  }
  return null;
}

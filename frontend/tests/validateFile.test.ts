import { describe, expect, it } from "vitest";
import { formatBytes, validateFile } from "@/lib/upload/validateFile";

const tpl = { acceptedMimeTypes: ["image/png"], maxFileSizeBytes: 2 * 1024 * 1024 };
const file = (type: string, size: number) => new File([new Uint8Array(size)], "f", { type });

describe("validateFile", () => {
  it("rejects types not accepted by the template", () => {
    expect(validateFile(file("image/jpeg", 10), tpl)).toBe("仅支持 PNG 格式");
    expect(validateFile(file("image/gif", 10), { ...tpl, acceptedMimeTypes: ["image/jpeg", "image/png"] })).toBe(
      "仅支持 JPG / PNG 格式",
    );
  });

  it("rejects empty and oversized files", () => {
    expect(validateFile(file("image/png", 0), tpl)).toBe("文件为空，请重新选择");
    expect(validateFile(file("image/png", 2 * 1024 * 1024 + 1), tpl)).toBe("图片不能超过 2MB");
  });

  it("accepts valid files", () => {
    expect(validateFile(file("image/png", 1024), tpl)).toBeNull();
  });

  it("formats sizes", () => {
    expect(formatBytes(5 * 1024 * 1024)).toBe("5MB");
    expect(formatBytes(1.5 * 1024 * 1024)).toBe("1.5MB");
    expect(formatBytes(2048)).toBe("2KB");
  });
});

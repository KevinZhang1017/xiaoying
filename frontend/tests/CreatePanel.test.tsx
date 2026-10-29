import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Task } from "@/lib/api/types";
import { deferred, makeTask, postcardTemplate } from "./factories";

const { USER } = vi.hoisted(() => ({ USER: { id: 1, email: "alice@demo.local", displayName: "Alice" } }));

vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({ user: USER, status: "authenticated", logout: vi.fn() }),
}));
vi.mock("@/lib/api/endpoints", () => ({ createTask: vi.fn() }));
vi.mock("@/lib/upload/uploadAsset", () => ({
  uploadAsset: vi.fn(),
  uploadErrorMessage: (e: Error) => e.message,
}));

import { createTask } from "@/lib/api/endpoints";
import { uploadAsset } from "@/lib/upload/uploadAsset";
import { CreatePanel } from "@/components/CreatePanel";

const createMock = vi.mocked(createTask);
const uploadMock = vi.mocked(uploadAsset);

function renderPanel() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <CreatePanel template={postcardTemplate} />
    </QueryClientProvider>,
  );
}

const pick = (index: number, file: File) =>
  fireEvent.change(screen.getByTestId(`slot-input-${index}`), { target: { files: [file] } });

const png = (name = "a.png") => new File([new Uint8Array(10)], name, { type: "image/png" });

let nextAssetId = 100;
beforeEach(() => {
  createMock.mockReset();
  uploadMock.mockReset();
  uploadMock.mockImplementation((file: File) => ({
    promise: Promise.resolve({
      id: nextAssetId++,
      url: "/api/assets/1/content",
      mimeType: file.type,
      sizeBytes: file.size,
      originalFilename: file.name,
    }),
    abort: vi.fn(),
  }));
});

describe("CreatePanel", () => {
  it("blocks submission until every slot holds an uploaded image", async () => {
    renderPanel();
    const submit = screen.getByRole("button", { name: /还需上传 2 张图片/ });
    expect(submit).toBeDisabled();

    // 不合法的类型在前端被拦截，不发起上传
    pick(0, new File(["x"], "a.gif", { type: "image/gif" }));
    expect(await screen.findByText("仅支持 JPG / PNG 格式")).toBeInTheDocument();
    expect(uploadMock).not.toHaveBeenCalled();

    pick(0, png());
    await screen.findByRole("button", { name: /还需上传 1 张图片/ });
    pick(1, new File([new Uint8Array(10)], "b.jpg", { type: "image/jpeg" }));
    expect(await screen.findByRole("button", { name: /立即制作/ })).toBeEnabled();
    expect(uploadMock).toHaveBeenCalledTimes(2);
  });

  it("submits once, shows the submitting state, and re-enables after the task is accepted", async () => {
    const pending = deferred<Task>();
    createMock.mockReturnValue(pending.promise);
    renderPanel();
    pick(0, png("a.png"));
    pick(1, png("b.png"));
    const submit = await screen.findByRole("button", { name: /立即制作/ });

    fireEvent.click(submit);
    fireEvent.click(submit); // 连续点击
    await waitFor(() => expect(createMock).toHaveBeenCalledTimes(1));
    expect(createMock).toHaveBeenCalledWith("demo-postcard", [expect.any(Number), expect.any(Number)]);
    expect(await screen.findByRole("button", { name: /提交中/ })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: /提交中/ }));
    expect(createMock).toHaveBeenCalledTimes(1);

    pending.resolve(makeTask(99, "PENDING"));
    expect(await screen.findByText("已提交，任务 #99 已进入队列")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("button", { name: /立即制作/ })).toBeEnabled());
  });

  it("shows upload failures on the slot with a retry action", async () => {
    uploadMock.mockImplementationOnce(() => ({
      promise: Promise.reject(new Error("网络异常，上传失败")),
      abort: vi.fn(),
    }));
    renderPanel();
    pick(0, png());
    expect(await screen.findByText("网络异常，上传失败")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "重试上传第 1 张图片" }));
    await screen.findByRole("button", { name: /还需上传 1 张图片/ });
  });

  it("removing an image makes the slot empty again", async () => {
    renderPanel();
    pick(0, png());
    await screen.findByRole("button", { name: /还需上传 1 张图片/ });
    fireEvent.click(screen.getByRole("button", { name: "移除第 1 张图片" }));
    expect(screen.getByRole("button", { name: "上传第 1 张图片" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /还需上传 2 张图片/ })).toBeDisabled();
  });
});

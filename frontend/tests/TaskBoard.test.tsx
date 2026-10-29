import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import type { TaskPage } from "@/lib/api/types";
import { deferred, makeTask, sleep } from "./factories";

const { USER } = vi.hoisted(() => ({ USER: { id: 1, email: "alice@demo.local", displayName: "Alice" } }));

vi.mock("@/lib/api/endpoints", () => ({
  listTasks: vi.fn(),
  getTasksByIds: vi.fn(),
}));
vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({ user: USER, status: "authenticated", logout: vi.fn() }),
}));

import { getTasksByIds, listTasks } from "@/lib/api/endpoints";
import { TaskBoard } from "@/components/TaskBoard";

const listMock = vi.mocked(listTasks);
const pollMock = vi.mocked(getTasksByIds);
const page = (...items: ReturnType<typeof makeTask>[]): TaskPage => ({ items, nextCursor: null });

function renderBoard() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  qc.setQueryData(["me"], USER);
  const utils = render(
    <QueryClientProvider client={qc}>
      <TaskBoard pollIntervalMs={40} />
    </QueryClientProvider>,
  );
  return { qc, ...utils };
}

const card = (id: number) => screen.getByTestId(`task-${id}`);

beforeEach(() => {
  listMock.mockReset();
  pollMock.mockReset();
});

describe("TaskBoard", () => {
  it("tracks several tasks independently and stops polling each one once it finishes", async () => {
    listMock.mockResolvedValue(page(makeTask(2, "PENDING"), makeTask(1, "RUNNING", { stage: "GENERATE" })));
    let call = 0;
    pollMock.mockImplementation(async (ids) => {
      call += 1;
      if (call === 1) {
        return page(
          makeTask(1, "FAILED", { stage: "GENERATE", errorMessage: "模拟失败：已配置在「模拟生成」阶段失败" }),
          makeTask(2, "RUNNING", { stage: "ASSET_CHECK" }),
        );
      }
      return page(...ids.map((id) => makeTask(id, "SUCCEEDED")));
    });

    renderBoard();

    await waitFor(() => expect(within(card(1)).getByText("制作失败")).toBeInTheDocument());
    expect(within(card(1)).getByText(/模拟失败/)).toBeInTheDocument();
    await waitFor(() =>
      expect(within(card(2)).getByRole("img", { name: "任务 #2 的制作结果" })).toBeInTheDocument(),
    );
    // A 的卡片没有被 B 的结果污染
    expect(within(card(1)).queryByRole("img", { name: /制作结果/ })).toBeNull();

    // 第一次之后的轮询只包含仍未结束的任务 2
    expect(pollMock.mock.calls[0][0]).toEqual([1, 2]);
    for (const [ids] of pollMock.mock.calls.slice(1)) expect(ids).toEqual([2]);

    // 全部结束后不再轮询
    const count = pollMock.mock.calls.length;
    await sleep(150);
    expect(pollMock.mock.calls.length).toBe(count);
  });

  it("treats a failing status query as a sync problem, not as a failed task", async () => {
    listMock.mockResolvedValue(page(makeTask(1, "RUNNING", { stage: "GENERATE" })));
    pollMock.mockRejectedValue(new ApiError("network", 0, "NETWORK_ERROR", "网络异常，无法连接服务器"));

    renderBoard();

    const banner = await screen.findByRole("alert");
    expect(banner).toHaveTextContent("状态同步失败");
    expect(within(card(1)).getByText("处理中")).toBeInTheDocument();
    expect(within(card(1)).queryByText("制作失败")).toBeNull();

    // 恢复后可以继续跟踪并得到结果
    pollMock.mockResolvedValue(page(makeTask(1, "SUCCEEDED")));
    fireEvent.click(screen.getByRole("button", { name: "立即重试" }));
    await waitFor(() =>
      expect(within(card(1)).getByRole("img", { name: "任务 #1 的制作结果" })).toBeInTheDocument(),
    );
    await waitFor(() => expect(screen.queryByText(/状态同步失败/)).toBeNull());
  });

  it("does not poll when there is no unfinished task", async () => {
    listMock.mockResolvedValue(page(makeTask(1, "SUCCEEDED"), makeTask(2, "FAILED", { errorMessage: "x" })));
    renderBoard();
    await screen.findByTestId("task-1");
    await sleep(100);
    expect(pollMock).not.toHaveBeenCalled();
  });

  it("stops polling after unmount", async () => {
    listMock.mockResolvedValue(page(makeTask(1, "RUNNING")));
    pollMock.mockImplementation(async () => page(makeTask(1, "RUNNING")));
    const { unmount } = renderBoard();
    await waitFor(() => expect(pollMock.mock.calls.length).toBeGreaterThanOrEqual(2));
    unmount();
    const count = pollMock.mock.calls.length;
    await sleep(150);
    expect(pollMock.mock.calls.length).toBe(count);
  });

  it("discards a late response that belongs to a previous user", async () => {
    listMock.mockResolvedValue(page(makeTask(1, "RUNNING")));
    const pending = deferred<TaskPage>();
    pollMock.mockImplementationOnce(() => pending.promise).mockImplementation(() => new Promise(() => {}));
    const { qc } = renderBoard();
    await waitFor(() => expect(pollMock).toHaveBeenCalled());

    // 请求在途期间切换了用户
    act(() => {
      qc.setQueryData(["me"], { id: 2, email: "bob@demo.local", displayName: "Bob" });
    });
    await act(async () => {
      pending.resolve(page(makeTask(1, "SUCCEEDED")));
      await sleep(10);
    });

    expect(within(card(1)).getByText("处理中")).toBeInTheDocument();
    expect(within(card(1)).queryByRole("img", { name: /制作结果/ })).toBeNull();
  });

  it("shows a recoverable error when history cannot be loaded", async () => {
    listMock.mockRejectedValueOnce(new ApiError("http", 503, "HTTP_503", "服务暂时不可用，请稍后重试"));
    renderBoard();
    expect(await screen.findByText(/历史记录加载失败/)).toBeInTheDocument();
    listMock.mockResolvedValue(page());
    fireEvent.click(screen.getByRole("button", { name: "重新加载" }));
    expect(await screen.findByText("还没有制作记录")).toBeInTheDocument();
  });
});

import { describe, expect, it } from "vitest";
import { activeTaskIds, insertTask, mergeTask, mergeTaskList } from "@/lib/tasks/mergeTask";
import { makeTask } from "./factories";

describe("mergeTask", () => {
  it("accepts a newer snapshot", () => {
    const prev = makeTask(1, "RUNNING");
    const next = makeTask(1, "SUCCEEDED");
    expect(mergeTask(prev, next)).toBe(next);
  });

  it("never moves a terminal task back to processing", () => {
    const done = makeTask(1, "SUCCEEDED");
    const lateRunning = makeTask(1, "RUNNING"); // 更晚的时间戳也不行
    expect(mergeTask(done, lateRunning)).toBe(done);
    const failed = makeTask(1, "FAILED");
    expect(mergeTask(failed, makeTask(1, "PENDING"))).toBe(failed);
  });

  it("drops out-of-order (older) responses", () => {
    const older = makeTask(1, "RUNNING", { stage: "ASSET_CHECK" });
    const newer = makeTask(1, "RUNNING", { stage: "GENERATE" });
    expect(mergeTask(newer, older)).toBe(newer);
  });

  it("never merges data of a different task", () => {
    const a = makeTask(1, "RUNNING");
    expect(mergeTask(a, makeTask(2, "SUCCEEDED"))).toBe(a);
  });
});

describe("mergeTaskList", () => {
  it("updates only the matching tasks and keeps others untouched", () => {
    const a = makeTask(1, "RUNNING");
    const b = makeTask(2, "RUNNING");
    const c = makeTask(3, "SUCCEEDED");
    const aFailed = makeTask(1, "FAILED", { stage: "GENERATE", errorMessage: "boom" });
    const out = mergeTaskList([a, b, c], [aFailed, makeTask(99, "SUCCEEDED")]);
    expect(out[0]).toBe(aFailed);
    expect(out[1]).toBe(b);
    expect(out[2]).toBe(c);
    expect(out).toHaveLength(3);
  });

  it("returns the same array reference when nothing changes", () => {
    const list = [makeTask(1, "SUCCEEDED")];
    expect(mergeTaskList(list, [makeTask(1, "RUNNING")])).toBe(list);
  });
});

describe("helpers", () => {
  it("insertTask prepends new tasks and merges existing ones", () => {
    const a = makeTask(1, "RUNNING");
    const b = makeTask(2, "PENDING");
    expect(insertTask([a], b).map((t) => t.id)).toEqual([2, 1]);
    const a2 = makeTask(1, "SUCCEEDED");
    expect(insertTask([b, a], a2)[1]).toBe(a2);
  });

  it("activeTaskIds returns sorted ids of unfinished tasks", () => {
    const ids = activeTaskIds([
      makeTask(5, "RUNNING"),
      makeTask(2, "SUCCEEDED"),
      makeTask(3, "PENDING"),
      makeTask(1, "FAILED"),
    ]);
    expect(ids).toEqual([3, 5]);
  });
});

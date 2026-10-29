import type { Metadata } from "next";
import { TaskBoard } from "@/components/TaskBoard";

export const metadata: Metadata = { title: "我的记录 · MockStudio" };

export default function HistoryPage() {
  return (
    <div className="page">
      <TaskBoard title="我的制作记录" showTemplate />
    </div>
  );
}

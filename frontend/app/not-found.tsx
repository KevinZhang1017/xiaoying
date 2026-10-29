import Link from "next/link";

export default function NotFound() {
  return (
    <div className="page">
      <div className="empty">
        <p>页面不存在</p>
        <Link href="/" className="btn btn-secondary">
          返回模板列表
        </Link>
      </div>
    </div>
  );
}

import Link from "next/link";

export default function TemplateNotFound() {
  return (
    <div className="page">
      <div className="empty">
        <p>模板不存在或已下线</p>
        <Link href="/" className="btn btn-secondary">
          返回模板列表
        </Link>
      </div>
    </div>
  );
}

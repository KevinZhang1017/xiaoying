import Link from "next/link";
import { fetchCategories, fetchTemplates } from "@/lib/api/server";

export const dynamic = "force-dynamic";

/** 模板列表（UI-01）：服务端组件直接从 Java 读取分类与模板。 */
export default async function HomePage({ searchParams }: { searchParams: Promise<{ category?: string }> }) {
  const { category } = await searchParams;
  const active = category ?? "";
  const [categories, templates] = await Promise.all([fetchCategories(), fetchTemplates(active || undefined)]);

  return (
    <div className="page">
      <nav className="tabs" aria-label="模板分类">
        <Link href="/" className="tab" aria-current={active === "" ? "page" : undefined}>
          全部
        </Link>
        {categories.map((c) => (
          <Link
            key={c.id}
            href={`/?category=${encodeURIComponent(c.id)}`}
            className="tab"
            aria-current={active === c.id ? "page" : undefined}
          >
            {c.name}
          </Link>
        ))}
      </nav>

      {templates.length === 0 ? (
        <div className="empty">
          <p>该分类下暂无模板</p>
          <Link href="/" className="btn btn-secondary">
            查看全部模板
          </Link>
        </div>
      ) : (
        <ul className="template-grid">
          {templates.map((t) => (
            <li key={t.id}>
              <Link href={`/templates/${encodeURIComponent(t.id)}`} className="template-card">
                <img src={t.coverUrl} alt="" loading="lazy" />
                <span className="template-card-info">
                  <span className="template-card-title">{t.title}</span>
                  <span className="template-card-sub">
                    {t.categoryName} · {t.requiredImageCount} 张图
                  </span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

import { notFound } from "next/navigation";
import { fetchTemplate } from "@/lib/api/server";
import { TemplateWorkspace } from "@/components/TemplateWorkspace";

export const dynamic = "force-dynamic";

export default async function TemplatePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const template = await fetchTemplate(id);
  if (!template) notFound();
  return <TemplateWorkspace template={template} />;
}

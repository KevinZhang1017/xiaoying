/**
 * 统一的请求封装：把失败归类为
 * - network：无法连接（断网、代理/后端不可达）
 * - unauthorized：401，需要重新登录
 * - http：服务端返回的其它错误（带业务错误码）
 *
 * 注意：这些都是「请求异常」，与后台任务的执行失败（task.status === "FAILED"）是两回事。
 */
export type ApiErrorKind = "network" | "unauthorized" | "http";

export class ApiError extends Error {
  constructor(
    public readonly kind: ApiErrorKind,
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function isAbortError(e: unknown): boolean {
  return e instanceof DOMException ? e.name === "AbortError" : (e as { name?: string })?.name === "AbortError";
}

interface ErrorBody {
  code?: string;
  message?: string;
  details?: Record<string, unknown>;
}

export function toApiError(status: number, body: unknown): ApiError {
  const b = (body ?? {}) as ErrorBody;
  const code = b.code ?? `HTTP_${status}`;
  const message =
    b.message ?? (status >= 500 ? "服务暂时不可用，请稍后重试" : status === 404 ? "资源不存在" : "请求失败");
  return new ApiError(status === 401 ? "unauthorized" : "http", status, code, message, b.details);
}

export const networkError = () => new ApiError("network", 0, "NETWORK_ERROR", "网络异常，无法连接服务器");

type ApiInit = Omit<RequestInit, "body"> & { json?: unknown; body?: BodyInit };

export async function apiFetch<T>(path: string, init: ApiInit = {}): Promise<T> {
  const { json, headers, ...rest } = init;
  let res: Response;
  try {
    res = await fetch(path, {
      ...rest,
      credentials: "same-origin",
      headers: json !== undefined ? { "Content-Type": "application/json", ...headers } : headers,
      body: json !== undefined ? JSON.stringify(json) : init.body,
    });
  } catch (e) {
    if (isAbortError(e)) throw e;
    throw networkError();
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let data: unknown = undefined;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = undefined;
    }
  }
  if (!res.ok) throw toApiError(res.status, data);
  return data as T;
}

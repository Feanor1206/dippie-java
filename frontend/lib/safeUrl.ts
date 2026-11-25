export function safeHttpUrl(value: string | null | undefined): string | undefined {
  const url = value?.trim();
  if (!url || !/^https?:\/\//i.test(url) || /[\\\s\u0000-\u001f\u007f]/.test(url)) return undefined;
  try {
    return new URL(url).hostname ? url : undefined;
  } catch {
    return undefined;
  }
}

export function safeImageUrl(value: string | null | undefined): string | undefined {
  const url = value?.trim();
  if (!url || /[\\\u0000-\u001f\u007f]/.test(url)) return undefined;
  if (url.startsWith('/') && !url.startsWith('//')) return url;
  const external = safeHttpUrl(url);
  return external && /^https:/i.test(external) ? external : undefined;
}

export function resolveStreamUrl(streamUrl: string): string {
  const url = safeImageUrl(streamUrl);
  return url && (!url.startsWith('/') || url.startsWith('/api/')) ? url : '';
}

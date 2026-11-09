import { useState } from "react";
import { Film } from "lucide-react";
export function safeWebUrl(value?: string | null) {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    return ["https:", "http:"].includes(url.protocol) ? url.href : undefined;
  } catch {
    return undefined;
  }
}
export function MoviePoster({
  title,
  url,
}: {
  title: string;
  url?: string | null;
}) {
  const [failedUrl, setFailedUrl] = useState<string>();
  const source = safeWebUrl(url);
  return (
    <div className="movie-poster">
      {source && source !== failedUrl ? (
        <img
          src={source}
          alt={`Poster ${title}`}
          loading="lazy"
          onError={() => setFailedUrl(source)}
        />
      ) : (
        <div className="poster-fallback">
          <Film size={32} />
          <strong>{title}</strong>
          <span>Chưa có poster</span>
        </div>
      )}
    </div>
  );
}

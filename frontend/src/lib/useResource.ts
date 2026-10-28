import { useEffect, useState } from 'react';
export function useResource<T>(loader: () => Promise<T>, deps: readonly unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(null);
    loader().then(value => { if (active) setData(value); }).catch((cause: unknown) => { if (active) { setData(null); setError(cause instanceof Error ? cause.message : 'Error desconocido'); } }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
    // La lista deps debe contener todas las variables leídas por loader.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);
  return { data, error, loading, reload: () => setVersion(v => v + 1) };
}

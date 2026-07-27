// useDebouncedSearch(query, delay) — returns a debounced copy of `query`.
import { useEffect, useState } from 'react';

export function useDebouncedSearch(query, delay = 300) {
  const [debounced, setDebounced] = useState(query);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(query), delay);
    return () => clearTimeout(id);
  }, [query, delay]);

  return debounced;
}

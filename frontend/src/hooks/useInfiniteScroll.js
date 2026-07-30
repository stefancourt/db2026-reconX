// TICKET-ADV118 — useInfiniteScroll: invokes loadMore() when sentinel is visible.
import { useEffect, useRef } from 'react';

export function useInfiniteScroll(loadMore, { rootMargin = '200px' } = {}) {
  const sentinelRef = useRef(null);
  const loadMoreRef = useRef(loadMore);

  // Keep the ref in sync with the latest callback without recreating the observer.
  useEffect(() => {
    loadMoreRef.current = loadMore;
  }, [loadMore]);

  useEffect(() => {
    if (!sentinelRef.current) return undefined;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) loadMoreRef.current();
      },
      { rootMargin, threshold: 0.1 }
    );
    observer.observe(sentinelRef.current);
    return () => observer.disconnect();
  }, [rootMargin]);

  return sentinelRef;
}

// TICKET-ADV118 — useInfiniteScroll: invokes loadMore() when sentinel is visible.
import { useRef } from 'react';

export function useInfiniteScroll(loadMore) {
  const sentinelRef = useRef(null);

  // TODO(TICKET-ADV118): in a useEffect, create an IntersectionObserver that
  //                     calls loadMore() when entries[0].isIntersecting.
  //                     Observe sentinelRef.current. Disconnect in cleanup.

  return sentinelRef;
}

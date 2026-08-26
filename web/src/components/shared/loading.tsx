import { Skeleton } from "@/components/ui/skeleton";

/** Skeleton rows for a table body. */
export function TableSkeleton({ rows = 5, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 rounded-lg border p-4"
          style={{ animationDelay: `${i * 50}ms` }}
        >
          {Array.from({ length: columns }).map((__, j) => (
            <Skeleton
              key={j}
              className={`h-4 skeleton-shimmer ${j === 0 ? "w-1/3" : "w-1/6"}`}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

/** Skeleton for a page of stat cards. */
export function CardsSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className="rounded-xl border p-5"
          style={{ animationDelay: `${i * 80}ms` }}
        >
          <div className="flex items-center gap-4">
            <Skeleton className="h-11 w-11 shrink-0 rounded-xl skeleton-shimmer" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-3 w-20 skeleton-shimmer" />
              <Skeleton className="h-7 w-14 skeleton-shimmer" />
              <Skeleton className="h-3 w-24 skeleton-shimmer" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

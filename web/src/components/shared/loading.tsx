import { Skeleton } from "@/components/ui/skeleton";

/** Skeleton rows for a table body. */
export function TableSkeleton({ rows = 5, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex items-center gap-4 rounded-lg border p-4">
          {Array.from({ length: columns }).map((__, j) => (
            <Skeleton key={j} className={`h-4 ${j === 0 ? "w-1/3" : "w-1/6"}`} />
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
        <Skeleton key={i} className="h-28 rounded-xl" />
      ))}
    </div>
  );
}

"use client";

import { useEffect, useRef, useState } from "react";

/**
 * Animates a number from 0 to `target` over `durationMs` using
 * an easing function. Renders the current value as children.
 */
export function AnimatedCounter({
  target,
  durationMs = 1200,
  className,
}: {
  target: number;
  durationMs?: number;
  className?: string;
}) {
  const [display, setDisplay] = useState(0);
  const rafRef = useRef<number>(0);
  const startRef = useRef<number>(0);

  useEffect(() => {
    if (target === 0) {
      setDisplay(0);
      return;
    }

    startRef.current = performance.now();

    const animate = (now: number) => {
      const elapsed = now - startRef.current;
      const progress = Math.min(elapsed / durationMs, 1);
      // easeOutExpo for a snappy deceleration
      const eased = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress);
      setDisplay(Math.round(eased * target));

      if (progress < 1) {
        rafRef.current = requestAnimationFrame(animate);
      }
    };

    rafRef.current = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(rafRef.current);
  }, [target, durationMs]);

  return <span className={className}>{display.toLocaleString()}</span>;
}

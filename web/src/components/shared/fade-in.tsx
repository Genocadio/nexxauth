"use client";

import { motion } from "framer-motion";
import type { ReactNode } from "react";

interface FadeInProps {
  children: ReactNode;
  className?: string;
  delay?: number;
  direction?: "up" | "down" | "none";
}

/** Subtle entrance animation shared by every page section. */
export function FadeIn({ children, className, delay = 0, direction = "up" }: FadeInProps) {
  const initial =
    direction === "none"
      ? { opacity: 0 }
      : direction === "down"
        ? { opacity: 0, y: -12 }
        : { opacity: 0, y: 12 };

  return (
    <motion.div
      className={className}
      initial={initial}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay, ease: [0.25, 0.46, 0.45, 0.94] }}
    >
      {children}
    </motion.div>
  );
}

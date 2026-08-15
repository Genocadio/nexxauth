"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function ConsoleIndexPage() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/console/organisations");
  }, [router]);
  return null;
}

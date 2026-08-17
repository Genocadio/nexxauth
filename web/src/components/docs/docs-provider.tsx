"use client";

import { createContext, useContext } from "react";
import type { DocumentationContext } from "@/types/docs";

const DocumentationContext = createContext<DocumentationContext | null>(null);

export function DocumentationProvider({
  children,
  context,
}: {
  children: React.ReactNode;
  context: DocumentationContext;
}) {
  return (
    <DocumentationContext.Provider value={context}>
      {children}
    </DocumentationContext.Provider>
  );
}

export function useDocs() {
  const context = useContext(DocumentationContext);
  if (!context) {
    throw new Error("useDocs must be used within a DocumentationProvider");
  }
  return context;
}

import { defineConfig } from "vitest/config";
export default defineConfig({
  resolve: {
    alias: {
      "@": import.meta.dirname + "/src",
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    exclude: ["node_modules", "e2e"],
    setupFiles: ["./src/__tests__/setup.ts"],
  },
});

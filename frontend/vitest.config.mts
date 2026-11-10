import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  // The "@/..." imports of tsconfig.json.
  resolve: { tsconfigPaths: true },
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    // "server-only" throws when imported outside a server bundle; in unit tests it is a no-op.
    alias: { "server-only": new URL("./src/test/server-only.ts", import.meta.url).pathname },
  },
});

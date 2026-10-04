import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      // __dirname does not exist in an ES module, which this file now is.
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});

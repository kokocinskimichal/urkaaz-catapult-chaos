import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const apiPort = process.env.STUDIO_PORT ?? "4310";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { "@studio/contracts": fileURLToPath(new URL("../server/src/contracts", import.meta.url)) },
  },
  server: {
    host: "127.0.0.1",
    port: 5180,
    strictPort: true,
    proxy: { "/api": `http://127.0.0.1:${apiPort}` },
  },
});

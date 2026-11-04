import fs from "node:fs";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const javaBackend = process.env.VUE_JAVA_BACKEND || "http://127.0.0.1:8080";
const pidFile = process.env.VUE_PID_FILE;

const pidPlugin = {
  name: "mark-vue-pid",
  configureServer(server) {
    if (!pidFile) return;
    fs.writeFileSync(pidFile, String(process.pid), "utf8");
    const cleanup = () => {
      try {
        fs.rmSync(pidFile, { force: true });
      } catch {
        // The process may already have lost filesystem access.
      }
    };
    server.httpServer?.once("close", cleanup);
    process.once("exit", cleanup);
  },
};

export default defineConfig({
  plugins: [vue(), pidPlugin],
  server: {
    port: 3020,
    strictPort: true,
    proxy: {
      "/api": {
        target: javaBackend,
        changeOrigin: true,
      },
      "/java-api": {
        target: javaBackend,
        changeOrigin: true,
      },
    },
  },
});

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 1004,
    // A tunnel presents its own hostname, which Vite refuses unless it is named here. A leading dot
    // covers every subdomain, so a new tunnel address needs no edit.
    allowedHosts: ['.ngrok-free.dev', '.ngrok-free.app', '.ngrok.app'],
    proxy: {
      // Same origin in development as in the container, so the session cookie stays first-party.
      '/api': {
        target: 'http://localhost:1000',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      // main.tsx mounts the app and types.ts declares types; neither holds behaviour to cover.
      exclude: [
        'src/main.tsx',
        'src/vite-env.d.ts',
        'src/auth/types.ts',
        'src/**/*.test.{ts,tsx}',
        'src/api/generated/**',
      ],
      thresholds: {
        lines: 80,
        statements: 80,
        functions: 80,
        branches: 80,
      },
    },
  },
});

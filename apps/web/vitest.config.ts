import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'node',
    // Global setup runs before every test file. Polyfills matchMedia etc.
    // for tests that opt into the jsdom environment.
    setupFiles: ['./vitest.setup.ts'],
    include: ['lib/**/*.test.ts', 'lib/**/*.test.tsx', 'app/**/*.test.ts', 'app/**/*.test.tsx', 'components/**/*.test.ts', 'components/**/*.test.tsx'],
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, '.'),
    },
  },
});

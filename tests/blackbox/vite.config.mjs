import { defineConfig } from '../../frontend/node_modules/vite/dist/node/index.js';
import react from '../../frontend/node_modules/@vitejs/plugin-react/dist/index.js';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.dirname(fileURLToPath(import.meta.url));
export default defineConfig({
  root: path.join(root, 'web'), envDir: false,
  resolve: { alias: {
    'react': path.resolve(root, '../../frontend/node_modules/react'),
    'react-dom': path.resolve(root, '../../frontend/node_modules/react-dom'),
  } },
  plugins: [react(), {
    name: 'blackbox-test-identities-only',
    configureServer(server) {
      server.middlewares.use('/__blackbox/identities', (_req, res) => {
        res.setHeader('Content-Type', 'application/json'); res.setHeader('Cache-Control', 'no-store');
        res.end(fs.readFileSync(process.env.BB_IDENTITIES_FILE));
      });
    },
  }],
  server: {
    host: '127.0.0.1', port: Number(process.env.BB_WEB_PORT || 18081), strictPort: true,
    fs: { allow: [path.resolve(root, '../..')] },
    proxy: { '/api': { target: process.env.BB_API_URL || 'http://127.0.0.1:18080' } },
  },
});

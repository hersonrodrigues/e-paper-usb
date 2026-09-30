import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const root = new URL('./', import.meta.url);
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' };
const allowed = new Set(['index.html', 'src/app.js', 'src/conversion.js', 'src/serial.js', 'src/imagetousb40.js', 'src/style.css']);
const port = Number(process.env.PORT || 5173);
createServer(async (req, res) => {
  try {
    const path = new URL(req.url, 'http://localhost').pathname.slice(1) || 'index.html';
    if (!['GET', 'HEAD'].includes(req.method) || !allowed.has(path)) {
      res.writeHead(404).end('Not found'); return;
    }
    const bytes = await readFile(fileURLToPath(new URL(path, root)));
    const ext = path.slice(path.lastIndexOf('.'));
    res.writeHead(200, {
      'Content-Type': `${types[ext]}; charset=utf-8`, 'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff',
      'Content-Security-Policy': "default-src 'self'; img-src 'self' blob: data:; style-src 'self'; script-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'",
    });
    res.end(req.method === 'HEAD' ? undefined : bytes);
  } catch { res.writeHead(500).end('Unable to load application'); }
}).listen(port, '127.0.0.1', () => console.log(`E-paper Studio: http://localhost:${port}`));

import { spawn } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// Local orchestration client for the user's existing Antigravity MCP server.
const server = fileURLToPath(new URL('./antigravity-observer-server.mjs', import.meta.url));
const child = spawn(process.execPath, [server], { stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
let buffer = '';
let nextId = 0;
const pending = new Map();
child.on('exit', code => {
  for (const entry of pending.values()) {
    clearTimeout(entry.timeout);
    entry.reject(new Error('Antigravity MCP exited: ' + code));
  }
  pending.clear();
});
child.stderr.on('data', data => process.stderr.write(data));
child.stdout.on('data', data => {
  buffer += data.toString();
  let end;
  while ((end = buffer.indexOf('\n')) !== -1) {
    const line = buffer.slice(0, end);
    buffer = buffer.slice(end + 1);
    if (!line.trim()) continue;
    try {
      const message = JSON.parse(line);
      const entry = pending.get(message.id);
      if (entry) {
        pending.delete(message.id);
        clearTimeout(entry.timeout);
        if (message.error) entry.reject(new Error(JSON.stringify(message.error)));
        else entry.resolve(message.result);
      }
    } catch (error) { process.stderr.write(error.message + '\n'); }
  }
});
function request(method, params) {
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error('MCP request timed out: ' + method)); }, 45000);
    pending.set(id, { resolve, reject, timeout });
    child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
  });
}
try {
  await request('initialize', { protocolVersion: '2025-11-25', capabilities: {}, clientInfo: { name: 'sdd-coordinator', version: '1.0.0' } });
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');
  const name = process.argv[2] || 'agy_list_chats';
  const rawArgs = process.argv[3];
  const args = rawArgs ? JSON.parse(rawArgs.startsWith('@') ? readFileSync(rawArgs.slice(1), 'utf8').replace(/^\uFEFF/, '') : rawArgs) : {};
  const result = name === 'tools/list' ? await request('tools/list', {}) : await request('tools/call', { name, arguments: args });
  process.stdout.write(JSON.stringify(result, null, 2) + '\n');
  if (result.isError) process.exitCode = 1;
} catch (error) {
  process.stderr.write(error.message + '\n');
  process.exitCode = 1;
} finally {
  for (const item of pending.values()) clearTimeout(item.timeout);
  child.kill();
}

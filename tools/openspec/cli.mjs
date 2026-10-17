import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const cli = fileURLToPath(new URL('./node_modules/@fission-ai/openspec/bin/openspec.js', import.meta.url));
if (!existsSync(cli)) {
  console.error('Установите CLI из корня проекта: npm ci --prefix tools/openspec --ignore-scripts');
  process.exit(1);
}

const result = spawnSync(process.execPath, [cli, ...process.argv.slice(2)], {
  cwd: fileURLToPath(new URL('../../', import.meta.url)),
  env: { ...process.env, OPENSPEC_TELEMETRY: '0', OPENSPEC_NO_UPDATE_CHECK: '1' },
  stdio: 'inherit',
});
if (result.error) console.error(result.error.message);
process.exit(result.status ?? 1);

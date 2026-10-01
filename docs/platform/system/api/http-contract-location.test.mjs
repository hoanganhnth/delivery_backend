import assert from 'node:assert/strict';
import test from 'node:test';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

test('canonical backend generator works without a sibling checkout or root override', () => {
  const env = { ...process.env };
  delete env.DELIVERY_WORKSPACE_ROOT;
  delete env.DELIVERY_BACKEND_ROOT;
  const result = spawnSync(process.execPath, [
    fileURLToPath(new URL('./generate-http-contract.mjs', import.meta.url)), '--check',
  ], { env, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr || result.stdout);
});

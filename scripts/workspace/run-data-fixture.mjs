#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const workspaceRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const defaultBaseUrl = process.env.BASE_URL || 'http://localhost:8079';
const runFixture = process.env.RUN_FIXTURE === 'true' || process.argv.includes('--run');
const fixturePathArg = process.argv.slice(2).find((arg) => !arg.startsWith('--'));

if (!fixturePathArg || process.argv.includes('--help')) {
  console.error('Usage: node scripts/run-data-fixture.mjs <scenario.json> [--run]');
  console.error('Default mode is dry-run. Use RUN_FIXTURE=true or --run to call APIs.');
  process.exit(2);
}

const fixturePath = resolve(process.cwd(), fixturePathArg);

const isRecord = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);

const readJson = async (filePath) => JSON.parse(await readFile(filePath, 'utf8'));

const parseTypedValue = (raw, type = 'string') => {
  if (raw === undefined || raw === null) return raw;
  if (type === 'number') {
    const value = Number(raw);
    if (!Number.isFinite(value)) throw new Error(`Không thể chuyển "${raw}" thành number`);
    return value;
  }
  if (type === 'boolean') return raw === true || raw === 'true';
  if (type === 'json') return typeof raw === 'string' ? JSON.parse(raw) : raw;
  return String(raw);
};

const getPath = (root, path) => path.split('.').filter(Boolean).reduce((value, key) => {
  if (value === null || value === undefined) return undefined;
  return value[key];
}, root);

const missingValue = (token) => {
  if (runFixture) throw new Error(`Thiếu giá trị cho ${token}`);
  return `<<missing:${token}>>`;
};

const resolveToken = (token, context) => {
  const trimmed = token.trim();
  if (trimmed === 'runId') return context.runId;

  const typedEnv = /^env\.([A-Za-z_][A-Za-z0-9_]*)(?::(number|boolean|json))?$/.exec(trimmed);
  if (typedEnv) {
    const [, name, type = 'string'] = typedEnv;
    const value = process.env[name];
    return value === undefined ? missingValue(`env.${name}`) : parseTypedValue(value, type);
  }

  const variable = /^var\.(.+)$/.exec(trimmed);
  if (variable) return getPath(context.variables, variable[1]) ?? missingValue(trimmed);

  const step = /^steps\.([^\.]+)\.(.+)$/.exec(trimmed);
  if (step) return getPath(context.steps[step[1]], step[2]) ?? missingValue(trimmed);

  return missingValue(trimmed);
};

const resolveValue = (value, context) => {
  if (Array.isArray(value)) return value.map((item) => resolveValue(item, context));
  if (isRecord(value)) {
    if (typeof value.fromEnv === 'string') {
      const raw = process.env[value.fromEnv] ?? value.default;
      if (raw === undefined) return missingValue(`env.${value.fromEnv}`);
      return parseTypedValue(raw, typeof value.type === 'string' ? value.type : 'string');
    }
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, resolveValue(item, context)]));
  }
  if (typeof value !== 'string') return value;

  const exact = /^\{\{\s*([^{}]+?)\s*\}\}$/.exec(value);
  if (exact) return resolveToken(exact[1], context);
  return value.replace(/\{\{\s*([^{}]+?)\s*\}\}/g, (_, token) => String(resolveToken(token, context)));
};

const redactedHeaders = (headers) => Object.fromEntries(Object.entries(headers).map(([key, value]) => [
  key.toLowerCase() === 'authorization' ? key : key,
  key.toLowerCase() === 'authorization' ? '<redacted>' : value,
]));

const runCatalog = async (fixture) => {
  const source = resolve(dirname(fixturePath), fixture.source);
  const script = resolve(workspaceRoot, 'backend_delivery/scripts/seed-realistic-catalog.sh');
  const ownerTokenEnv = fixture.ownerTokenEnv || 'OWNER_TOKEN';
  const environment = {
    ...process.env,
    CATALOG_FILE: source,
    DRY_RUN: runFixture ? 'false' : 'true',
  };
  if (process.env.BASE_URL && !environment.BASE) environment.BASE = process.env.BASE_URL;
  if (runFixture && !environment[ownerTokenEnv]) {
    throw new Error(`RUN_FIXTURE=true cần ${ownerTokenEnv}`);
  }
  if (environment[ownerTokenEnv]) environment.OWNER_TOKEN = environment[ownerTokenEnv];

  const result = spawnSync('bash', [script], {
    cwd: workspaceRoot,
    env: environment,
    encoding: 'utf8',
  });
  process.stdout.write(result.stdout || '');
  process.stderr.write(result.stderr || '');
  if (result.status !== 0) process.exit(result.status || 1);
};

const runHttp = async (fixture) => {
  const context = {
    runId: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    variables: {},
    steps: {},
  };
  context.variables = resolveValue(fixture.variables || {}, context);
  const baseUrl = resolveValue(fixture.baseUrl || defaultBaseUrl, context).replace(/\/$/, '');
  const requestResults = [];

  for (const request of fixture.requests || []) {
    if (!isRecord(request) || typeof request.id !== 'string' || typeof request.method !== 'string') {
      throw new Error('Mỗi request cần id và method');
    }
    const path = resolveValue(request.path || '/', context);
    const headers = {
      'Content-Type': 'application/json',
      ...resolveValue(request.headers || {}, context),
    };
    if (request.tokenEnv) {
      const token = process.env[request.tokenEnv];
      headers.Authorization = token ? `Bearer ${token}` : missingValue(`env.${request.tokenEnv}`);
    }
    const bodyPath = request.bodyFile ? resolve(dirname(fixturePath), request.bodyFile) : null;
    const rawBody = bodyPath ? await readJson(bodyPath) : request.body;
    const body = rawBody === undefined ? undefined : resolveValue(rawBody, context);
    const target = `${baseUrl}${path}`;

    if (!runFixture) {
      console.log(`\n[DRY-RUN] ${request.method.toUpperCase()} ${target}`);
      console.log(JSON.stringify({ headers: redactedHeaders(headers), body }, null, 2));
      context.steps[request.id] = { data: { id: `dry-run-${request.id}` } };
      requestResults.push({ id: request.id, status: 'DRY_RUN' });
      continue;
    }

    const response = await fetch(target, {
      method: request.method.toUpperCase(),
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    let parsed;
    try { parsed = text ? JSON.parse(text) : null; } catch { parsed = { raw: text }; }
    context.steps[request.id] = parsed;
    if (!response.ok) {
      throw new Error(`${request.id} thất bại HTTP ${response.status}: ${text}`);
    }
    const captures = {};
    for (const [name, pathExpression] of Object.entries(request.capture || {})) {
      const value = getPath(parsed, pathExpression);
      if (value === undefined) throw new Error(`Không capture được ${name} từ ${request.id}.${pathExpression}`);
      captures[name] = value;
    }
    requestResults.push({ id: request.id, status: response.status, captures });
    console.log(`✅ ${request.id}: HTTP ${response.status}`, captures);
  }

  const output = { fixture: fixture.name || fixturePathArg, runId: context.runId, dryRun: !runFixture, requests: requestResults };
  const outputDirectory = resolve(workspaceRoot, 'data/.runs');
  await mkdir(outputDirectory, { recursive: true });
  await writeFile(resolve(outputDirectory, `${context.runId}-${fixture.name || 'fixture'}.json`), `${JSON.stringify(output, null, 2)}\n`);
};

const fixture = await readJson(fixturePath);
if (!isRecord(fixture) || fixture.version !== 1) throw new Error('Fixture phải là object version=1');
if (fixture.kind === 'catalog') await runCatalog(fixture);
else if (fixture.kind === 'http') await runHttp(fixture);
else throw new Error('Fixture kind phải là catalog hoặc http');

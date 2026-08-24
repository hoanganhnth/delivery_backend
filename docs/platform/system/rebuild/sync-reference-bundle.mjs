#!/usr/bin/env node

/**
 * Creates a deterministic, read-only source mirror under the platform
 * documentation tree's system/reference directory.
 * The canonical docs remain editable above it; the mirror lets a new engineer
 * or model browse the current cross-repository documentation/configuration
 * corpus from one folder without guessing which repo owns a file.
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const workspace = path.resolve(here, '../../../../..');
const systemDirectory = path.resolve(here, '..');
const platformDirectory = path.resolve(systemDirectory, '..');
const referenceDirectory = path.join(systemDirectory, 'reference');
const mirrorDirectory = path.join(referenceDirectory, 'workspace');
const write = process.argv.includes('--write');
const check = process.argv.includes('--check');

if ((write && check) || (!write && !check)) {
  console.error('Usage: node docs/system/rebuild/sync-reference-bundle.mjs --write|--check');
  process.exit(2);
}

const excludedDirectoryNames = new Set([
  '.git',
  '.agents',
  '.dart_tool',
  '.venv',
  'node_modules',
  'Pods',
  'target',
  'build',
  '.gradle',
  '.secrets',
]);

function relativeToWorkspace(file) {
  return path.relative(workspace, file).split(path.sep).join('/');
}

function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

function shouldSkipDirectory(absoluteDirectory) {
  if (absoluteDirectory === referenceDirectory
      || absoluteDirectory.startsWith(`${referenceDirectory}${path.sep}`)) {
    return true;
  }
  if (path.basename(absoluteDirectory).startsWith('.reference-stage-')) {
    return true;
  }
  return excludedDirectoryNames.has(path.basename(absoluteDirectory));
}

function walk(absoluteDirectory, select, files) {
  if (shouldSkipDirectory(absoluteDirectory)) return;
  for (const entry of fs.readdirSync(absoluteDirectory, { withFileTypes: true })) {
    const target = path.join(absoluteDirectory, entry.name);
    if (entry.isDirectory()) {
      walk(target, select, files);
    } else if (entry.isFile() && select(target)) {
      files.add(target);
    }
  }
}

function extension(file) {
  return path.extname(file).toLowerCase();
}

const sources = new Set();

// Canonical cross-system docs now live inside the backend repository. The old
// workspace-level docs path is a compatibility surface and is intentionally
// excluded so two copies cannot become independent authorities.
walk(platformDirectory, (file) => ['.md', '.mjs', '.json'].includes(extension(file)), sources);

// Backend documentation plus reproducibility-relevant config/migration/deploy
// assets. Java source is deliberately not mirrored; code remains executable
// authority in its owning repository and is linked by the catalog/source map.
walk(path.join(workspace, 'backend_delivery'), (file) => {
  const relative = relativeToWorkspace(file);
  const name = path.basename(file);
  if (extension(file) === '.md') return true;
  if (name === 'pom.xml' || name === 'Dockerfile') return true;
  if (name.startsWith('docker-compose') && ['.yml', '.yaml'].includes(extension(file))) return true;
  if (relative.startsWith('backend_delivery/deploy/kubernetes/')) return ['.md', '.mjs', '.yaml', '.yml'].includes(extension(file));
  if (relative.startsWith('backend_delivery/scripts/')) return extension(file) === '.sh';
  if (relative.startsWith('backend_delivery/monitoring/')) return ['.yml', '.yaml', '.json'].includes(extension(file));
  if (relative.startsWith('backend_delivery/.github/workflows/')) return ['.yml', '.yaml'].includes(extension(file));
  return relative.includes('/src/main/resources/')
    && ['.properties', '.yml', '.yaml', '.sql'].includes(extension(file));
}, sources);

// First-party client documentation and dependency manifests; vendor/generated
// docs are filtered by the directory guard above.
for (const client of ['delivery_app', 'delivery_web', 'shipper_app2']) {
  walk(path.join(workspace, client), (file) => {
    const name = path.basename(file);
    return extension(file) === '.md' || name === 'pubspec.yaml' || name === 'package.json';
  }, sources);
}

const rootAgents = path.join(workspace, 'AGENTS.md');
if (fs.existsSync(rootAgents)) sources.add(rootAgents);

const sourceRows = [...sources]
  .map((file) => ({
    source: relativeToWorkspace(file),
    target: `workspace/${relativeToWorkspace(file)}`,
    sha256: sha256(file),
    bytes: fs.statSync(file).size,
  }))
  .sort((left, right) => left.source.localeCompare(right.source));

const manifest = {
  format: 1,
  purpose: 'Deterministic reference snapshot for the Delivery reconstruction documentation.',
  sourceCount: sourceRows.length,
  generatedFiles: [
    'README.md',
    'MANIFEST.json',
    'workspace/backend_delivery/docs/platform/system/reference/README.md',
  ],
  sources: sourceRows,
};
const manifestContent = `${JSON.stringify(manifest, null, 2)}\n`;
const readmeContent = [
  '# Reference Source Mirror\n\n',
  `This generated mirror preserves ${sourceRows.length} first-party documentation, `,
  'runtime configuration, migration, deployment, monitoring and client-manifest files ',
  'under workspace/ using their original workspace paths. Relative links among copied ',
  'documentation therefore continue to resolve wherever their targets are also mirrored.\n\n',
  'The editable/canonical reconstruction guides are one directory above this mirror. ',
  "The owning repositories' code, tests and runtime configuration remain authoritative. ",
  'Java/Dart/TypeScript source is not duplicated here; use the canonical source map to ',
  'follow code references back to its owner.\n\n',
  'Regenerate after documentation/configuration changes:\n\n',
  '```bash\n',
  'node docs/system/rebuild/sync-reference-bundle.mjs --write\n',
  'node docs/system/rebuild/sync-reference-bundle.mjs --check\n',
  '```\n\n',
  'MANIFEST.json records every source path, SHA-256 checksum and byte count. ',
  'No secret directories, vendor dependency docs, node modules, Pods, build output or .git content is included.\n',
].join('');
const mirroredEntryPointContent = '# Offline Reference Bundle Entry Point\n\n'
  + 'This path is a navigation shim for the mirrored `docs/system/` copy. '
  + 'Open the live bundle index at [reference/README.md](../../../../../../README.md).\n';

function expectedFiles() {
  return new Map([
    ...sourceRows.map((row) => [path.join('workspace', row.source), path.join(workspace, row.source)]),
    ['MANIFEST.json', null],
    ['README.md', null],
    ['workspace/backend_delivery/docs/platform/system/reference/README.md', null],
  ]);
}

function writeBundle() {
  const temporaryDirectory = path.join(systemDirectory, `.reference-stage-${process.pid}`);
  fs.rmSync(temporaryDirectory, { recursive: true, force: true });
  fs.mkdirSync(temporaryDirectory, { recursive: true });
  for (const row of sourceRows) {
    const destination = path.join(temporaryDirectory, 'workspace', row.source);
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.copyFileSync(path.join(workspace, row.source), destination);
  }
  fs.writeFileSync(path.join(temporaryDirectory, 'MANIFEST.json'), manifestContent);
  fs.writeFileSync(path.join(temporaryDirectory, 'README.md'), readmeContent);
  const mirroredEntryPoint = path.join(
    temporaryDirectory,
    'workspace/backend_delivery/docs/platform/system/reference/README.md',
  );
  fs.mkdirSync(path.dirname(mirroredEntryPoint), { recursive: true });
  fs.writeFileSync(mirroredEntryPoint, mirroredEntryPointContent);
  fs.rmSync(referenceDirectory, { recursive: true, force: true });
  fs.renameSync(temporaryDirectory, referenceDirectory);
  console.log(`Wrote reference bundle with ${sourceRows.length} source files.`);
}

function checkBundle() {
  const problems = [];
  if (!fs.existsSync(path.join(referenceDirectory, 'MANIFEST.json'))) {
    problems.push('missing docs/system/reference/MANIFEST.json');
  } else if (fs.readFileSync(path.join(referenceDirectory, 'MANIFEST.json'), 'utf8') !== manifestContent) {
    problems.push('reference manifest is stale; run sync-reference-bundle.mjs --write');
  }
  if (!fs.existsSync(path.join(referenceDirectory, 'README.md'))
      || fs.readFileSync(path.join(referenceDirectory, 'README.md'), 'utf8') !== readmeContent) {
    problems.push('reference README is stale or missing');
  }
  const mirroredEntryPoint = path.join(
    referenceDirectory,
    'workspace/backend_delivery/docs/platform/system/reference/README.md',
  );
  if (!fs.existsSync(mirroredEntryPoint)
      || fs.readFileSync(mirroredEntryPoint, 'utf8') !== mirroredEntryPointContent) {
    problems.push('mirrored docs/system reference entry point is stale or missing');
  }

  const expected = expectedFiles();
  for (const row of sourceRows) {
    const target = path.join(mirrorDirectory, row.source);
    if (!fs.existsSync(target)) {
      problems.push(`missing mirrored source ${row.source}`);
    } else if (sha256(target) !== row.sha256) {
      problems.push(`stale mirrored source ${row.source}`);
    }
  }
  if (fs.existsSync(referenceDirectory)) {
    const actual = new Set();
    walk(referenceDirectory, (file) => true, actual);
    for (const file of actual) {
      const local = path.relative(referenceDirectory, file);
      if (!expected.has(local)) problems.push(`unexpected reference file ${local}`);
    }
  }

  if (problems.length > 0) {
    console.error('Reference bundle verification failed:');
    for (const problem of problems) console.error(`- ${problem}`);
    process.exit(1);
  }
  console.log(`PASS: reference bundle has ${sourceRows.length} current source files.`);
}

if (write) writeBundle();
if (check) checkBundle();

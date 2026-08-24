#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const workspace = path.resolve(here, '../../../..');
const requiredDocuments = [
  'README.md',
  'diagram-standards.md',
  'architecture.md',
  'service-catalog.md',
  'workflows.md',
  'api/README.md',
  'api/http-contract-catalog.md',
  'events-and-data.md',
  'security.md',
  'clients.md',
  'technology-and-tooling.md',
  'operations/README.md',
  'operations/deployment-foundation.md',
  'operations/production-platform-decision-packet.md',
  'operations/release-and-recovery.md',
  'operations/observability.md',
  'rebuild/README.md',
  'rebuild/source-map.md',
  'simulator/README.md',
];
const expectedServiceNames = [
  'api-gateway',
  'auth-service',
  'user-service',
  'restaurant-service',
  'order-service',
  'delivery-service',
  'search-service',
  'shipper-service',
  'settlement-service',
  'notification-service',
  'match-service',
  'tracking-service',
  'livestream-service',
  'saga-orchestrator-service',
  'promotion-service',
  'analytics-service',
  'flashsale-service',
];

const problems = [];
const markdownFiles = [];
const supportedMermaidHeaders = new Set([
  'flowchart',
  'sequenceDiagram',
  'stateDiagram-v2',
  'erDiagram',
  'classDiagram',
]);
const forbiddenBoundaryPatterns = [
  /\bClient\s*-->.*\bService\b/,
  /\bClient\s*-->.*\bJWKS\b/,
  /\bC\s*->>\s*O\b/,
  /\bSh\s*->>\s*D\b/,
  /\bV\s*->>\s*T\b/,
];

const referenceCheck = spawnSync(
  process.execPath,
  [path.join(here, 'rebuild/sync-reference-bundle.mjs'), '--check'],
  { encoding: 'utf8' },
);
if (referenceCheck.status !== 0) {
  problems.push('offline reference bundle is stale or missing; run sync-reference-bundle.mjs --write');
}

const httpContractCheck = spawnSync(
  process.execPath,
  [path.join(here, 'api/generate-http-contract.mjs'), '--check'],
  { encoding: 'utf8' },
);
if (httpContractCheck.status !== 0) {
  problems.push('source-derived HTTP contract is stale or missing; run api/generate-http-contract.mjs --write');
}

function walk(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (path.relative(here, target) === 'reference') continue;
      walk(target);
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      markdownFiles.push(target);
    }
  }
}

function lintMermaid(document, content) {
  const relativeDocument = path.relative(workspace, document);
  const lines = content.split(/\r?\n/);
  const blocks = [];
  let openBlock = null;

  for (const [index, line] of lines.entries()) {
    if (!openBlock) {
      const opening = line.match(/^(?<marker>`{3,}|~{3,})mermaid\s*$/);
      if (opening) {
        openBlock = {
          line: index + 1,
          marker: opening.groups.marker,
          body: [],
        };
      }
      continue;
    }

    const close = new RegExp(`^${openBlock.marker[0]}{${openBlock.marker.length},}\\s*$`);
    if (close.test(line)) {
      blocks.push(openBlock);
      openBlock = null;
    } else {
      openBlock.body.push(line);
    }
  }

  if (openBlock) {
    problems.push(`${relativeDocument}:${openBlock.line} has an unclosed Mermaid fence`);
    return blocks.length;
  }

  for (const block of blocks) {
    if (block.marker !== '```') {
      problems.push(`${relativeDocument}:${block.line} must use a triple-backtick Mermaid fence`);
    }

    const firstCodeLine = block.body.find((line) => line.trim().length)?.trim() ?? '';
    const header = firstCodeLine.match(/^(\S+)/)?.[1];
    if (!header) {
      problems.push(`${relativeDocument}:${block.line} Mermaid block has no diagram header`);
    } else if (header === 'graph') {
      problems.push(`${relativeDocument}:${block.line} uses legacy Mermaid 'graph'; use 'flowchart'`);
    } else if (!supportedMermaidHeaders.has(header)) {
      problems.push(
        `${relativeDocument}:${block.line} uses unsupported Mermaid header '${header}'`,
      );
    }

    if (block.body.some((line) => line.includes('\\n'))) {
      problems.push(
        `${relativeDocument}:${block.line} uses \\n in a Mermaid label; use <br/> for portable line breaks`,
      );
    }

    for (const pattern of forbiddenBoundaryPatterns) {
      if (block.body.some((line) => pattern.test(line))) {
        problems.push(
          `${relativeDocument}:${block.line} violates the public Gateway boundary: ${pattern}`,
        );
      }
    }
  }

  return blocks.length;
}

for (const document of requiredDocuments) {
  if (!fs.existsSync(path.join(here, document))) {
    problems.push(`missing required document: docs/system/${document}`);
  }
}

walk(here);

let mermaidBlocks = 0;
for (const document of markdownFiles) {
  const content = fs.readFileSync(document, 'utf8');
  const relativeDocument = path.relative(workspace, document);

  let openFence = null;
  for (const [index, line] of content.split(/\r?\n/).entries()) {
    const fence = line.match(/^(?<marker>`{3,}|~{3,})(?<language>[^\s]*)/);
    if (!fence) continue;

    if (!openFence) {
      openFence = { marker: fence.groups.marker, line: index + 1 };
    } else if (fence.groups.marker[0] === openFence.marker[0]
        && fence.groups.marker.length >= openFence.marker.length) {
      openFence = null;
    }
  }
  if (openFence) {
    problems.push(`${relativeDocument}:${openFence.line} has an unclosed Markdown fence`);
  }

  mermaidBlocks += lintMermaid(document, content);

  const markdownLink = /\[[^\]]*\]\(([^)]+)\)/g;
  for (const match of content.matchAll(markdownLink)) {
    let target = match[1].trim();
    if (target.startsWith('<') && target.endsWith('>')) target = target.slice(1, -1);
    if (/^(https?:|mailto:|#)/.test(target)) continue;

    const localTarget = target.split('#', 1)[0].trim().replace(/\s+".*$/, '');
    if (!localTarget) continue;
    const resolved = path.resolve(path.dirname(document), localTarget);
    if (!fs.existsSync(resolved)) {
      problems.push(`${relativeDocument} links to missing local target ${target}`);
    }
  }
}

// The root architecture is the editable Mermaid source referenced by this
// folder, so apply the same syntax guard even though it lives outside docs/system.
const editableDiagramDocuments = [path.join(here, '../ARCHITECTURE.md')];
for (const document of editableDiagramDocuments) {
  if (fs.existsSync(document)) {
    mermaidBlocks += lintMermaid(document, fs.readFileSync(document, 'utf8'));
  }
}

if (mermaidBlocks === 0) {
  problems.push('expected at least one Mermaid source block in docs/system');
}

const catalogPath = path.join(here, 'service-catalog.md');
if (fs.existsSync(catalogPath)) {
  const catalog = fs.readFileSync(catalogPath, 'utf8');
  for (const service of expectedServiceNames) {
    if (!catalog.includes(service)) {
      problems.push(`service-catalog.md does not mention expected service ${service}`);
    }
  }
}

if (problems.length > 0) {
  console.error('System documentation verification failed:');
  for (const problem of problems) console.error(`- ${problem}`);
  process.exitCode = 1;
} else {
  console.log(
    `PASS: ${markdownFiles.length} Markdown documents, ${mermaidBlocks} Mermaid blocks `
      + '(system corpus and editable root source), '
      + `${expectedServiceNames.length} service catalog entries, and all local links resolved.`,
  );
}

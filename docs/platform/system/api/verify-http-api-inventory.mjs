#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import {
  compareInventoryToSource,
  extractMappedHandlersFromJava,
  parseHttpInventory,
} from './http-api-inventory-lib.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const backend = path.resolve(here, '../../../..');
const inventoryPath = path.join(backend, 'docs/http-api-inventory.md');

function walk(directory, files = []) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(target, files);
    else if (entry.isFile() && entry.name.endsWith('Controller.java')) files.push(target);
  }
  return files;
}

function mappedHandlers() {
  const handlers = [];
  for (const entry of fs.readdirSync(backend, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const sourceRoot = path.join(backend, entry.name, 'src/main/java');
    if (!fs.existsSync(sourceRoot)) continue;
    for (const file of walk(sourceRoot)) {
      handlers.push(...extractMappedHandlersFromJava(fs.readFileSync(file, 'utf8'), {
        service: entry.name,
        controller: path.basename(file, '.java'),
        file: path.relative(backend, file).split(path.sep).join('/'),
      }));
    }
  }
  const seen = new Set();
  for (const handler of handlers) {
    const key = `${handler.service}|${handler.controller}|${handler.handler}`;
    if (seen.has(key)) {
      throw new Error(`source has duplicate mapped handler key '${key}'; inventory cannot represent overloads safely`);
    }
    seen.add(key);
  }
  return handlers;
}

try {
  const inventory = parseHttpInventory(fs.readFileSync(inventoryPath, 'utf8'));
  const source = mappedHandlers();
  const comparison = compareInventoryToSource(inventory, source);
  if (comparison.missingFromInventory.length || comparison.missingFromSource.length) {
    console.error('HTTP API inventory does not match mapped controller handlers.');
    for (const key of comparison.missingFromInventory) console.error(`- missing from inventory: ${key}`);
    for (const key of comparison.missingFromSource) console.error(`- missing from source: ${key}`);
    process.exitCode = 1;
  } else {
    const services = new Set(source.map(({ service }) => service));
    console.log(`HTTP API inventory is aligned with ${source.length} mapped handlers across ${services.size} services.`);
  }
} catch (error) {
  console.error(`HTTP API inventory verification failed: ${error.message}`);
  process.exitCode = 1;
}

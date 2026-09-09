#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { enrichCatalog } from './hanoi-contact-data.mjs';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const backendRoot = resolve(scriptDirectory, '../..');
const workspaceRoot = resolve(backendRoot, '..');
const defaultCatalogPath = resolve(workspaceRoot, 'data/catalog/hanoi-catalog.json');
const defaultManifestPath = resolve(backendRoot, 'scripts/fixtures/hanoi-restaurant-contact-manifest.json');

const args = process.argv.slice(2);
const hasFlag = (name) => args.includes(name);
const optionValue = (name, fallback) => {
  const index = args.indexOf(name);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
};

if (hasFlag('--help')) {
  console.log([
    'Usage: node scripts/workspace/enrich-hanoi-contact-data.mjs [options]',
    '',
    'Mặc định chỉ kiểm tra và in summary.',
    'Dùng --write để cập nhật catalog shared; dùng --write-manifest để lưu artifact vào Git.',
    'Options: --write --write-manifest --catalog PATH --manifest PATH',
  ].join('\n'));
  process.exit(0);
}

const catalogPath = resolve(process.cwd(), optionValue('--catalog', process.env.CATALOG_FILE || defaultCatalogPath));
const manifestPath = resolve(process.cwd(), optionValue('--manifest', process.env.CONTACT_MANIFEST_FILE || defaultManifestPath));
const writeCatalog = hasFlag('--write');
const writeManifest = hasFlag('--write-manifest');
const catalog = JSON.parse(await readFile(catalogPath, 'utf8'));
const enrichedCatalog = enrichCatalog(catalog);
const restaurants = enrichedCatalog.restaurants || [];

const contactManifest = {
  schemaVersion: 1,
  dataset: `${enrichedCatalog.dataset || 'hanoi-catalog'}-contacts`,
  city: enrichedCatalog.city || 'Hà Nội',
  generatedAt: enrichedCatalog.generatedAt || null,
  source: {
    publicOnly: true,
    sourceCatalog: relative(backendRoot, catalogPath) || catalogPath,
    addressPolicy: 'source-backed addresses preserved; missing details use deterministic mock data',
    phonePolicy: 'deterministic Vietnamese 10-digit mock numbers',
    coordinatePolicy: 'district-centroid jitter for generated mock addresses',
  },
  coverage: {
    restaurants: restaurants.length,
    sourceBackedAddresses: restaurants.filter((restaurant) => restaurant.provenance?.address === 'source_backed').length,
    generatedAddresses: restaurants.filter((restaurant) => restaurant.provenance?.address === 'synthetic_mock').length,
    uniquePhones: new Set(restaurants.map((restaurant) => restaurant.phone)).size,
  },
  restaurants: restaurants.map((restaurant) => ({
    restaurantKey: restaurant.restaurantKey,
    name: restaurant.name,
    address: restaurant.address,
    district: restaurant.district,
    addressLat: restaurant.addressLat,
    addressLng: restaurant.addressLng,
    phone: restaurant.phone,
    addressProvenance: restaurant.provenance?.address || 'unknown',
    phoneProvenance: restaurant.provenance?.phone || 'unknown',
    coordinateProvenance: restaurant.provenance?.coordinates || 'unknown',
  })),
};

if (writeCatalog) {
  await writeFile(catalogPath, `${JSON.stringify(enrichedCatalog, null, 2)}\n`);
}
if (writeManifest) {
  await mkdir(dirname(manifestPath), { recursive: true });
  await writeFile(manifestPath, `${JSON.stringify(contactManifest, null, 2)}\n`);
}

console.log(JSON.stringify({
  catalog: catalogPath,
  manifest: writeManifest ? manifestPath : null,
  ...contactManifest.coverage,
  status: writeCatalog || writeManifest ? 'WRITTEN' : 'DRY_RUN',
}, null, 2));

#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const backendRoot = resolve(scriptDirectory, '../..');
const workspaceRoot = resolve(backendRoot, '..');
const defaultCatalogPath = resolve(workspaceRoot, 'data/catalog/hanoi-catalog.json');
const defaultOutputPath = resolve(backendRoot, 'scripts/fixtures/hanoi-grab-image-manifest.json');

const args = process.argv.slice(2);
const hasFlag = (name) => args.includes(name);
const optionValue = (name, fallback) => {
  const index = args.indexOf(name);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
};

if (hasFlag('--help')) {
  console.log([
    'Usage: node scripts/workspace/export-grab-image-manifest.mjs [options]',
    '',
    'Mặc định chỉ kiểm tra và in summary. Dùng --write để ghi manifest vào Git.',
    'Options: --write --catalog PATH --output PATH',
  ].join('\n'));
  process.exit(0);
}

const catalogPath = resolve(process.cwd(), optionValue('--catalog', process.env.CATALOG_FILE || defaultCatalogPath));
const outputPath = resolve(process.cwd(), optionValue('--output', process.env.IMAGE_MANIFEST_FILE || defaultOutputPath));
const writeManifest = hasFlag('--write');
const grabImageHost = 'huawei-food-cms.grab.com';

const catalog = JSON.parse(await readFile(catalogPath, 'utf8'));
const restaurants = Array.isArray(catalog.restaurants) ? catalog.restaurants : [];
const menuItems = Array.isArray(catalog.menuItems) ? catalog.menuItems : [];

const isGrabImage = (value) => {
  if (typeof value !== 'string' || !value.startsWith('https://')) return false;
  try {
    const url = new URL(value);
    return url.hostname === grabImageHost
      && /\.(?:webp|jpe?g|png)(?:$|[?#])/i.test(url.pathname);
  } catch {
    return false;
  }
};

if (catalog.schemaVersion !== 1) throw new Error('Catalog phải có schemaVersion=1');
if (!restaurants.length || !menuItems.length) throw new Error('Catalog phải có restaurants và menuItems');
if (new Set(restaurants.map((restaurant) => restaurant.restaurantKey)).size !== restaurants.length) {
  throw new Error('Catalog có restaurantKey trùng');
}
if (restaurants.some((restaurant) => !restaurant.restaurantKey || !restaurant.name || !restaurant.address || !isGrabImage(restaurant.image))) {
  throw new Error('Catalog có restaurant thiếu key/name/address hoặc ảnh Grab CDN');
}
const restaurantKeys = new Set(restaurants.map((restaurant) => restaurant.restaurantKey));
if (menuItems.some((item) => !restaurantKeys.has(item.restaurantKey) || !item.name || !isGrabImage(item.image))) {
  throw new Error('Catalog có menu orphan hoặc thiếu tên/ảnh Grab CDN');
}

const observedAt = catalog.imageEnrichment?.observedAt
  || catalog.collection?.imageEnrichedAt
  || catalog.generatedAt
  || null;
const detailEndpoint = catalog.imageEnrichment?.endpoint
  || 'https://portal.grab.com/foodweb/merchants';
const searchEndpoint = catalog.imageEnrichment?.searchEndpoint
  || 'https://portal.grab.com/foodweb/search';
const relativeCatalogPath = relative(backendRoot, catalogPath) || catalogPath;

const manifest = {
  schemaVersion: 1,
  dataset: `${catalog.dataset || 'hanoi-catalog'}-grab-images`,
  city: catalog.city || null,
  generatedAt: new Date().toISOString().slice(0, 10),
  source: {
    platform: 'GrabFood',
    cdnHost: grabImageHost,
    publicOnly: true,
    antiBotBypass: false,
    observedAt,
    detailEndpoint,
    searchEndpoint,
    sourceCatalog: relativeCatalogPath,
  },
  coverage: {
    restaurants: restaurants.length,
    menuItems: menuItems.length,
    uniqueRestaurantImages: new Set(restaurants.map((restaurant) => restaurant.image)).size,
    uniqueMenuImages: new Set(menuItems.map((item) => item.image)).size,
  },
  restaurants: restaurants.map((restaurant) => ({
    restaurantKey: restaurant.restaurantKey,
    name: restaurant.name,
    address: restaurant.address,
    image: restaurant.image,
    imageProvenance: restaurant.provenance?.image || restaurant.sourceFacts?.imageProvenance || 'source_backed',
    imageSourcePlatform: restaurant.source?.imageSourcePlatform || restaurant.sourceFacts?.imageSourcePlatform || 'GrabFood',
    imageSourceRecordId: restaurant.source?.imageSourceRecordId || restaurant.sourceFacts?.imageSourceRecordId || null,
    imageSourceUrl: restaurant.source?.imageSourceUrl || restaurant.sourceFacts?.imageSourceUrl || null,
    imageMatch: restaurant.source?.imageMatch || restaurant.sourceFacts?.imageMatch || null,
  })),
  menuItems: menuItems.map((item) => ({
    restaurantKey: item.restaurantKey,
    name: item.name,
    image: item.image,
    imageProvenance: item.imageProvenance || 'source_backed',
    imageSourcePlatform: item.imageSourcePlatform || 'GrabFood',
    imageSourceUrl: item.imageSourceUrl || null,
    imageSourceItemId: item.imageSourceItemId || null,
    imageSourceItemName: item.imageSourceItemName || null,
    imageMatch: item.imageMatch || null,
  })),
};

const summary = {
  catalog: catalogPath,
  output: outputPath,
  restaurants: manifest.coverage.restaurants,
  menuItems: manifest.coverage.menuItems,
  uniqueRestaurantImages: manifest.coverage.uniqueRestaurantImages,
  uniqueMenuImages: manifest.coverage.uniqueMenuImages,
  observedAt,
  status: writeManifest ? 'WRITTEN' : 'DRY_RUN',
};

if (writeManifest) {
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, `${JSON.stringify(manifest, null, 2)}\n`);
}

console.log(JSON.stringify(summary, null, 2));

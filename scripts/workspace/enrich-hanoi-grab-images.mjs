#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { access, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const backendRoot = resolve(scriptDirectory, '../..');
const workspaceRoot = resolve(backendRoot, '..');
const defaultCatalogPath = resolve(workspaceRoot, 'data/catalog/hanoi-catalog.json');
const defaultReportPath = resolve(workspaceRoot, 'data/catalog/hanoi-collection-report.json');
const defaultCacheDirectory = resolve(workspaceRoot, 'data/sources/hanoi-grab/.cache/image-details');

const args = process.argv.slice(2);
const hasFlag = (name) => args.includes(name);
const optionValue = (name, fallback) => {
  const index = args.indexOf(name);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
};

if (hasFlag('--help')) {
  console.log([
    'Usage: node scripts/workspace/enrich-hanoi-grab-images.mjs [options]',
    '',
    'Mặc định chỉ dry-run. Dùng --write để ghi catalog; --write chỉ cho phép chạy full catalog.',
    'Options: --write --refresh --limit N --start N --rate-ms N --timeout-ms N --retry-count N',
  ].join('\n'));
  process.exit(0);
}

const catalogPath = resolve(process.cwd(), optionValue('--catalog', process.env.CATALOG_FILE || defaultCatalogPath));
const reportPath = resolve(process.cwd(), optionValue('--report', process.env.REPORT_FILE || defaultReportPath));
const cacheDirectory = resolve(process.cwd(), optionValue('--cache-dir', process.env.GRAB_IMAGE_CACHE_DIR || defaultCacheDirectory));
const writeCatalog = hasFlag('--write');
const refreshCache = hasFlag('--refresh');
const startIndex = Number(optionValue('--start', '0'));
const limit = Number(optionValue('--limit', '0'));
const rateMs = Math.max(0, Number(optionValue('--rate-ms', process.env.GRAB_IMAGE_RATE_MS || '400')));
const timeoutMs = Math.max(1000, Number(optionValue('--timeout-ms', process.env.GRAB_IMAGE_TIMEOUT_MS || '20000')));
const retryCount = Math.max(0, Number(optionValue('--retry-count', process.env.GRAB_IMAGE_RETRY_COUNT || '2')));
const latlng = optionValue('--latlng', process.env.GRAB_IMAGE_LATLNG || '21.0278,105.8342');
const observedAt = process.env.OBSERVED_AT || new Date().toISOString().slice(0, 10);
const detailBaseUrl = (process.env.GRAB_DETAIL_BASE_URL || 'https://portal.grab.com/foodweb/merchants').replace(/\/$/, '');
const searchUrl = process.env.GRAB_SEARCH_URL || 'https://portal.grab.com/foodweb/search';
const grabImageHost = 'huawei-food-cms.grab.com';

if (!Number.isInteger(startIndex) || startIndex < 0) throw new Error('--start phải là số nguyên không âm');
if (!Number.isInteger(limit) || limit < 0) throw new Error('--limit phải là số nguyên không âm');
if (!Number.isFinite(rateMs) || !Number.isFinite(timeoutMs) || !Number.isFinite(retryCount)) throw new Error('Tham số HTTP không hợp lệ');
if (writeCatalog && (startIndex !== 0 || limit !== 0)) throw new Error('--write chỉ được dùng cho full catalog, bỏ --start/--limit');

const readJson = async (filePath) => JSON.parse(await readFile(filePath, 'utf8'));
const fileExists = async (filePath) => access(filePath).then(() => true).catch(() => false);
const sleep = (milliseconds) => new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));
const isRecord = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
const unique = (values) => [...new Set(values.filter(Boolean))];
const sha256 = (value) => createHash('sha256').update(String(value)).digest('hex');
const stableIndex = (key, length) => {
  if (!length) return -1;
  return createHash('sha256').update(String(key)).digest().readUInt32BE(0) % length;
};

const isGrabImageUrl = (value) => {
  if (typeof value !== 'string' || !value.startsWith('https://')) return false;
  try {
    const parsed = new URL(value);
    return parsed.hostname === grabImageHost
      && /^\/(?:compressed_webp|Merchants)\//.test(parsed.pathname)
      && /\.(?:webp|jpe?g|png)(?:$|[?#])/i.test(parsed.pathname);
  } catch {
    return false;
  }
};

const pickGrabImage = (...values) => values.find(isGrabImageUrl) || null;

const normalizeText = (value) => String(value || '')
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .replace(/đ/g, 'd')
  .replace(/Đ/g, 'D')
  .toLowerCase()
  .replace(/[^a-z0-9\s]+/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();

const matchingStopWords = new Set(['delivery', 'grabfood', 'food', 'restaurant', 'quan']);
const textTokens = (value) => new Set(
  normalizeText(value)
    .split(' ')
    .filter((token) => token.length > 1 && !matchingStopWords.has(token)),
);

const similarity = (left, right) => {
  const leftText = normalizeText(left);
  const rightText = normalizeText(right);
  const leftTokens = textTokens(left);
  const rightTokens = textTokens(right);
  if (!leftTokens.size || !rightTokens.size) return 0;
  let overlap = 0;
  for (const token of leftTokens) if (rightTokens.has(token)) overlap += 1;
  const denominator = Math.max(1, Math.min(leftTokens.size, rightTokens.size));
  const containmentBonus = leftText.includes(rightText) || rightText.includes(leftText) ? 0.35 : 0;
  return overlap / denominator + containmentBonus;
};

const cacheFileFor = (prefix, key) => resolve(cacheDirectory, `${prefix}-${sha256(key).slice(0, 24)}.json`);

class HttpError extends Error {
  constructor(status, message, retryAfterMs = 0) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.retryAfterMs = retryAfterMs;
  }
}

let nextRequestAt = 0;
const waitForRateLimit = async () => {
  const waitMs = Math.max(0, nextRequestAt - Date.now());
  if (waitMs) await sleep(waitMs);
  nextRequestAt = Date.now() + rateMs;
};

const parseRetryAfter = (value) => {
  if (!value) return 0;
  const seconds = Number(value);
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1000);
  const date = Date.parse(value);
  return Number.isFinite(date) ? Math.max(0, date - Date.now()) : 0;
};

const fetchJson = async (url, { method = 'GET', body, cacheFile, label }) => {
  if (!refreshCache && await fileExists(cacheFile)) {
    const cached = await readJson(cacheFile);
    if (cached?.payload !== undefined) return { ...cached, cacheHit: true };
  }

  let lastError;
  for (let attempt = 0; attempt <= retryCount; attempt += 1) {
    try {
      await waitForRateLimit();
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), timeoutMs);
      let response;
      try {
        response = await fetch(url, {
          method,
          headers: {
            Accept: 'application/json',
            ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
            Origin: 'https://food.grab.com',
            'User-Agent': 'delivery-local-catalog-fixture/1.0 (+local-development; public-pages-only)',
          },
          body: body === undefined ? undefined : JSON.stringify(body),
          redirect: 'follow',
          signal: controller.signal,
        });
      } finally {
        clearTimeout(timeout);
      }
      const responseText = await response.text();
      if (!response.ok) {
        throw new HttpError(
          response.status,
          `${label} HTTP ${response.status}`,
          parseRetryAfter(response.headers.get('retry-after')),
        );
      }
      let payload;
      try {
        payload = responseText ? JSON.parse(responseText) : null;
      } catch {
        throw new Error(`${label} trả về JSON không hợp lệ`);
      }
      const cached = { url, fetchedAt: new Date().toISOString(), status: response.status, payload };
      await mkdir(cacheDirectory, { recursive: true });
      await writeFile(cacheFile, `${JSON.stringify(cached)}\n`);
      return { ...cached, cacheHit: false };
    } catch (error) {
      lastError = error;
      if (!(error instanceof HttpError) || error.status !== 429 || attempt >= retryCount) throw error;
      const backoff = Math.max(error.retryAfterMs, rateMs * (attempt + 2), 1000);
      console.error(`⚠️ ${label} bị rate-limit, chờ ${backoff}ms rồi thử lại`);
      await sleep(backoff);
    }
  }
  throw lastError || new Error(`${label} thất bại`);
};

const detailUrlFor = (sourceId) => `${detailBaseUrl}/${encodeURIComponent(sourceId)}?userID=9999999999999&latlng=${encodeURIComponent(latlng)}`;

const parseMenuItems = (merchant) => {
  const items = [];
  for (const category of merchant?.menu?.categories || []) {
    for (const item of category?.items || []) {
      const image = pickGrabImage(
        item.imgHref,
        ...(Array.isArray(item.images) ? item.images : []),
        item.thumbImgHref,
        ...(Array.isArray(item.thumbImages) ? item.thumbImages : []),
        item.imgHrefFallback,
      );
      if (!item?.ID || !item?.name || !image) continue;
      items.push({ id: String(item.ID), name: String(item.name), image });
    }
  }
  const byId = new Map();
  for (const item of items) if (!byId.has(item.id)) byId.set(item.id, item);
  return [...byId.values()].sort((left, right) => left.id.localeCompare(right.id));
};

const parseDetail = (payload, expectedId) => {
  const merchant = payload?.merchant;
  if (!isRecord(merchant) || !merchant.ID) throw new Error('response không có merchant');
  if (String(merchant.ID).toUpperCase() !== String(expectedId).toUpperCase()) {
    throw new Error(`merchant ID trả về không khớp: cần ${expectedId}, nhận ${merchant.ID}`);
  }
  const menuItems = parseMenuItems(merchant);
  return {
    id: String(merchant.ID),
    name: String(merchant.name || ''),
    restaurantImage: pickGrabImage(
      merchant.photoHref,
      merchant.smallPhotoHref,
      merchant.photoHrefFallback,
      menuItems[0]?.image,
    ),
    menuItems,
  };
};

const getDetail = async (sourceId) => {
  const url = detailUrlFor(sourceId);
  const response = await fetchJson(url, {
    cacheFile: cacheFileFor('merchant', sourceId),
    label: `Grab merchant ${sourceId}`,
  });
  return { ...parseDetail(response.payload, sourceId), url, cacheHit: response.cacheHit };
};

const searchCandidates = (payload) => {
  const candidates = [];
  for (const merchant of payload?.searchResult?.searchMerchants || []) {
    if (merchant?.id && merchant?.merchantBrief) candidates.push({
      id: String(merchant.id),
      name: String(merchant.address?.name || ''),
      image: pickGrabImage(
        merchant.merchantBrief.photoHref,
        merchant.merchantBrief.smallPhotoHref,
        merchant.merchantBrief.photoHrefFallback,
        merchant.merchantBrief.smallPhotoHrefFallback,
      ),
    });
    for (const branch of merchant?.branchMerchants || []) {
      if (branch?.id && branch?.merchantBrief) candidates.push({
        id: String(branch.id),
        name: String(branch.address?.name || ''),
        image: pickGrabImage(
          branch.merchantBrief.photoHref,
          branch.merchantBrief.smallPhotoHref,
          branch.merchantBrief.photoHrefFallback,
          branch.merchantBrief.smallPhotoHrefFallback,
        ),
      });
    }
  }
  const byId = new Map();
  for (const candidate of candidates) if (!byId.has(candidate.id)) byId.set(candidate.id, candidate);
  return [...byId.values()];
};

const searchGrabMerchant = async (restaurant) => {
  const keyword = restaurant.name;
  const body = { latlng, keyword, offset: 0, pageSize: 32 };
  const response = await fetchJson(searchUrl, {
    method: 'POST',
    body,
    cacheFile: cacheFileFor('search', `${latlng}:${keyword}`),
    label: `Grab search ${keyword}`,
  });
  const candidates = searchCandidates(response.payload)
    .map((candidate) => ({ ...candidate, score: similarity(keyword, candidate.name) }))
    .sort((left, right) => right.score - left.score || left.id.localeCompare(right.id));
  const best = candidates[0];
  if (!best) return null;
  return { ...best, searchUrl, cacheHit: response.cacheHit };
};

const addSourceField = (restaurant, field) => {
  restaurant.source = restaurant.source || {};
  restaurant.source.sourceFields = unique([...(restaurant.source.sourceFields || []), field]);
};

const markRestaurantImage = (restaurant, image, details) => {
  restaurant.image = image;
  restaurant.provenance = { ...(restaurant.provenance || {}), image: details.fallback ? 'source_backed_fallback' : 'source_backed' };
  restaurant.source = {
    ...(restaurant.source || {}),
    imageSourcePlatform: 'GrabFood',
    imageSourceRecordId: details.sourceId,
    imageSourceUrl: details.url,
    imageMatch: details.match || 'merchant-id',
  };
  restaurant.sourceFacts = {
    ...(restaurant.sourceFacts || {}),
    imageProvenance: details.fallback ? 'source_backed_fallback' : 'source_backed',
    imageSourcePlatform: 'GrabFood',
    imageSourceRecordId: details.sourceId,
    imageMatch: details.match || 'merchant-id',
  };
  addSourceField(restaurant, 'restaurantPhoto');
};

const markMenuImage = (item, image, details) => {
  item.image = image;
  item.imageProvenance = details.fallback ? 'source_backed_fallback' : 'source_backed';
  item.imageSourcePlatform = 'GrabFood';
  item.imageSourceUrl = details.url;
  if (details.itemId) item.imageSourceItemId = details.itemId;
  if (details.itemName) item.imageSourceItemName = details.itemName;
  if (details.match) item.imageMatch = details.match;
};

const rankMenuImages = (target, actualItems, restaurant) => actualItems
  .map((actual) => ({
    actual,
    score: similarity(`${target.name} ${restaurant.name} ${restaurant.cuisine || ''}`, actual.name),
  }))
  .sort((left, right) => right.score - left.score || left.actual.id.localeCompare(right.actual.id));

const menuItemsByRestaurant = (menuItems) => {
  const result = new Map();
  for (const item of menuItems) {
    const items = result.get(item.restaurantKey) || [];
    items.push(item);
    result.set(item.restaurantKey, items);
  }
  return result;
};

const updateFromDetail = (restaurant, detail, menusByRestaurant, details) => {
  let directRestaurantImages = 0;
  let directMenuImages = 0;
  if (detail.restaurantImage) {
    markRestaurantImage(restaurant, detail.restaurantImage, details);
    directRestaurantImages = 1;
  }
  const targetItems = menusByRestaurant.get(restaurant.restaurantKey) || [];
  const usedActualIds = new Set();
  for (const [index, item] of targetItems.entries()) {
    const ranked = rankMenuImages(item, detail.menuItems, restaurant);
    const unused = ranked.find((candidate) => !usedActualIds.has(candidate.actual.id));
    const selected = unused || ranked[index % Math.max(1, ranked.length)];
    if (!selected) continue;
    usedActualIds.add(selected.actual.id);
    markMenuImage(item, selected.actual.image, {
      ...details,
      itemId: selected.actual.id,
      itemName: selected.actual.name,
      match: `menu-name-score:${selected.score.toFixed(2)}`,
    });
    directMenuImages += 1;
  }
  return { directRestaurantImages, directMenuImages };
};

const updateFromSearch = (restaurant, searchMatch, menusByRestaurant, detail) => updateFromDetail(
  restaurant,
  detail,
  menusByRestaurant,
  {
    sourceId: searchMatch.id,
    url: detail.url,
    match: `search-score:${searchMatch.score.toFixed(2)}`,
  },
);

const grabImagesFrom = (values) => unique(values.filter(isGrabImageUrl));

const updateFallbackImages = (catalog, menusByRestaurant, restaurantPool, menuPool) => {
  let fallbackRestaurantImages = 0;
  let fallbackMenuImages = 0;
  let unresolvedRestaurants = 0;
  let unresolvedMenus = 0;
  for (const restaurant of catalog.restaurants) {
    if (isGrabImageUrl(restaurant.image)) continue;
    const image = restaurantPool[stableIndex(`restaurant:${restaurant.restaurantKey}`, restaurantPool.length)];
    if (!image) {
      unresolvedRestaurants += 1;
      continue;
    }
    markRestaurantImage(restaurant, image, {
      sourceId: 'grab-image-pool',
      url: 'https://portal.grab.com/foodweb/merchants',
      match: 'grab-image-pool',
      fallback: true,
    });
    fallbackRestaurantImages += 1;
  }
  for (const item of catalog.menuItems) {
    if (isGrabImageUrl(item.image)) continue;
    const image = menuPool[stableIndex(`menu:${item.restaurantKey}:${item.name}`, menuPool.length)]
      || restaurantPool[stableIndex(`menu-restaurant:${item.restaurantKey}`, restaurantPool.length)];
    if (!image) {
      unresolvedMenus += 1;
      continue;
    }
    markMenuImage(item, image, {
      sourceId: 'grab-image-pool',
      url: 'https://portal.grab.com/foodweb/merchants',
      match: 'grab-image-pool',
      fallback: true,
    });
    fallbackMenuImages += 1;
  }
  return { fallbackRestaurantImages, fallbackMenuImages, unresolvedRestaurants, unresolvedMenus };
};

const updateCatalogMetadata = (catalog, requestSummary) => {
  const policy = catalog.provenancePolicy || {};
  policy.sourceBacked = unique([...(policy.sourceBacked || []), 'restaurant photo', 'menu item photo']);
  policy.synthetic = (policy.synthetic || []).filter((field) => field !== 'image');
  policy.notCaptured = (policy.notCaptured || []).filter((field) => !['restaurant photos', 'menu item photos'].includes(field));
  catalog.provenancePolicy = policy;
  catalog.generatedAt = observedAt;
  catalog.imageEnrichment = {
    collector: 'scripts/workspace/enrich-hanoi-grab-images.mjs',
    observedAt,
    endpoint: detailBaseUrl,
    searchEndpoint: searchUrl,
    publicOnly: true,
    antiBotBypass: false,
    rateLimitMs: rateMs,
    requestSummary,
  };
  catalog.collection = {
    ...(catalog.collection || {}),
    imageEnricher: 'scripts/workspace/enrich-hanoi-grab-images.mjs',
    imageSource: 'GrabFood public merchant detail/search response',
    imageEnrichedAt: observedAt,
  };
};

const updateReport = async (summary) => {
  if (!await fileExists(reportPath)) return;
  const report = await readJson(reportPath);
  report.imageEnrichment = {
    collector: 'scripts/workspace/enrich-hanoi-grab-images.mjs',
    observedAt,
    endpoint: detailBaseUrl,
    searchEndpoint: searchUrl,
    publicOnly: true,
    antiBotBypass: false,
    requestSummary: summary,
  };
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`);
};

const main = async () => {
  const catalog = await readJson(catalogPath);
  if (!Array.isArray(catalog.restaurants) || !Array.isArray(catalog.menuItems)) throw new Error('Catalog thiếu restaurants/menuItems');
  const menusByRestaurant = menuItemsByRestaurant(catalog.menuItems);
  const endIndex = limit ? Math.min(catalog.restaurants.length, startIndex + limit) : catalog.restaurants.length;
  const selectedRestaurants = catalog.restaurants.slice(startIndex, endIndex);
  const detailById = new Map();
  const errors = [];
  const directSourceRows = new Set();
  const directGrabRows = new Set();
  const summary = {
    dryRun: !writeCatalog,
    catalogRestaurants: catalog.restaurants.length,
    catalogMenuItems: catalog.menuItems.length,
    selectedRestaurants: selectedRestaurants.length,
    detailRequests: 0,
    searchRequests: 0,
    directRestaurantImages: 0,
    directMenuImages: 0,
    fallbackRestaurantImages: 0,
    fallbackMenuImages: 0,
    unresolvedRestaurants: 0,
    unresolvedMenus: 0,
    errors: 0,
  };

  const getCachedOrFetchDetail = async (sourceId) => {
    if (detailById.has(sourceId)) return detailById.get(sourceId);
    summary.detailRequests += 1;
    const detail = await getDetail(sourceId);
    detailById.set(sourceId, detail);
    return detail;
  };

  const processGrabRestaurant = async (restaurant) => {
    const sourceId = restaurant.sourceFacts?.sourceRecordId;
    if (!sourceId) throw new Error('GrabFood restaurant thiếu sourceFacts.sourceRecordId');
    const detail = await getCachedOrFetchDetail(sourceId);
    const counts = updateFromDetail(restaurant, detail, menusByRestaurant, {
      sourceId,
      url: detail.url,
      match: 'merchant-id',
    });
    if (counts.directRestaurantImages) directSourceRows.add(restaurant.restaurantKey);
    if (counts.directRestaurantImages) directGrabRows.add(restaurant.restaurantKey);
    summary.directRestaurantImages += counts.directRestaurantImages;
    summary.directMenuImages += counts.directMenuImages;
  };

  const processSearchRestaurant = async (restaurant) => {
    summary.searchRequests += 1;
    const searchMatch = await searchGrabMerchant(restaurant);
    if (!searchMatch || searchMatch.score < 0.2) {
      throw new Error(`Không tìm được Grab merchant đủ khớp (score=${searchMatch?.score?.toFixed(2) || '0.00'})`);
    }
    let counts;
    try {
      const detail = await getCachedOrFetchDetail(searchMatch.id);
      counts = updateFromSearch(restaurant, searchMatch, menusByRestaurant, detail);
    } catch (error) {
      if (!searchMatch.image) throw error;
      markRestaurantImage(restaurant, searchMatch.image, {
        sourceId: searchMatch.id,
        url: searchMatch.searchUrl,
        match: `search-image-score:${searchMatch.score.toFixed(2)}`,
      });
      counts = { directRestaurantImages: 1, directMenuImages: 0 };
    }
    if (counts.directRestaurantImages) directSourceRows.add(restaurant.restaurantKey);
    if (counts.directRestaurantImages && restaurant.source?.platform === 'GrabFood') directGrabRows.add(restaurant.restaurantKey);
    summary.directRestaurantImages += counts.directRestaurantImages;
    summary.directMenuImages += counts.directMenuImages;
  };

  for (const restaurant of selectedRestaurants.filter((row) => row.source?.platform === 'GrabFood')) {
    try {
      await processGrabRestaurant(restaurant);
    } catch (error) {
      if (error instanceof HttpError && error.status === 404) {
        try {
          await processSearchRestaurant(restaurant);
          continue;
        } catch (searchError) {
          error = searchError;
        }
      }
      errors.push({ restaurantKey: restaurant.restaurantKey, name: restaurant.name, message: error instanceof Error ? error.message : String(error) });
      console.error(`⚠️ Bỏ qua ${restaurant.restaurantKey}: ${errors.at(-1).message}`);
    }
  }
  for (const restaurant of selectedRestaurants.filter((row) => row.source?.platform !== 'GrabFood')) {
    try {
      await processSearchRestaurant(restaurant);
    } catch (error) {
      errors.push({ restaurantKey: restaurant.restaurantKey, name: restaurant.name, message: error instanceof Error ? error.message : String(error) });
      console.error(`⚠️ Bỏ qua ${restaurant.restaurantKey}: ${errors.at(-1).message}`);
    }
  }

  const restaurantPool = grabImagesFrom(catalog.restaurants.map((restaurant) => restaurant.image));
  const menuPool = grabImagesFrom(catalog.menuItems.map((item) => item.image));
  if (selectedRestaurants.length === catalog.restaurants.length) {
    const fallback = updateFallbackImages(catalog, menusByRestaurant, restaurantPool, menuPool);
    summary.fallbackRestaurantImages += fallback.fallbackRestaurantImages;
    summary.fallbackMenuImages += fallback.fallbackMenuImages;
    summary.unresolvedRestaurants = fallback.unresolvedRestaurants;
    summary.unresolvedMenus = fallback.unresolvedMenus;
  } else {
    summary.unresolvedRestaurants = selectedRestaurants.filter((restaurant) => !isGrabImageUrl(restaurant.image)).length;
    summary.unresolvedMenus = selectedRestaurants.reduce((count, restaurant) => count + (menusByRestaurant.get(restaurant.restaurantKey) || []).filter((item) => !isGrabImageUrl(item.image)).length, 0);
  }
  summary.errors = errors.length;
  summary.directSourceRows = directSourceRows.size;
  summary.directGrabRows = directGrabRows.size;
  summary.uniqueRestaurantImages = new Set(catalog.restaurants.map((restaurant) => restaurant.image)).size;
  summary.uniqueMenuImages = new Set(catalog.menuItems.map((item) => item.image)).size;

  if (writeCatalog) {
    const sourceGrabRows = catalog.restaurants.filter((restaurant) => restaurant.source?.platform === 'GrabFood').length;
    const directCoverage = sourceGrabRows ? directGrabRows.size / sourceGrabRows : 0;
    if (directCoverage < 0.8) throw new Error(`Direct Grab coverage quá thấp: ${(directCoverage * 100).toFixed(1)}% (cần ít nhất 80%)`);
    if (summary.unresolvedRestaurants || summary.unresolvedMenus) throw new Error(`Còn record thiếu ảnh: restaurants=${summary.unresolvedRestaurants}, menu=${summary.unresolvedMenus}`);
    updateCatalogMetadata(catalog, summary);
    await writeFile(catalogPath, `${JSON.stringify(catalog, null, 2)}\n`);
    await updateReport(summary);
  }

  console.log(JSON.stringify({
    ...summary,
    catalogPath,
    cacheDirectory,
    status: writeCatalog ? 'WRITTEN' : 'DRY_RUN',
    sampleErrors: errors.slice(0, 10),
  }, null, 2));
};

await main();

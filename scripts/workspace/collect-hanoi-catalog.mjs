#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { access, mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import process from 'node:process';

const workspaceRoot = resolve(new URL('..', import.meta.url).pathname);
const dataRoot = resolve(workspaceRoot, 'data');
const grabSourceDir = resolve(dataRoot, 'sources/hanoi-grab');
const grabCacheDir = resolve(grabSourceDir, '.cache');
const defaultGrabSupplementalInput = resolve(dataRoot, 'sources/hanoi-grab/public-listings.json');
const defaultShopeeInput = resolve(dataRoot, 'sources/hanoi-shopeefood/public-listings.json');
const defaultCatalogPath = resolve(dataRoot, 'catalog/hanoi-catalog.json');
const defaultReportPath = resolve(dataRoot, 'catalog/hanoi-collection-report.json');
const observedAt = process.env.OBSERVED_AT || new Date().toISOString().slice(0, 10);

const grabPages = [
  { kind: 'cuisine', label: 'Món Hàn', url: 'https://food.grab.com/vn/vi/hanoi/cuisines/m%C3%B3n-h%C3%A0n-delivery/300011' },
  { kind: 'cuisine', label: 'Món Á', url: 'https://food.grab.com/vn/vi/hanoi/cuisines/m%C3%B3n-%C3%A1-delivery/300004' },
  { kind: 'cuisine', label: 'Món Âu', url: 'https://food.grab.com/vn/vi/hanoi/cuisines/m%C3%B3n-%C3%A2u-delivery/300083' },
  { kind: 'cuisine', label: 'Món Ấn', url: 'https://food.grab.com/vn/vi/hanoi/cuisines/m%C3%B3n-%E1%BA%A4n-delivery/300052' },
  { kind: 'cuisine', label: 'Món miền Bắc Việt Nam', url: 'https://food.grab.com/vn/vi/hanoi/cuisines/m%C3%B3n-mi%E1%BB%81n-b%E1%BA%AFc-vi%E1%BB%87t-nam-delivery/300038' },
  { kind: 'dish', label: 'Đồ ăn vặt', url: 'https://food.grab.com/vn/vi/hanoi/dishes/%C4%91%E1%BB%93-%C4%83n-v%E1%BA%B7t-delivery/401948' },
  { kind: 'dish', label: 'Gỏi', url: 'https://food.grab.com/vn/vi/hanoi/dishes/g%E1%BB%8Fi-delivery/400957' },
  { kind: 'dish', label: 'Hải sản', url: 'https://food.grab.com/vn/vi/hanoi/dishes/m%C3%B3n-h%E1%BA%A3i-s%E1%BA%A3n-delivery/401874' },
  { kind: 'dish', label: 'Cuốn', url: 'https://food.grab.com/vn/vi/hanoi/dishes/cu%E1%BB%91n-delivery/401783' },
  { kind: 'dish', label: 'Đồ uống', url: 'https://food.grab.com/vn/vi/hanoi/dishes/%C4%91%E1%BB%93-u%E1%BB%91ng-delivery/400181' },
  { kind: 'dish', label: 'Hủ tiếu', url: 'https://food.grab.com/vn/vi/hanoi/dishes/h%E1%BB%A7-ti%E1%BA%BFu-delivery/401026' },
  { kind: 'dish', label: 'Thịt bò', url: 'https://food.grab.com/vn/vi/hanoi/dishes/th%E1%BB%8Bt-b%C3%B2-delivery/400153' },
  { kind: 'dish', label: 'Gà rán', url: 'https://food.grab.com/vn/vi/hanoi/dishes/g%C3%A0-r%C3%A1n-delivery/400803' },
  { kind: 'dish', label: 'Thịt heo', url: 'https://food.grab.com/vn/vi/hanoi/dishes/m%C3%B3n-th%E1%BB%8Bt-heo-delivery/401654' },
  { kind: 'dish', label: 'Mì ống', url: 'https://food.grab.com/vn/vi/hanoi/dishes/m%C3%AC-%E1%BB%91ng-delivery/401558' },
  { kind: 'dish', label: 'Phở', url: 'https://food.grab.com/vn/vi/hanoi/dishes/ph%E1%BB%9F-delivery/401594' },
  { kind: 'dish', label: 'Cháo', url: 'https://food.grab.com/vn/vi/hanoi/dishes/ch%C3%A1o-delivery/401678' },
  { kind: 'dish', label: 'Bánh cuốn', url: 'https://food.grab.com/vn/vi/hanoi/dishes/b%C3%A1nh-cu%E1%BB%91n-delivery/400301' },
  { kind: 'dish', label: 'Bò kho', url: 'https://food.grab.com/vn/vi/hanoi/dishes/b%C3%B2-kho-delivery/402234' },
  { kind: 'dish', label: 'Trà sữa', url: 'https://food.grab.com/vn/vi/hanoi/dishes/tr%C3%A0-s%E1%BB%AFa-delivery/401341' },
  { kind: 'dish', label: 'Chè Thái', url: 'https://food.grab.com/vn/vi/hanoi/dishes/ch%C3%A8-th%C3%A1i-delivery/400535' },
  { kind: 'dish', label: 'Tráng miệng', url: 'https://food.grab.com/vn/en/hanoi/dishes/dessert-delivery/400686' },
  { kind: 'dish', label: 'Đồ uống (English fallback)', url: 'https://food.grab.com/vn/en/hanoi/dishes/beverage-delivery/400181' },
  { kind: 'dish', label: 'Bánh mì (English fallback)', url: 'https://food.grab.com/vn/en/hanoi/dishes/banh-mi-delivery/400122' },
  { kind: 'dish', label: 'Trái cây', url: 'https://food.grab.com/vn/vi/hanoi/dishes/tr%C3%A1i-c%C3%A2y-delivery/400851' },
];

const districtCentroids = [
  ['Hoàn Kiếm', 21.0285, 105.8542],
  ['Tây Hồ', 21.0703, 105.8185],
  ['Ba Đình', 21.0338, 105.8266],
  ['Thanh Xuân', 20.9950, 105.8137],
  ['Hai Bà Trưng', 20.9991, 105.8549],
  ['Đống Đa', 21.0150, 105.8268],
  ['Cầu Giấy', 21.0367, 105.7936],
  ['Hoàng Mai', 20.9820, 105.8570],
  ['Long Biên', 21.0457, 105.8803],
  ['Hà Đông', 20.9710, 105.7786],
  ['Nam Từ Liêm', 21.0140, 105.7650],
  ['Bắc Từ Liêm', 21.0710, 105.7580],
  ['Gia Lâm', 21.0130, 105.9550],
  ['Hoài Đức', 21.0250, 105.7080],
  ['Thanh Trì', 20.9460, 105.8430],
  ['Thường Tín', 20.8430, 105.8630],
];

const districtAliases = new Map([
  ['hoan kiem', 'Hoàn Kiếm'], ['tay ho', 'Tây Hồ'], ['ba dinh', 'Ba Đình'],
  ['thanh xuan', 'Thanh Xuân'], ['hai ba trung', 'Hai Bà Trưng'], ['dong da', 'Đống Đa'],
  ['cau giay', 'Cầu Giấy'], ['hoang mai', 'Hoàng Mai'], ['long bien', 'Long Biên'],
  ['ha dong', 'Hà Đông'], ['nam tu liem', 'Nam Từ Liêm'], ['bac tu liem', 'Bắc Từ Liêm'],
  ['gia lam', 'Gia Lâm'], ['hoai duc', 'Hoài Đức'], ['thanh tri', 'Thanh Trì'],
  ['thuong tin', 'Thường Tín'],
]);

const streetDistrictHints = [
  [['cổ linh', 'nguyễn văn cừ', 'nguyễn văn linh', 'ngọc lâm', 'hồng tiến', 'bồ đề', 'phúc đồng', 'ngọc thụy'], 'Long Biên'],
  [['võ chí công', 'âu cơ', 'an dương', 'yên phụ', 'nghi tàm', 'hồ tây'], 'Tây Hồ'],
  [['đào tấn', 'đội cấn', 'giảng võ', 'thành công', 'ngọc hà', 'quán thánh', 'văn cao', 'điện biên'], 'Ba Đình'],
  [['hàng bông', 'hàng tre', 'hàng vôi', 'hàng buồm', 'hàng bè', 'hàng đậu', 'hàng than', 'hàng mắm', 'lý quốc sư', 'phủ doãn', 'tràng tiền'], 'Hoàn Kiếm'],
  [['xã đàn', 'tây sơn', 'khâm thiên', 'láng', 'thái hà', 'phạm ngọc thạch', 'nguyễn thái học', 'văn miếu', 'đặng văn ngữ', 'phạm hồng thái'], 'Đống Đa'],
  [['bạch mai', 'lò đúc', 'hồng mai', 'đại cồ việt', 'phố huế', 'hàm long', 'nguyễn đình chiểu', 'bà triệu', 'trần khát chân'], 'Hai Bà Trưng'],
  [['vương thừa vũ', 'nguyễn xiển', 'giáp nhất', 'hạ đình', 'nguyễn quý đức', 'chính kinh', 'khương đình'], 'Thanh Xuân'],
  [['xuân thủy', 'minh khai', 'cầu giấy', 'quan hoa', 'yên hòa', 'hoàng quốc việt', 'nghĩa đô', 'hồ tùng mậu'], 'Cầu Giấy'],
  [['trương định', 'định công', 'hoàng mai', 'minh khai', 'lĩnh nam'], 'Hoàng Mai'],
  [['phạm văn đồng', 'kiều mai', 'cổ nhuế', 'phúc diễn', 'giao lưu'], 'Bắc Từ Liêm'],
  [['mỹ đình', 'hồ tùng mậu', 'cầu diễn', 'lê đức thọ'], 'Nam Từ Liêm'],
  [['nguyễn văn lộc', 'cầu am', 'hà đông', 'mỗ lao', 'văn quán', 'dương nội'], 'Hà Đông'],
  [['trâu quỳ', 'đa tốn', 'ocean park', 'đại dương', 'ngô xuân quảng'], 'Gia Lâm'],
  [['xuân phương', 'tây mỗ', 'phùng khoang'], 'Nam Từ Liêm'],
  [['trạm trôi', 'vân canh', 'an khánh', 'yên sở', 'song phương'], 'Hoài Đức'],
];

const menuTemplates = [
  { match: /phở|hủ tiếu|mì|mỳ|bún|miến/i, items: [['Phần truyền thống', 45000], ['Phần đặc biệt', 65000], ['Topping thêm', 20000], ['Nước uống', 15000]] },
  { match: /cơm|gạo lứt|rice|healthy/i, items: [['Cơm phần tiêu chuẩn', 55000], ['Cơm phần đặc biệt', 75000], ['Món thêm', 25000], ['Canh hoặc nước', 15000]] },
  { match: /gà|chicken|burger|pizza|taco|bánh mì/i, items: [['Phần chính', 55000], ['Combo cá nhân', 79000], ['Phần thêm', 25000], ['Nước ngọt', 15000]] },
  { match: /trà sữa|trà|cà phê|nước ép|đồ uống|beverage|coffee/i, items: [['Size M', 35000], ['Size L', 45000], ['Topping', 10000], ['Bánh ăn kèm', 30000]] },
  { match: /chè|tráng miệng|dessert|bánh|kem|sữa chua/i, items: [['Phần thường', 30000], ['Phần đặc biệt', 45000], ['Combo hai phần', 75000], ['Topping thêm', 10000]] },
  { match: /lẩu|hải sản|ốc|nướng|thái|hàn|hoa|ấn|âu|mexico/i, items: [['Phần một người', 65000], ['Set hai người', 169000], ['Món gọi thêm', 45000], ['Nước uống', 15000]] },
  { match: /chay|veggie|salad/i, items: [['Suất chay tiêu chuẩn', 45000], ['Suất chay đặc biệt', 65000], ['Salad thêm', 25000], ['Nước thảo mộc', 20000]] },
];
const fallbackMenu = [['Món bán chạy', 50000], ['Món đặc biệt', 70000], ['Món ăn kèm', 25000], ['Nước uống', 15000]];

const args = process.argv.slice(2);
const hasFlag = (name) => args.includes(name);
const valueOf = (name, fallback) => {
  const index = args.indexOf(name);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
};
const platform = valueOf('--platform', 'both');
const maxPages = Number(valueOf('--max-pages', String(grabPages.length)));
const rateMs = Math.max(0, Number(valueOf('--rate-ms', process.env.RATE_MS || '650')));
const timeoutMs = Math.max(1000, Number(valueOf('--timeout-ms', process.env.TIMEOUT_MS || '20000')));
const shopeeInput = resolve(workspaceRoot, valueOf('--shopee-input', defaultShopeeInput));
const grabSupplementalInput = resolve(workspaceRoot, valueOf('--grab-supplemental-input', defaultGrabSupplementalInput));
const catalogPath = resolve(workspaceRoot, valueOf('--catalog', defaultCatalogPath));
const reportPath = resolve(workspaceRoot, valueOf('--report', defaultReportPath));
const refresh = hasFlag('--refresh');
const cacheOnly = hasFlag('--cache-only');
const skipGrab = platform === 'shopee' || hasFlag('--skip-grab');
const skipShopee = platform === 'grab' || hasFlag('--skip-shopee');

const isRecord = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
const clean = (value) => (value === undefined || value === null || value === '' ? undefined : value);
const delay = (ms) => new Promise((resolveDelay) => setTimeout(resolveDelay, ms));
const removeDiacritics = (value) => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D');
const normalized = (value) => removeDiacritics(value).toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
const slugify = (value) => normalized(value).replace(/\s+/g, '-').slice(0, 120) || 'merchant';
const hashNumber = (value) => Number.parseInt(createHash('sha1').update(String(value)).digest('hex').slice(0, 8), 16);
const sha256 = (value) => createHash('sha256').update(value).digest('hex');

const decodeHtml = (value) => String(value || '')
  .replace(/&nbsp;|&#160;/gi, ' ')
  .replace(/&amp;/gi, '&')
  .replace(/&quot;/gi, '"')
  .replace(/&#x27;|&#39;|&apos;/gi, "'")
  .replace(/&lt;/gi, '<')
  .replace(/&gt;/gi, '>')
  .replace(/&#(x?[0-9a-f]+);/gi, (_, raw) => {
    const codePoint = raw.toLowerCase().startsWith('x') ? Number.parseInt(raw.slice(1), 16) : Number.parseInt(raw, 10);
    return Number.isFinite(codePoint) ? String.fromCodePoint(codePoint) : _;
  });
const stripMarkup = (value) => decodeHtml(String(value || '').replace(/<!--[\s\S]*?-->/g, '').replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();

const parseGrabPage = (html, page) => {
  const title = stripMarkup(html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i)?.[1] || page.label);
  const chunks = html.split('<div class="ant-col-24 RestaurantListCol').slice(1);
  const records = [];
  for (const chunk of chunks) {
    const href = chunk.match(/<a[^>]+href="([^\"]*\/restaurant\/[^\"]+)"/i)?.[1];
    const name = stripMarkup(
      chunk.match(/<h2[^>]*>([\s\S]*?)<\/h2>/i)?.[1]
        || chunk.match(/<p[^>]*class="[^"]*name[^"]*"[^>]*>([\s\S]*?)<\/p>/i)?.[1],
    );
    if (!href || !name) continue;
    const cuisine = stripMarkup(chunk.match(/basicInfoRow[^>]*cuisine[^>]*>([\s\S]*?)<\/div>/i)?.[1]);
    const ratingText = chunk.match(/<div class="numbersChild[^>]*>[\s\S]*?ratingStar[\s\S]*?<\/div>\s*([0-5](?:[.,]\d+)?)/i)?.[1];
    const deliveryText = stripMarkup(chunk.match(/<div class="basicInfoRow[^>]*numbers[^>]*>([\s\S]*?)<\/div>\s*<\/div>/i)?.[1] || chunk.match(/deliveryClock[\s\S]{0,500}/i)?.[0]);
    const etaMatch = deliveryText.match(/([0-9]+)\s*(?:phút|mins?|minutes?)/i);
    const distanceMatch = deliveryText.match(/([0-9]+(?:[.,][0-9]+)?)\s*km/i);
    const promo = /promoTagHead[^>]*>\s*Promo\s*</i.test(chunk) || /discountText[^>]*>\s*[^<\s]/i.test(chunk) ? 'Promo' : undefined;
    const sourceUrl = new URL(href.replace(/\?$/, ''), 'https://food.grab.com').toString();
    const sourceId = sourceUrl.match(/\/([^/]+)\/?$/)?.[1] || slugify(name);
    records.push({
      sourceId,
      name,
      cuisine: clean(cuisine),
      rating: ratingText ? Number(ratingText.replace(',', '.')) : undefined,
      estimatedDeliveryMinutes: etaMatch ? Number(etaMatch[1]) : undefined,
      distanceKmFromSourceLocation: distanceMatch ? Number(distanceMatch[1].replace(',', '.')) : undefined,
      promotionLabel: promo,
      url: sourceUrl,
      listingUrl: page.url,
      listingLabel: page.label,
      sourceFields: ['name', 'cuisine', 'rating', 'estimatedDeliveryMinutes', 'distanceKmFromSourceLocation', 'promotionLabel'],
    });
  }
  return { title, records };
};

const cacheFileFor = (url) => resolve(grabCacheDir, `${sha256(url).slice(0, 24)}.json`);
const readJson = async (filePath) => JSON.parse(await readFile(filePath, 'utf8'));
const fileExists = async (filePath) => access(filePath).then(() => true).catch(() => false);

const fetchGrabPage = async (page) => {
  const cachePath = cacheFileFor(page.url);
  if (!refresh && await fileExists(cachePath)) {
    const cached = await readJson(cachePath);
    return { ...cached, cacheHit: true, parsed: parseGrabPage(cached.body, page) };
  }
  if (cacheOnly) throw new Error(`Không có cache cho ${page.url}`);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(page.url, {
      headers: {
        accept: 'text/html,application/xhtml+xml',
        'user-agent': 'delivery-local-catalog-fixture/1.0 (+local-development; public-pages-only)',
      },
      redirect: 'follow',
      signal: controller.signal,
    });
    const body = await response.text();
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const cached = {
      url: page.url,
      fetchedAt: new Date().toISOString(),
      status: response.status,
      contentType: response.headers.get('content-type'),
      body,
    };
    await mkdir(grabCacheDir, { recursive: true });
    await writeFile(cachePath, `${JSON.stringify(cached)}\n`);
    return { ...cached, cacheHit: false, parsed: parseGrabPage(body, page) };
  } finally {
    clearTimeout(timeout);
  }
};

const districtFromText = (value) => {
  const text = normalized(value);
  for (const [alias, district] of districtAliases) if (text.includes(alias)) return district;
  for (const [hints, district] of streetDistrictHints) {
    if (hints.some((hint) => text.includes(normalized(hint)))) return district;
  }
  return 'Hà Nội (chưa xác định quận)';
};

const centroidFor = (district, key) => {
  const base = districtCentroids.find(([name]) => name === district) || ['Hà Nội', 21.0278, 105.8342];
  const seed = hashNumber(key);
  const latitudeOffset = ((seed % 1000) / 1000 - 0.5) * 0.004;
  const longitudeOffset = (((Math.floor(seed / 1000) % 1000) / 1000) - 0.5) * 0.004;
  return { latitude: Number((base[1] + latitudeOffset).toFixed(6)), longitude: Number((base[2] + longitudeOffset).toFixed(6)) };
};

const parseHours = (hours) => {
  const matches = [...String(hours || '').matchAll(/([01]?\d|2[0-3]):([0-5]\d)/g)].map((match) => `${match[1].padStart(2, '0')}:${match[2]}`);
  if (!matches.length) return { openingHour: '08:00', closingHour: '22:00', provenance: 'synthetic_mock' };
  return { openingHour: matches[0], closingHour: matches[matches.length - 1], provenance: 'source_backed' };
};

const chooseMenu = (restaurant) => {
  const haystack = `${restaurant.name} ${restaurant.cuisine}`;
  return menuTemplates.find((template) => template.match.test(haystack))?.items || fallbackMenu;
};

const toCanonicalRecord = (raw, platformName) => {
  const district = districtFromText(`${raw.address || ''} ${raw.name || ''}`);
  const restaurantKey = slugify(`${platformName}-${raw.sourceId}`);
  const coordinates = centroidFor(district, restaurantKey);
  const hours = parseHours(raw.hours);
  const isGrab = platformName === 'GrabFood';
  const address = raw.address || `Khu vực ${district.replace(' (chưa xác định quận)', '')}, Hà Nội (địa chỉ chi tiết không hiển thị trong listing)`;
  const sourceFields = raw.sourceFields || ['name', 'address', 'cuisine'];
  const sourceFacts = {
    ...(raw.priceRangeVnd ? { priceRangeVnd: raw.priceRangeVnd } : {}),
    ...(raw.ratingLabel ? { ratingLabel: raw.ratingLabel } : {}),
    ...(clean(raw.rating) !== undefined ? { rating: raw.rating } : {}),
    ...(clean(raw.distanceKmFromSourceLocation) !== undefined ? { distanceKmFromSourceLocation: raw.distanceKmFromSourceLocation } : {}),
    ...(clean(raw.estimatedDeliveryMinutes) !== undefined ? { estimatedDeliveryMinutes: raw.estimatedDeliveryMinutes } : {}),
    ...(raw.promotionLabel ? { promotionLabel: raw.promotionLabel } : {}),
    cuisineLabels: raw.cuisine ? raw.cuisine.split(/,\s*/).filter(Boolean) : [],
    addressConfidence: isGrab && !raw.address ? 'not-exposed-in-listing' : 'source-detail-or-listing',
    coordinateConfidence: 'approximate-district-centroid-jitter',
    menuProvenance: 'synthetic_mock',
    sourceRecordId: raw.sourceId,
  };
  const restaurant = {
    restaurantKey,
    name: raw.name,
    address,
    openingHour: hours.openingHour,
    closingHour: hours.closingHour,
    phone: null,
    image: null,
    description: `${raw.cuisine || 'Nhà hàng'} tại Hà Nội; mô tả dùng cho local development.`,
    addressLat: coordinates.latitude,
    addressLng: coordinates.longitude,
    cuisine: raw.cuisine || 'Ẩm thực tổng hợp',
    source: {
      platform: platformName,
      url: raw.url,
      observedAt,
      sourceFields,
      ...(raw.listingUrls?.length ? { listingUrls: raw.listingUrls } : {}),
    },
    sourceFacts,
    provenance: {
      address: raw.address ? 'source_backed' : 'synthetic_mock',
      openingHour: hours.provenance,
      closingHour: hours.provenance,
      coordinates: 'synthetic_mock',
      description: 'synthetic_mock',
      menu: 'synthetic_mock',
    },
    district,
  };
  const [items] = [chooseMenu(restaurant)];
  const menuItems = items.map(([label, price], index) => ({
    restaurantKey,
    name: `${label} - ${raw.cuisine?.split(/,\s*/)[0] || 'Hà Nội'}`,
    description: `Món mock ${index + 1} dùng để demo catalog ${raw.name}.`,
    price,
    status: 'AVAILABLE',
    provenance: 'synthetic_mock',
  }));
  return { restaurant, menuItems };
};

const mergeRawRecords = (records, platformName) => {
  const merged = new Map();
  for (const raw of records) {
    if (!raw || !raw.sourceId || !raw.name || !raw.url) continue;
    const key = `${platformName}:${raw.sourceId}`;
    const current = merged.get(key);
    if (!current) {
      merged.set(key, { ...raw, listingUrls: raw.listingUrl ? [raw.listingUrl] : [] });
      continue;
    }
    const listingUrls = [...new Set([...current.listingUrls, raw.listingUrl].filter(Boolean))];
    const sourceFields = [...new Set([...(current.sourceFields || []), ...(raw.sourceFields || [])])];
    const cuisine = [...new Set(`${current.cuisine || ''},${raw.cuisine || ''}`.split(',').map((item) => item.trim()).filter(Boolean))].join(', ');
    merged.set(key, { ...current, ...raw, cuisine, sourceFields, listingUrls });
  }
  return [...merged.values()];
};

const makeReport = ({ pages, errors, restaurants, menuItems, rawRecordCounts }) => {
  const countBy = (values) => Object.fromEntries([...values.reduce((map, value) => map.set(value, (map.get(value) || 0) + 1), new Map())].sort((a, b) => b[1] - a[1]));
  const cuisines = restaurants.flatMap((row) => String(row.cuisine || '').split(/,\s*/).filter(Boolean));
  return {
    schemaVersion: 1,
    dataset: `realistic-catalog-hanoi-${observedAt}`,
    city: 'Hà Nội',
    generatedAt: new Date().toISOString(),
    collectionPolicy: {
      publicOnly: true,
      privateEndpointsCalled: false,
      antiBotBypass: false,
      rateLimitMs: rateMs,
      defaultRequestTimeoutMs: timeoutMs,
    },
    pages,
    errors,
    rawRecordCounts,
    counts: {
      restaurants: restaurants.length,
      menuItems: menuItems.length,
      sourcePlatforms: countBy(restaurants.map((row) => row.source.platform)),
    },
    coverage: {
      districts: countBy(restaurants.map((row) => row.district)),
      cuisines: countBy(cuisines),
      requestedAreas: districtCentroids.map(([name]) => name),
      observedAreas: districtCentroids.map(([name]) => name).filter((name) => restaurants.some((row) => row.district === name)),
      unobservedAreas: districtCentroids.map(([name]) => name).filter((name) => !restaurants.some((row) => row.district === name)),
    },
    limitations: [
      'GrabFood listing SSR exposes name, cuisine, rating, ETA, distance and promo labels but not a full merchant address/menu payload.',
      'ShopeeFood direct app-shell responses are not treated as private API permission; public detail/listing records are loaded from the committed public snapshot adapter.',
      'Coordinates are approximate district-centroid jitter for local nearby/matching demos, never production dispatch or geocoding truth.',
      'Menu items are synthetic mock data unless a future public menu snapshot is explicitly added with source fields.',
    ],
  };
};

const collectGrab = async () => {
  if (skipGrab) return { records: [], pages: [], errors: [], rawRecordCounts: { GrabFood: 0 } };
  const records = [];
  const pages = [];
  const errors = [];
  const selectedPages = grabPages.slice(0, Math.max(0, maxPages));
  for (const [index, page] of selectedPages.entries()) {
    if (index > 0) await delay(rateMs);
    try {
      const result = await fetchGrabPage(page);
      const parsedRecords = result.parsed.records.map((record) => ({ ...record, observedAt }));
      records.push(...parsedRecords);
      pages.push({
        platform: 'GrabFood',
        kind: page.kind,
        label: page.label,
        url: page.url,
        status: result.status,
        cacheHit: result.cacheHit,
        fetchedAt: result.fetchedAt,
        title: result.parsed.title,
        recordCount: parsedRecords.length,
        contentSha256: sha256(result.body),
        records: parsedRecords,
      });
      console.log(`GrabFood ${index + 1}/${selectedPages.length}: ${parsedRecords.length} records — ${page.label}`);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      errors.push({ platform: 'GrabFood', url: page.url, message });
      pages.push({ platform: 'GrabFood', kind: page.kind, label: page.label, url: page.url, error: message, recordCount: 0 });
      console.error(`GrabFood bỏ qua ${page.url}: ${message}`);
    }
  }
  if (await fileExists(grabSupplementalInput)) {
    const input = await readJson(grabSupplementalInput);
    if (isRecord(input) && input.platform === 'GrabFood' && Array.isArray(input.records)) {
      const supplementalRecords = input.records.map((record) => ({ ...record, observedAt }));
      records.push(...supplementalRecords);
      pages.push({ platform: 'GrabFood', url: input.sourceUrl, sourceSnapshot: grabSupplementalInput, recordCount: supplementalRecords.length, collectionMethod: input.collectionMethod, records: supplementalRecords });
      console.log(`GrabFood public chain snapshot: ${supplementalRecords.length} records — ${grabSupplementalInput}`);
    }
  }
  return { records, pages, errors, rawRecordCounts: { GrabFood: records.length } };
};

const collectShopee = async () => {
  if (skipShopee) return { records: [], pages: [], errors: [], rawRecordCounts: { ShopeeFood: 0 } };
  if (!(await fileExists(shopeeInput))) throw new Error(`Không tìm thấy ShopeeFood input ${shopeeInput}`);
  const input = await readJson(shopeeInput);
  if (!isRecord(input) || input.platform !== 'ShopeeFood' || !Array.isArray(input.records)) throw new Error('ShopeeFood input không đúng schema');
  const records = input.records.map((record) => ({ ...record, observedAt }));
  console.log(`ShopeeFood public snapshot: ${records.length} records — ${shopeeInput}`);
  return {
    records,
    pages: [{ platform: 'ShopeeFood', url: 'https://shopeefood.vn/ha-noi/food', sourceSnapshot: shopeeInput, recordCount: records.length, collectionMethod: input.collectionMethod }],
    errors: [],
    rawRecordCounts: { ShopeeFood: records.length },
  };
};

const main = async () => {
  if (hasFlag('--help')) {
    console.log('Usage: node scripts/collect-hanoi-catalog.mjs [--refresh] [--platform both|grab|shopee] [--max-pages N] [--cache-only]');
    process.exit(0);
  }
  await mkdir(resolve(dataRoot, 'catalog'), { recursive: true });
  await mkdir(grabSourceDir, { recursive: true });
  const [grab, shopee] = await Promise.all([collectGrab(), collectShopee()]);
  const grabRecords = mergeRawRecords(grab.records, 'GrabFood');
  const shopeeRecords = mergeRawRecords(shopee.records, 'ShopeeFood');
  const canonical = [...grabRecords.map((row) => toCanonicalRecord(row, 'GrabFood')), ...shopeeRecords.map((row) => toCanonicalRecord(row, 'ShopeeFood'))];
  const restaurants = canonical.map(({ restaurant }) => restaurant).sort((a, b) => a.restaurantKey.localeCompare(b.restaurantKey));
  const menuItems = canonical.flatMap(({ menuItems: items }) => items).sort((a, b) => `${a.restaurantKey}:${a.name}`.localeCompare(`${b.restaurantKey}:${b.name}`));
  const sourcePages = [...grab.pages, ...shopee.pages];
  const sourceErrors = [...grab.errors, ...shopee.errors];
  const report = makeReport({
    pages: sourcePages,
    errors: sourceErrors,
    restaurants,
    menuItems,
    rawRecordCounts: { ...grab.rawRecordCounts, ...shopee.rawRecordCounts, GrabFoodAfterDedupe: grabRecords.length, ShopeeFoodAfterDedupe: shopeeRecords.length },
  });
  const catalog = {
    schemaVersion: 1,
    dataset: report.dataset,
    city: 'Hà Nội',
    generatedAt: observedAt,
    provenancePolicy: {
      sourceBacked: ['restaurant name', 'publicly listed address or service area', 'opening/closing hour when exposed', 'price range/rating/ETA/distance/promotion labels when exposed', 'cuisine labels when exposed'],
      normalized: ['Vietnamese text, currency values, source URLs, observedAt, restaurantKey, district label'],
      synthetic: ['approximate coordinates', 'phone', 'image', 'description', 'menu item names/descriptions/prices', 'opening/closing hour when source did not expose it'],
      notCaptured: ['personal data', 'customer reviews', 'private/authenticated endpoints', 'payment/account/driver identity data', 'restaurant photos'],
    },
    collection: {
      collector: 'scripts/collect-hanoi-catalog.mjs',
      report: 'data/catalog/hanoi-collection-report.json',
      sourceSnapshot: 'data/sources/hanoi-grab/pages.json',
      grabSupplementalInput: 'data/sources/hanoi-grab/public-listings.json',
      shopeeInput: 'data/sources/hanoi-shopeefood/public-listings.json',
      publicOnly: true,
    },
    restaurants,
    menuItems,
  };
  await writeFile(resolve(grabSourceDir, 'pages.json'), `${JSON.stringify({ schemaVersion: 1, platform: 'GrabFood', city: 'Hà Nội', observedAt, pages: sourcePages.filter((page) => page.platform === 'GrabFood') }, null, 2)}\n`);
  await writeFile(catalogPath, `${JSON.stringify(catalog, null, 2)}\n`);
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`Catalog Hà Nội: ${restaurants.length} restaurants, ${menuItems.length} menu items`);
  console.log(`Coverage quận: ${Object.entries(report.coverage.districts).map(([name, count]) => `${name}=${count}`).join(', ')}`);
  if (sourceErrors.length) console.log(`Cảnh báo collector: ${sourceErrors.length} source errors; xem ${reportPath}`);
};

await main();

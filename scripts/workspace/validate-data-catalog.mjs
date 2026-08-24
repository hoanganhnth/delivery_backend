#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import process from 'node:process';

const workspaceRoot = resolve(new URL('..', import.meta.url).pathname);
const catalogPath = resolve(workspaceRoot, process.argv[2] || 'data/catalog/hanoi-catalog.json');
const fail = (message) => {
  console.error(`❌ ${message}`);
  process.exitCode = 1;
};

const catalog = JSON.parse(await readFile(catalogPath, 'utf8'));
const isHanoi = /hà nội|hanoi/i.test(String(catalog.city || ''));
if (catalog.schemaVersion !== 1) fail('schemaVersion phải là 1');
if (!Array.isArray(catalog.restaurants) || !catalog.restaurants.length) fail('restaurants phải là mảng khác rỗng');
if (!Array.isArray(catalog.menuItems) || !catalog.menuItems.length) fail('menuItems phải là mảng khác rỗng');

const restaurants = catalog.restaurants || [];
const menuItems = catalog.menuItems || [];
const restaurantKeys = new Set();
for (const restaurant of restaurants) {
  if (!restaurant.restaurantKey || restaurantKeys.has(restaurant.restaurantKey)) fail(`restaurantKey trùng/thiếu: ${restaurant.restaurantKey}`);
  restaurantKeys.add(restaurant.restaurantKey);
  if (!restaurant.name || !restaurant.address) fail(`restaurant thiếu name/address: ${restaurant.restaurantKey}`);
  if (!Number.isFinite(restaurant.addressLat) || !Number.isFinite(restaurant.addressLng)) fail(`restaurant thiếu tọa độ: ${restaurant.restaurantKey}`);
  if (isHanoi && (restaurant.addressLat < 20.5 || restaurant.addressLat > 21.6 || restaurant.addressLng < 105.4 || restaurant.addressLng > 106.3)) fail(`tọa độ ngoài bounding box Hà Nội: ${restaurant.restaurantKey}`);
  if (!['GrabFood', 'ShopeeFood'].includes(restaurant.source?.platform)) fail(`platform không hợp lệ: ${restaurant.restaurantKey}`);
  if (!String(restaurant.source?.url || '').startsWith('https://')) fail(`source URL thiếu/không https: ${restaurant.restaurantKey}`);
  if (isHanoi && restaurant.provenance?.menu !== 'synthetic_mock') fail(`menu provenance không được đánh dấu synthetic_mock: ${restaurant.restaurantKey}`);
}

const menuCounts = new Map();
for (const item of menuItems) {
  if (!restaurantKeys.has(item.restaurantKey)) fail(`menu orphan: ${item.restaurantKey}`);
  if (!item.name || !item.description || !Number.isFinite(item.price) || item.price <= 0) fail(`menu thiếu field: ${item.restaurantKey}/${item.name}`);
  if (isHanoi && item.provenance !== 'synthetic_mock') fail(`menu provenance không được đánh dấu synthetic_mock: ${item.restaurantKey}/${item.name}`);
  menuCounts.set(item.restaurantKey, (menuCounts.get(item.restaurantKey) || 0) + 1);
}
for (const key of restaurantKeys) if ((menuCounts.get(key) || 0) < 4) fail(`restaurant có dưới 4 menu item: ${key}`);

const counts = (values) => Object.fromEntries([...values.reduce((map, value) => map.set(value, (map.get(value) || 0) + 1), new Map())].sort((a, b) => b[1] - a[1]));
const districtCoverage = counts(restaurants.map((restaurant) => restaurant.district || 'missing'));
const platformCoverage = counts(restaurants.map((restaurant) => restaurant.source.platform));
console.log(JSON.stringify({
  dataset: catalog.dataset,
  city: catalog.city,
  restaurants: restaurants.length,
  menuItems: menuItems.length,
  platforms: platformCoverage,
  districts: districtCoverage,
  status: process.exitCode ? 'FAILED' : 'PASS',
}, null, 2));

if (process.exitCode) process.exit(1);

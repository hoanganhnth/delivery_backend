#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import process from 'node:process';

const workspaceRoot = resolve(new URL('../..', import.meta.url).pathname);
const catalogPath = resolve(workspaceRoot, process.argv[2] || 'data/catalog/hanoi-catalog.json');
const grabImageHost = process.env.GRAB_IMAGE_HOST || 'huawei-food-cms.grab.com';
const minRestaurantUniqueRatio = Number(process.env.MIN_GRAB_RESTAURANT_UNIQUE_RATIO || '0.7');
const minMenuUniqueRatio = Number(process.env.MIN_GRAB_MENU_UNIQUE_RATIO || '0.3');

const catalog = JSON.parse(await readFile(catalogPath, 'utf8'));
const restaurants = Array.isArray(catalog.restaurants) ? catalog.restaurants : [];
const menuItems = Array.isArray(catalog.menuItems) ? catalog.menuItems : [];
const errors = [];

const isGrabImage = (value) => {
  if (typeof value !== 'string' || !value.startsWith('https://')) return false;
  try {
    const url = new URL(value);
    return url.hostname === grabImageHost && /\.(?:webp|jpe?g|png)(?:$|[?#])/i.test(url.pathname);
  } catch {
    return false;
  }
};

for (const restaurant of restaurants) {
  if (!isGrabImage(restaurant.image)) {
    errors.push(`restaurant image không phải Grab CDN: ${restaurant.restaurantKey}`);
  }
}

for (const item of menuItems) {
  if (!isGrabImage(item.image)) {
    errors.push(`menu image không phải Grab CDN: ${item.restaurantKey}/${item.name}`);
  }
}

const uniqueRestaurantImages = new Set(restaurants.map((restaurant) => restaurant.image)).size;
const uniqueMenuImages = new Set(menuItems.map((item) => item.image)).size;
const expectedRestaurantUnique = Math.ceil(restaurants.length * minRestaurantUniqueRatio);
const expectedMenuUnique = Math.ceil(menuItems.length * minMenuUniqueRatio);

if (uniqueRestaurantImages < expectedRestaurantUnique) {
  errors.push(`độ đa dạng ảnh quán thấp: ${uniqueRestaurantImages}/${restaurants.length}, cần ít nhất ${expectedRestaurantUnique}`);
}
if (uniqueMenuImages < expectedMenuUnique) {
  errors.push(`độ đa dạng ảnh món thấp: ${uniqueMenuImages}/${menuItems.length}, cần ít nhất ${expectedMenuUnique}`);
}

console.log(JSON.stringify({
  catalog: catalogPath,
  restaurants: restaurants.length,
  menuItems: menuItems.length,
  uniqueRestaurantImages,
  uniqueMenuImages,
  minRestaurantUniqueRatio,
  minMenuUniqueRatio,
  errors: errors.length,
  status: errors.length ? 'FAILED' : 'PASS',
}, null, 2));

if (errors.length) {
  for (const error of errors.slice(0, 20)) console.error(`❌ ${error}`);
  if (errors.length > 20) console.error(`❌ ... và ${errors.length - 20} lỗi khác`);
  process.exit(1);
}

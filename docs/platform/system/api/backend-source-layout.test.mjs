import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { serviceSourceRoots, serviceForSource } from './backend-source-layout.mjs';

test('HTTP adapters remain discoverable with the same service identity after relocation', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'backend-layout-'));
  try {
    fs.mkdirSync(path.join(root, 'order-service/src/main/java'), { recursive: true });
    fs.mkdirSync(path.join(root, 'routing/boot/src/main/java'), { recursive: true });
    fs.mkdirSync(path.join(root, 'routing/infrastructure/src/main/java'), { recursive: true });
    fs.writeFileSync(path.join(root, 'routing/boot/pom.xml'),
      '<project><parent><artifactId>spring-boot-starter-parent</artifactId></parent>'
      + '<artifactId>routing-service</artifactId></project>');
    fs.mkdirSync(path.join(root, 'docs/reference/src/main/java'), { recursive: true });
    assert.deepEqual(serviceSourceRoots(root).map(({ service, directory }) =>
      [service, path.relative(root, directory)]).sort(), [
      ['order-service', 'order-service/src/main/java'],
      ['routing-service', 'routing/boot/src/main/java'],
      ['routing-service', 'routing/infrastructure/src/main/java'],
    ]);
    assert.equal(serviceForSource(root,
      path.join(root, 'routing/infrastructure/src/main/java/RoutingController.java')), 'routing-service');
    assert.equal(serviceForSource(root,
      path.join(root, 'order-service/src/main/java/OrderController.java')), 'order-service');
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('Settlement adapters retain settlement-service identity in the root layout', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'backend-layout-'));
  try {
    fs.mkdirSync(path.join(root, 'order-service/src/main/java'), { recursive: true });
    fs.mkdirSync(path.join(root, 'settlement/boot/src/main/java'), { recursive: true });
    fs.mkdirSync(path.join(root, 'settlement/infrastructure/src/main/java'), { recursive: true });
    fs.writeFileSync(path.join(root, 'settlement/boot/pom.xml'),
      '<project><parent><artifactId>spring-boot-starter-parent</artifactId></parent>'
      + '<artifactId>settlement-service</artifactId></project>');
    fs.mkdirSync(path.join(root, 'docs/reference/src/main/java'), { recursive: true });
    assert.deepEqual(serviceSourceRoots(root).map(({ service, directory }) =>
      [service, path.relative(root, directory)]).sort(), [
      ['order-service', 'order-service/src/main/java'],
      ['settlement-service', 'settlement/boot/src/main/java'],
      ['settlement-service', 'settlement/infrastructure/src/main/java'],
    ]);
    assert.equal(serviceForSource(root,
      path.join(root, 'settlement/infrastructure/src/main/java/SettlementController.java')), 'settlement-service');
    assert.equal(serviceForSource(root,
      path.join(root, 'order-service/src/main/java/OrderController.java')), 'order-service');
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('Delivery adapters retain delivery-service identity in the root layout', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'backend-layout-'));
  try {
    fs.mkdirSync(path.join(root, 'order-service/src/main/java'), { recursive: true });
    fs.mkdirSync(path.join(root, 'delivery/boot/src/main/java'), { recursive: true });
    fs.mkdirSync(path.join(root, 'delivery/infrastructure/src/main/java'), { recursive: true });
    fs.writeFileSync(path.join(root, 'delivery/boot/pom.xml'),
      '<project><parent><artifactId>spring-boot-starter-parent</artifactId></parent>'
      + '<artifactId>delivery-service</artifactId></project>');
    fs.mkdirSync(path.join(root, 'docs/reference/src/main/java'), { recursive: true });
    assert.deepEqual(serviceSourceRoots(root).map(({ service, directory }) =>
      [service, path.relative(root, directory)]).sort(), [
      ['delivery-service', 'delivery/boot/src/main/java'],
      ['delivery-service', 'delivery/infrastructure/src/main/java'],
      ['order-service', 'order-service/src/main/java'],
    ]);
    assert.equal(serviceForSource(root,
      path.join(root, 'delivery/infrastructure/src/main/java/DeliveryController.java')), 'delivery-service');
    assert.equal(serviceForSource(root,
      path.join(root, 'order-service/src/main/java/OrderController.java')), 'order-service');
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('Promotion adapters retain promotion-service identity in the root layout', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'backend-layout-'));
  try {
    fs.mkdirSync(path.join(root, 'order-service/src/main/java'), { recursive: true });
    fs.mkdirSync(path.join(root, 'promotion/boot/src/main/java'), { recursive: true });
    fs.mkdirSync(path.join(root, 'promotion/infrastructure/src/main/java'), { recursive: true });
    fs.writeFileSync(path.join(root, 'promotion/boot/pom.xml'),
      '<project><parent><artifactId>spring-boot-starter-parent</artifactId></parent>'
      + '<artifactId>promotion-service</artifactId></project>');
    fs.mkdirSync(path.join(root, 'docs/reference/src/main/java'), { recursive: true });
    assert.deepEqual(serviceSourceRoots(root).map(({ service, directory }) =>
      [service, path.relative(root, directory)]).sort(), [
      ['order-service', 'order-service/src/main/java'],
      ['promotion-service', 'promotion/boot/src/main/java'],
      ['promotion-service', 'promotion/infrastructure/src/main/java'],
    ]);
    assert.equal(serviceForSource(root,
      path.join(root, 'promotion/infrastructure/src/main/java/PromotionController.java')), 'promotion-service');
    assert.equal(serviceForSource(root,
      path.join(root, 'order-service/src/main/java/OrderController.java')), 'order-service');
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('a boot POM without an artifact identity fails instead of omitting handlers', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'backend-layout-'));
  try {
    fs.mkdirSync(path.join(root, 'routing/boot'), { recursive: true });
    fs.writeFileSync(path.join(root, 'routing/boot/pom.xml'), '<project/>');
    assert.throws(() => serviceSourceRoots(root), /artifactId/);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('Order relocation keeps its artifact identity and adapters discoverable', () => {
  const backend = path.resolve(import.meta.dirname, '../../../..');
  assert.equal(fs.existsSync(path.join(backend, 'order-service')), false);
  assert.deepEqual(serviceSourceRoots(backend)
    .filter(({ service }) => service === 'order-service')
    .map(({ directory }) => path.relative(backend, directory)).sort(), [
    'order/boot/src/main/java',
    'order/infrastructure/src/main/java',
  ]);
  assert.equal(serviceForSource(backend, path.join(backend,
    'order/infrastructure/src/main/java/com/delivery/order_service/controller/OrderController.java')),
  'order-service');
  const bootSources = fs.readdirSync(path.join(backend,
    'order/boot/src/main/java/com/delivery/order_service'));
  assert.deepEqual(bootSources, ['OrderServiceApplication.java']);
  assert.ok(fs.existsSync(path.join(backend,
    'order/infrastructure/src/main/resources/db/migration')));
  assert.equal(fs.existsSync(path.join(backend, 'order/boot/src/main/resources/db')), false);
});

test('Notification relocation preserves discovery of every HTTP handler', () => {
  const root = path.resolve(import.meta.dirname, '../../../..');
  assert.equal(fs.existsSync(path.join(root, 'notification-service')), false);
  const roots = serviceSourceRoots(root).filter(({ service }) => service === 'notification-service');
  assert.deepEqual(roots.map(({ directory }) => path.relative(root, directory)).sort(), [
    'notification/boot/src/main/java',
    'notification/infrastructure/src/main/java',
  ]);
  for (const controller of ['FirebaseController', 'NotificationController']) {
    const source = path.join(root,
      `notification/infrastructure/src/main/java/com/delivery/notification_service/controller/${controller}.java`);
    assert.equal(fs.existsSync(source), true);
    assert.equal(serviceForSource(root, source), 'notification-service');
  }
});


test('Analytics relocation keeps artifact identity, adapters and migrations discoverable', () => {
  const backend = path.resolve(import.meta.dirname, '../../../..');
  assert.equal(fs.existsSync(path.join(backend, 'analytics-service')), false);
  assert.deepEqual(serviceSourceRoots(backend)
    .filter(({ service }) => service === 'analytics-service')
    .map(({ directory }) => path.relative(backend, directory)).sort(), [
    'analytics/boot/src/main/java',
    'analytics/infrastructure/src/main/java',
  ]);
  assert.equal(serviceForSource(backend, path.join(backend,
    'analytics/infrastructure/src/main/java/com/delivery/analytics_service/controller/DashboardController.java')),
  'analytics-service');
  assert.deepEqual(fs.readdirSync(path.join(backend,
    'analytics/boot/src/main/java/com/delivery/analytics_service')), ['AnalyticsServiceApplication.java']);
  assert.deepEqual(fs.readdirSync(path.join(backend,
    'analytics/boot/src/main/resources')), ['application.properties']);
  assert.ok(fs.existsSync(path.join(backend,
    'analytics/infrastructure/src/main/resources/db/migration')));
});

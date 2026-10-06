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

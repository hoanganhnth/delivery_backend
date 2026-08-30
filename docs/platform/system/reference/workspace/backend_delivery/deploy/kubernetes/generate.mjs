#!/usr/bin/env node

/**
 * Generates the provider-neutral Kubernetes workload base from one audited
 * service inventory. Generated files are checked in so Kustomize can render
 * without Node; use --check in CI to prevent inventory/output drift.
 */
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const generatedDirectory = path.join(here, 'base', 'generated');
const write = process.argv.includes('--write');
const check = process.argv.includes('--check');

if ((write && check) || (!write && !check)) {
  console.error('Usage: node deploy/kubernetes/generate.mjs --write|--check');
  process.exit(2);
}

const standardResources = {
  requests: { cpu: '100m', memory: '256Mi' },
  limits: { cpu: '1000m', memory: '512Mi' },
};
const controlPlaneResources = {
  requests: { cpu: '100m', memory: '128Mi' },
  limits: { cpu: '500m', memory: '256Mi' },
};

const services = [
  { id: 'discovery-server', port: 8761, controlPlane: 'discovery' },
  { id: 'config-server', port: 8888, controlPlane: 'config' },
  { id: 'api-gateway', port: 8079 },
  { id: 'auth-service', port: 8081, database: 'auth_db', internal: true, jwt: true },
  { id: 'user-service', port: 8082, database: 'user_db', internal: true },
  { id: 'restaurant-service', port: 8083, database: 'restaurant_db', internal: true },
  { id: 'order-service', port: 8084, database: 'order_db', internal: true },
  { id: 'delivery-service', port: 8085, database: 'delivery_db', internal: true },
  { id: 'search-service', port: 8088 },
  { id: 'shipper-service', port: 8089, database: 'shipper_db', internal: true },
  { id: 'settlement-service', port: 8090, database: 'settlement_db', internal: true },
  { id: 'notification-service', port: 8091, database: 'notification_service_db', internal: true },
  { id: 'match-service', port: 8092, database: 'match_db', internal: true },
  { id: 'tracking-service', port: 8093, database: 'tracking_db', internal: true },
  { id: 'routing-service', port: 8094, internal: true },
  { id: 'livestream-service', port: 8094, database: 'livestream_db' },
  { id: 'saga-orchestrator-service', port: 8095, database: 'saga_db' },
  { id: 'promotion-service', port: 8096, database: 'promotion_db', internal: true },
  { id: 'analytics-service', port: 8097, database: 'analytics_db' },
  { id: 'flashsale-service', port: 8092, database: 'flashsale_db', internal: true },
  // Simulator control plane is an ADMIN-only private workload. Virtual
  // workers are scaled from this image by a separate approved overlay.
  { id: 'simulator-service', port: 8100, database: 'simulator_db', internal: true },
];

function labels(service) {
  const wave = service.controlPlane
    ? 'control'
    : service.id === 'auth-service'
      ? 'auth'
      : service.id === 'api-gateway'
        ? 'gateway'
        : 'resources';
  return {
    'app.kubernetes.io/name': service.id,
    'app.kubernetes.io/part-of': 'delivery-platform',
    'app.kubernetes.io/component': service.controlPlane ? 'control-plane' : 'application',
    'delivery.platform/template': 'provider-neutral',
    'delivery.platform/wave': wave,
  };
}

function metadata(service) {
  return { name: service.id, labels: labels(service) };
}

function secretSource(name, items, optional = false) {
  return { secret: { name, items, ...(optional ? { optional: true } : {}) } };
}

function runtimeSecretSources(service) {
  const sources = [];
  if (service.internal) {
    sources.push(secretSource('delivery-shared-internal', [
      { key: 'value', path: 'internal-secret' },
    ]));
  }
  if (service.database) {
    sources.push(secretSource(`delivery-${service.id}-db`, [
      { key: 'username', path: 'spring.datasource.username' },
      { key: 'password', path: 'spring.datasource.password' },
    ]));
  }
  if (service.jwt) {
    sources.push(secretSource('delivery-auth-jwt', [
      { key: 'private.pem', path: 'jwt-private.pem' },
      { key: 'public.pem', path: 'jwt-public.pem' },
    ]));
  }
  if (service.id === 'auth-service') {
    // This secret is intentionally optional while the rollout percentage is
    // zero. Partial percentage cohorts fail closed at Auth startup unless its
    // HMAC key has been delivered.
    sources.push(secretSource('delivery-auth-registration-canary', [
      { key: 'allowlist', path: 'registration-canary-allowlist' },
      { key: 'hash-key', path: 'registration-canary-hash-key' },
    ], true));
  }
  if (service.controlPlane === 'config') {
    sources.push(secretSource('delivery-config-repository', [
      { key: 'username', path: 'spring.cloud.config.server.git.username' },
      { key: 'password', path: 'spring.cloud.config.server.git.password' },
    ]));
  }
  return sources;
}

function appEnvironment(service) {
  if (service.controlPlane === 'config') {
    return [
      {
        name: 'CONFIG_SERVER_BACKEND',
        valueFrom: { configMapKeyRef: { name: 'delivery-control-plane', key: 'CONFIG_SERVER_BACKEND' } },
      },
      {
        name: 'CONFIG_REPOSITORY_GIT_URI',
        valueFrom: { configMapKeyRef: { name: 'delivery-control-plane', key: 'CONFIG_REPOSITORY_GIT_URI' } },
      },
      {
        name: 'CONFIG_REPOSITORY_DEFAULT_LABEL',
        valueFrom: { configMapKeyRef: { name: 'delivery-control-plane', key: 'CONFIG_REPOSITORY_DEFAULT_LABEL' } },
      },
      { name: 'SPRING_CONFIG_IMPORT', value: 'optional:configtree:/run/secrets/' },
      { name: 'MANAGEMENT_SERVER_PORT', value: '9090' },
      { name: 'JAVA_TOOL_OPTIONS', value: '-Xms64m -Xmx192m -Djava.io.tmpdir=/tmp' },
    ];
  }
  if (service.controlPlane === 'discovery') {
    return [
      { name: 'MANAGEMENT_SERVER_PORT', value: '9090' },
      { name: 'JAVA_TOOL_OPTIONS', value: '-Xms64m -Xmx192m -Djava.io.tmpdir=/tmp' },
    ];
  }

  const environment = [
    { name: 'MANAGEMENT_SERVER_PORT', value: '9090' },
    { name: 'JAVA_TOOL_OPTIONS', value: '-Xms128m -Xmx384m -Djava.io.tmpdir=/tmp' },
  ];
  if (service.database) {
    environment.push(
      {
        name: 'DATABASE_HOST',
        valueFrom: { configMapKeyRef: { name: 'delivery-data-plane', key: 'DATABASE_HOST' } },
      },
      {
        name: 'DATABASE_PORT',
        valueFrom: { configMapKeyRef: { name: 'delivery-data-plane', key: 'DATABASE_PORT' } },
      },
      {
        name: 'SPRING_DATASOURCE_URL',
        value: `jdbc:postgresql://$(DATABASE_HOST):$(DATABASE_PORT)/${service.database}`,
      },
    );
  }
  if (service.internal) {
    environment.push({ name: 'PLATFORM_SECRETS_INTERNAL_SECRET_REQUIRED', value: 'true' });
  }
  if (service.jwt) {
    environment.push(
      { name: 'JWT_PRIVATE_KEY_PATH', value: '/run/secrets/jwt-private.pem' },
      { name: 'JWT_PUBLIC_KEY_PATH', value: '/run/secrets/jwt-public.pem' },
    );
  }
  const identityMigrationKey = (applicationVariable, configMapKey) => ({
    name: applicationVariable,
    valueFrom: {
      configMapKeyRef: { name: 'delivery-runtime', key: configMapKey },
    },
  });
  if (service.id === 'auth-service') {
    environment.push(
      identityMigrationKey('IDENTITY_EVENTS_ENABLED', 'AUTH_IDENTITY_EVENTS_ENABLED'),
      identityMigrationKey('IDENTITY_OUTBOX_RELAY_ENABLED', 'AUTH_IDENTITY_OUTBOX_RELAY_ENABLED'),
      identityMigrationKey('IDENTITY_STATUS_BOOTSTRAP_ENABLED', 'AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED'),
      identityMigrationKey('PUBLIC_REGISTRATION_ENABLED', 'AUTH_PUBLIC_REGISTRATION_ENABLED'),
      identityMigrationKey('REGISTRATION_CANARY_PERCENTAGE', 'AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE'),
      identityMigrationKey('JWT_ACCESS_TOKEN_SUBJECT_MODE', 'AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE'),
    );
  }
  if (service.id === 'user-service') {
    environment.push(
      identityMigrationKey('IDENTITY_EVENTS_ENABLED', 'USER_IDENTITY_EVENTS_ENABLED'),
      identityMigrationKey('IDENTITY_OUTBOX_RELAY_ENABLED', 'USER_IDENTITY_OUTBOX_RELAY_ENABLED'),
    );
  }
  if (service.id === 'shipper-service') {
    environment.push(
      identityMigrationKey('IDENTITY_EVENTS_ENABLED', 'SHIPPER_IDENTITY_EVENTS_ENABLED'),
      identityMigrationKey('SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED', 'SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED'),
    );
  }
  if (service.id === 'delivery-service') {
    environment.push(identityMigrationKey(
      'SHIPPER_IDENTITY_PROJECTION_ENFORCED',
      'DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED',
    ));
  }
  if (service.id === 'tracking-service') {
    environment.push(identityMigrationKey(
      'SHIPPER_IDENTITY_PROJECTION_ENFORCED',
      'TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED',
    ));
  }
  if (service.id === 'restaurant-service') {
    environment.push(identityMigrationKey(
      'RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED',
      'RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED',
    ));
  }
  if (service.id === 'order-service') {
    environment.push(identityMigrationKey(
      'ORDER_PRINCIPAL_OWNERSHIP_ENFORCED',
      'ORDER_PRINCIPAL_OWNERSHIP_ENFORCED',
    ));
  }
  if (service.id === 'notification-service') {
    environment.push(identityMigrationKey(
      'NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED',
      'NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED',
    ));
  }
  if (service.id === 'settlement-service') {
    environment.push(identityMigrationKey(
      'SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED',
      'SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED',
    ));
  }
  if (service.id === 'promotion-service') {
    environment.push(identityMigrationKey(
      'PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED',
      'PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED',
    ));
  }
  if (service.id === 'flashsale-service') {
    environment.push(identityMigrationKey(
      'FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED',
      'FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED',
    ));
  }
  return environment;
}

function deployment(service) {
  const secretSources = runtimeSecretSources(service);
  const volumeMounts = [{ name: 'tmp', mountPath: '/tmp' }];
  const volumes = [{ name: 'tmp', emptyDir: { sizeLimit: '128Mi' } }];
  if (secretSources.length > 0) {
    volumeMounts.push({ name: 'runtime-secrets', mountPath: '/run/secrets', readOnly: true });
    volumes.push({
      name: 'runtime-secrets',
      projected: { defaultMode: 288, sources: secretSources },
    });
  }

  const container = {
    name: service.id,
    image: `registry.example.invalid/delivery/${service.id}:REPLACE_ME`,
    imagePullPolicy: 'IfNotPresent',
    ports: [
      { name: 'http', containerPort: service.port, protocol: 'TCP' },
      { name: 'management', containerPort: 9090, protocol: 'TCP' },
    ],
    env: appEnvironment(service),
    volumeMounts,
    resources: service.controlPlane ? controlPlaneResources : standardResources,
    securityContext: {
      allowPrivilegeEscalation: false,
      capabilities: { drop: ['ALL'] },
      readOnlyRootFilesystem: true,
      runAsNonRoot: true,
      runAsUser: 10001,
      runAsGroup: 10001,
    },
    startupProbe: {
      httpGet: { path: '/actuator/health/readiness', port: 'management' },
      periodSeconds: 5,
      timeoutSeconds: 3,
      failureThreshold: 36,
    },
    readinessProbe: {
      httpGet: { path: '/actuator/health/readiness', port: 'management' },
      periodSeconds: 5,
      timeoutSeconds: 3,
      failureThreshold: 3,
    },
    livenessProbe: {
      httpGet: { path: '/actuator/health/liveness', port: 'management' },
      periodSeconds: 15,
      timeoutSeconds: 3,
      failureThreshold: 3,
    },
  };
  if (!service.controlPlane) {
    container.envFrom = [{ configMapRef: { name: 'delivery-runtime' } }];
  }

  return {
    apiVersion: 'apps/v1',
    kind: 'Deployment',
    metadata: metadata(service),
    spec: {
      replicas: 1,
      revisionHistoryLimit: 5,
      strategy: {
        type: 'RollingUpdate',
        rollingUpdate: { maxUnavailable: 0, maxSurge: 1 },
      },
      selector: { matchLabels: { 'app.kubernetes.io/name': service.id } },
      template: {
        metadata: { labels: labels(service) },
        spec: {
          serviceAccountName: service.id,
          automountServiceAccountToken: false,
          terminationGracePeriodSeconds: service.controlPlane ? 30 : 90,
          securityContext: {
            fsGroup: 10001,
            fsGroupChangePolicy: 'OnRootMismatch',
            seccompProfile: { type: 'RuntimeDefault' },
          },
          containers: [container],
          volumes,
        },
      },
    },
  };
}

function serviceAccount(service) {
  return {
    apiVersion: 'v1',
    kind: 'ServiceAccount',
    metadata: metadata(service),
    automountServiceAccountToken: false,
  };
}

function service(serviceDefinition) {
  return {
    apiVersion: 'v1',
    kind: 'Service',
    metadata: metadata(serviceDefinition),
    spec: {
      type: 'ClusterIP',
      selector: { 'app.kubernetes.io/name': serviceDefinition.id },
      ports: [
        { name: 'http', port: serviceDefinition.port, targetPort: 'http', protocol: 'TCP' },
        { name: 'management', port: 9090, targetPort: 'management', protocol: 'TCP' },
      ],
    },
  };
}

function serialise(resource) {
  return `${JSON.stringify(resource, null, 2)}\n`;
}

const planned = new Map();
const resources = [];
for (const serviceDefinition of services) {
  const deploymentPath = `${serviceDefinition.id}.deployment.yaml`;
  const servicePath = `${serviceDefinition.id}.service.yaml`;
  const accountPath = `${serviceDefinition.id}.serviceaccount.yaml`;
  planned.set(deploymentPath, serialise(deployment(serviceDefinition)));
  planned.set(servicePath, serialise(service(serviceDefinition)));
  planned.set(accountPath, serialise(serviceAccount(serviceDefinition)));
  resources.push(deploymentPath, servicePath, accountPath);
}
planned.set('kustomization.yaml', [
  'apiVersion: kustomize.config.k8s.io/v1beta1',
  'kind: Kustomization',
  'resources:',
  ...resources.map((resource) => `  - ${resource}`),
  '',
].join('\n'));

let failures = 0;
for (const [relativePath, content] of planned) {
  const destination = path.join(generatedDirectory, relativePath);
  if (write) {
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.writeFileSync(destination, content);
  } else if (!fs.existsSync(destination) || fs.readFileSync(destination, 'utf8') !== content) {
    console.error(`generated manifest is stale or missing: deploy/kubernetes/base/generated/${relativePath}`);
    failures += 1;
  }
}

if (check && failures > 0) process.exit(1);
if (write) console.log(`Wrote ${planned.size} generated Kubernetes manifest files.`);
if (check) console.log(`PASS: ${planned.size} generated Kubernetes manifest files are current.`);

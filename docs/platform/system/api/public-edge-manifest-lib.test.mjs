import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { parseGatewayRouteSource } from './public-edge-manifest-lib.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const backend = path.resolve(here, '../../../..');

function wrapRoute(routeBody, extraConstructorParameter = '') {
  return `
    public class GatewayRouteConfig {
      public GatewayRouteConfig(
        @Value("\${app.widget-service.uri:lb://widget-service}") String widgetServiceUri
        ${extraConstructorParameter}
      ) { }

      public RouteLocator routes(RouteLocatorBuilder builder) {
        var routes = builder.routes();
        ${routeBody}
        return routes.build();
      }
    }
  `;
}

test('extracts route ID, paths, methods and destination metadata', () => {
  const source = wrapRoute(`
    routes.route("widget-read", r -> r.path("/api/widgets", "/api/widgets/{id:[0-9]+}")
      .and().method(HttpMethod.GET, HttpMethod.HEAD)
      .uri(widgetServiceUri));
  `);

  const manifest = parseGatewayRouteSource(source, { file: 'GatewayRouteConfig.java' });

  assert.equal(manifest.counts.routes, 1);
  assert.equal(manifest.counts.pathPatterns, 2);
  assert.deepEqual(manifest.routes[0], {
    id: 'widget-read',
    paths: ['/api/widgets', '/api/widgets/{id:[0-9]+}'],
    methods: ['GET', 'HEAD'],
    downstream: {
      service: 'widget-service',
      uriVariable: 'widgetServiceUri',
      property: 'app.widget-service.uri',
      defaultUri: 'lb://widget-service',
    },
    featureGate: { mode: 'always' },
    rateLimit: {
      classification: 'request-dependent',
      source: 'api-gateway/src/main/java/com/delivery/api_gateway/ratelimit/GatewayRateLimitFilter.java',
    },
    source: { file: 'GatewayRouteConfig.java', line: 11 },
  });
});

test('records methodless routes as ANY and preserves rewrite filters', () => {
  const source = wrapRoute(`
    routes.route("widget-proxy", r -> r.path("/api/admin/widgets/**")
      .filters(f -> f.rewritePath("/api/admin/widgets(?<segment>/?.*)", "/api/widgets\${segment}"))
      .uri(widgetServiceUri));
  `);

  const route = parseGatewayRouteSource(source, { file: 'GatewayRouteConfig.java' }).routes[0];

  assert.deepEqual(route.methods, ['ANY']);
  assert.deepEqual(route.rewrite, {
    from: '/api/admin/widgets(?<segment>/?.*)',
    to: '/api/widgets${segment}',
  });
});

test('associates routes inside a boolean block with its property gate', () => {
  const source = wrapRoute(`
    if (widgetClientApiEnabled) {
      routes.route("widget-write", r -> r.path("/api/widgets")
        .and().method(HttpMethod.POST)
        .uri(widgetServiceUri));
    }
  `, ', @Value("\${app.widget.client-api-enabled:false}") boolean widgetClientApiEnabled');

  const route = parseGatewayRouteSource(source, { file: 'GatewayRouteConfig.java' }).routes[0];

  assert.deepEqual(route.featureGate, {
    mode: 'property',
    variable: 'widgetClientApiEnabled',
    property: 'app.widget.client-api-enabled',
    default: false,
  });
});

test('fails closed for duplicate route IDs and unsupported route-chain methods', () => {
  const duplicate = wrapRoute(`
    routes.route("same", r -> r.path("/one").uri(widgetServiceUri));
    routes.route("same", r -> r.path("/two").uri(widgetServiceUri));
  `);
  const unsupported = wrapRoute(`
    routes.route("header-route", r -> r.path("/one")
      .and().header("X-Test", "yes")
      .uri(widgetServiceUri));
  `);

  assert.throws(() => parseGatewayRouteSource(duplicate), /duplicate route ID 'same'/);
  assert.throws(() => parseGatewayRouteSource(unsupported), /unsupported route-chain method 'header'/);
});

test('extracts the complete production Gateway route surface', () => {
  const gatewayFile = path.join(
    backend,
    'api-gateway/src/main/java/com/delivery/api_gateway/config/GatewayRouteConfig.java',
  );
  const manifest = parseGatewayRouteSource(fs.readFileSync(gatewayFile, 'utf8'), {
    file: 'api-gateway/src/main/java/com/delivery/api_gateway/config/GatewayRouteConfig.java',
  });

  assert.deepEqual(manifest.counts, {
    routes: 93,
    pathPatterns: 157,
    byGate: {
      always: 81,
      'app.livestream.client-api-enabled': 10,
      'app.payment.client-api-enabled': 2,
    },
  });
  assert.deepEqual(manifest.routes.find(({ id }) => id === 'web-bff-service').methods, ['ANY']);
  assert.deepEqual(manifest.routes.find(({ id }) => id === 'tracking-service-ws').methods, ['ANY']);
  assert.equal(
    manifest.routes.find(({ id }) => id === 'settlement-service-customer-payment-create')
      .featureGate.property,
    'app.payment.client-api-enabled',
  );
  assert.deepEqual(
    manifest.routes.find(({ id }) => id === 'simulator-admin-control').rewrite,
    {
      from: '/api/admin/simulations(?<segment>/?.*)',
      to: '/api/simulator${segment}',
    },
  );
});

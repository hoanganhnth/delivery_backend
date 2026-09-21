import assert from 'node:assert/strict';
import test from 'node:test';

import {
  compareInventoryToSource,
  extractMappedHandlersFromJava,
  parseHttpInventory,
} from './http-api-inventory-lib.mjs';

test('discovers composed mappings and method-level RequestMapping but excludes class mappings', () => {
  const source = `
    @RestController
    @RequestMapping("/api/widgets")
    public class WidgetController {
      @GetMapping("/{id}")
      public ResponseEntity<String> get(@PathVariable Long id) {
        return ResponseEntity.ok("ok");
      }

      @RequestMapping("/proxy/**")
      public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        return ResponseEntity.ok(new byte[0]);
      }

      @RequestMapping(value = "/callback", method = {RequestMethod.GET, RequestMethod.POST})
      public ResponseEntity<Void> callback() {
        return ResponseEntity.ok().build();
      }
    }
  `;

  const handlers = extractMappedHandlersFromJava(source, {
    service: 'widget-service',
    controller: 'WidgetController',
    file: 'widget-service/src/main/java/example/WidgetController.java',
  });

  assert.deepEqual(handlers.map(({ handler, annotation }) => ({ handler, annotation })), [
    { handler: 'get', annotation: 'GetMapping' },
    { handler: 'proxy', annotation: 'RequestMapping' },
    { handler: 'callback', annotation: 'RequestMapping' },
  ]);
});

test('ignores mapping text in comments and string literals', () => {
  const source = `
    public class WidgetController {
      // @GetMapping("/not-real")
      private String example = "@PostMapping";

      @DeleteMapping("/{id}")
      public void delete(Long id) { }
    }
  `;

  const handlers = extractMappedHandlersFromJava(source, {
    service: 'widget-service',
    controller: 'WidgetController',
    file: 'WidgetController.java',
  });

  assert.deepEqual(handlers.map(({ handler }) => handler), ['delete']);
});

test('rejects malformed handler cells instead of treating prose as a Java method name', () => {
  const markdown = `
## Exact method inventory

| Service | Controller | Verb | Path | Handler |
|---|---|---|---|---|
| widget-service | WidgetController | GET | \`/api/widgets\` | \`get\` (public read) |
  `;

  assert.throws(
    () => parseHttpInventory(markdown),
    /invalid handler 'get \(public read\)'/,
  );
});

test('rejects duplicate inventory handler keys', () => {
  const markdown = `
## Exact method inventory

| Service | Controller | Verb | Path | Handler |
|---|---|---|---|---|
| widget-service | WidgetController | GET | \`/api/widgets\` | \`get\` |
| widget-service | WidgetController | GET | \`/api/widgets/page\` | \`get\` |
  `;

  assert.throws(() => parseHttpInventory(markdown), /duplicate handler key/);
});

test('reports source mappings missing from inventory and stale inventory handlers', () => {
  const inventory = [
    { service: 'widget-service', controller: 'WidgetController', handler: 'stale' },
  ];
  const source = [
    { service: 'widget-service', controller: 'WidgetController', handler: 'current' },
  ];

  assert.deepEqual(compareInventoryToSource(inventory, source), {
    missingFromInventory: ['widget-service|WidgetController|current'],
    missingFromSource: ['widget-service|WidgetController|stale'],
  });
});

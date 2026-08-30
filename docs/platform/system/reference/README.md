# Reference Source Mirror

This generated mirror preserves 559 first-party documentation, runtime configuration, migration, deployment, monitoring and client-manifest files under workspace/ using their original workspace paths. Relative links among copied documentation therefore continue to resolve wherever their targets are also mirrored.

The editable/canonical reconstruction guides are one directory above this mirror. The owning repositories' code, tests and runtime configuration remain authoritative. Java/Dart/TypeScript source is not duplicated here; use the canonical source map to follow code references back to its owner.

Regenerate after documentation/configuration changes:

```bash
node docs/system/rebuild/sync-reference-bundle.mjs --write
node docs/system/rebuild/sync-reference-bundle.mjs --check
```

MANIFEST.json records every source path, SHA-256 checksum and byte count. No secret directories, vendor dependency docs, node modules, Pods, build output or .git content is included.

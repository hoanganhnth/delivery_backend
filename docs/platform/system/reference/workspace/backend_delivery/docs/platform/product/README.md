# Product Docs

This directory contains current consumer-product behavior derived from real
accepted intent. Harness deliberately ships no fake product domains.

When a user provides a product specification, derive smaller living documents
here instead of keeping one growing specification as the operating manual. Name
files after actual product domains, such as `overview.md`, `billing.md`,
`permissions.md`, or `api-conventions.md`.

## Current Product Contract

The platform product corpus is maintained by the Delivery project in this
directory:

- [overview.md](./overview.md) — product boundary, client map and capability
  status.
- [use-cases.md](./use-cases.md) — source-derived inventory of actor goals,
  system automation, failure branches and public/gated/experimental boundaries.
- [features/](./features/README.md) — detailed domain behavior and rollout
  notes.

The generic repository-harness contract still lives in the root README, current
workflow and architecture documents, lasting decisions, optional orchestration
contract, implementation, and executable tests.

## Update Rule

When behavior changes:

1. Update the affected product document when the expected behavior changed.
2. Update the active execution plan when complex work uses one.
3. Add a lasting decision only when future work must inherit a consequential
   product, architecture, data, security, compatibility, or validation choice.
4. Add or update executable proof that exercises the behavior.

Bounded changes do not require a story packet, proof-matrix row, or Harness CLI
mutation.

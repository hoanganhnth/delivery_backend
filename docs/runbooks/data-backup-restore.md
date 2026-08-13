# Data Backup And Restore Runbook

## Safety boundary

Never rehearse against production. `restore-data-plane-isolated.sh` accepts only
database prefixes matching `phase4_restore_*_` and requires the explicit
`DROP_ISOLATED_DATABASES` confirmation. The automated rehearsal creates and
deletes a disposable container whose name starts with
`phase4-restore-rehearsal`.

Restore is appropriate after confirmed database loss/corruption, an unrecoverable
schema rollout, or a regional data-plane loss. It is not a normal response to a
consumer lag, stale Redis location, a poison Kafka record, or a single failed
request.

## Ownership and recovery targets

| Data class | Service databases | MVP RPO / RTO | Production RPO / RTO |
|---|---|---:|---:|
| Critical lifecycle/finance | `order_db`, `delivery_db`, `match_db`, `settlement_db` and their outboxes/receipts | 4h / 2h | 5m / 60m |
| Durable identity/product/support state | `auth_db`, `user_db`, `restaurant_db`, `shipper_db`, `notification_service_db`, `tracking_db` (sampled support history only), `livestream_db`, `saga_db`, `promotion_db`, `analytics_db`, `flashsale_db` | 24h / 4h | 15m / 2h |
| Search projection | Elasticsearch indices | rebuild from durable producers; no independent RPO | rebuild within 2h |
| Match/tracking realtime | Redis GEO, online/freshness, offers, publisher leases/fences | reconnect/rebuild; never restore stale state | reconnect/rebuild; never restore stale state |

The PostgreSQL list is encoded in `scripts/backup-data-plane.sh`. `match_db`
is a protected Match command/result store; `search_db` remains a legacy/unused
init entry and is not claimed as a protected store.

## Backup policy

- MVP: encrypted daily backups retained 14 days and weekly backups retained
  eight weeks.
- Production: continuous/PITR WAL coverage and daily recovery points retained
  35 days; monthly logical recovery points retained 12 months.
- This is disaster-recovery retention, not statutory retention for financial or
  personal records.
- PostgreSQL backups use custom-format logical dumps without ownership/ACLs.
  Production PITR additionally requires managed physical base backups and WAL
  archiving to encrypted object storage.
- The logical bundle is encrypted with AES-256 using PBKDF2 (600,000 rounds).
  Supply the passphrase as a mode-0600 secret file from a secrets manager; never
  commit it or put it in a command-line argument.
- An outer SHA-256 sidecar detects transfer/storage corruption before decrypt;
  inner SHA-256 checks cover the manifest, every dump and Kafka metadata file.
- Kafka metadata capture contains topic descriptions/configuration, ACL output
  and observed consumer offsets. It is recovery evidence, not a command to
  blindly overwrite live offsets.
- Production backup fails if ACL export fails. Only the disposable rehearsal
  may set `ALLOW_MISSING_KAFKA_ACLS=true`, because its broker deliberately has
  no authorizer and writes that fact into the encrypted manifest payload.
- Run `cleanup-data-plane-backups.sh` from a retention job. It defaults to dry
  run; set `DRY_RUN=false` only for the approved backup prefix. Object storage
  lifecycle policies should enforce the same periods independently.

Example operator backup:

```bash
BACKUP_OUTPUT_DIR=/secure/backup/delivery \
BACKUP_ENCRYPTION_PASSPHRASE_FILE=/run/secrets/delivery-backup-passphrase \
BACKUP_TIER=daily \
PGHOST=postgres-primary PGUSER=backup_operator \
KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092 \
scripts/backup-data-plane.sh
```

## Restore procedure

1. Declare an incident owner and recovery timestamp. Stop external ingress,
   service writers, outbox relays and consumers for the affected environment.
2. Select one consistent recovery point for order, delivery, Match, settlement
   and saga/outbox databases. Do not mix a newer settlement ledger or Match
   result outbox with older order or delivery state.
3. Verify the encrypted artifact SHA-256 sidecar and decrypt/verify all inner
   checksums. Failure means the artifact is unusable; choose another recovery
   point.
4. Restore into isolated databases first:

   ```bash
   BACKUP_FILE=/secure/backup/delivery/delivery-data-daily-....tar.gz.enc \
   BACKUP_ENCRYPTION_PASSPHRASE_FILE=/run/secrets/delivery-backup-passphrase \
   RESTORE_PREFIX=phase4_restore_incident123_ \
   RESTORE_CONFIRMATION=DROP_ISOLATED_DATABASES \
   PGHOST=isolated-postgres PGUSER=restore_operator \
   scripts/restore-data-plane-isolated.sh
   ```

5. Run `verify-restored-critical-data.sh` against both the source snapshot (when
   readable) and restored prefix. Compare fingerprints and counts for orders,
   deliveries, Match command/result rows, settlement receipts/ledger, all
   lifecycle outboxes and notification projection. Run service migration and
   smoke tests against the isolated copy.
6. Rebuild Elasticsearch/search projections from durable source events or a
   controlled export. Require projection checkpoints to reach the selected
   recovery point before opening reads.
7. Redis is flushed/recreated. Shippers reconnect and republish; do not import
   GEO, online sets, freshness keys, assignment offers, generation leases or
   offline deadlines from a backup.
8. Promote/switch only after reconciliation, security approval and measured RTO
   are recorded. Keep the failed store read-only until the incident closes.

## Avoiding duplicate events

- Keep original outbox `event_id`, aggregate identity, status and payload. Never
  copy business rows while generating new event IDs.
- Restore mutually dependent databases to a common recovery point before any
  relay or consumer resumes. Resume outbox relays before accepting new writes.
- Do not automatically reset Kafka consumer groups from the metadata snapshot.
  First compare the restored receipts/deduplication rows and outbox statuses with
  broker offsets. A database restored behind an offset needs controlled replay;
  a database ahead of an offset will naturally see duplicates.
- Settlement replay is safe only because `settlement_receipts.event_id`, the
  order guard and ledger business-key constraints remain restored. Match,
  Notification, Saga and history consumers likewise require their durable
  receipt/unique keys.
- Rehearse replay with a new isolated consumer-group ID. For production, reset a
  group only while all its consumers are stopped, retain the audit export, and
  advance in small ranges while checking DLT/duplicate metrics.
- Offline tombstones and location events are not replayed into restored Redis.
  A fresh authenticated publisher generation establishes the new realtime
  truth.

## Rehearsal

`scripts/verify-backup-restore-rehearsal.sh` creates representative order,
delivery, settlement ledger/receipt, outbox and notification projection data;
backs up every service database; proves a tampered artifact is rejected; drops
the disposable source databases; restores to isolated names; compares canonical
fingerprints and uniqueness invariants; and runs a smoke query. It reports the
measured restore duration and fails if the MVP critical RTO is exceeded.

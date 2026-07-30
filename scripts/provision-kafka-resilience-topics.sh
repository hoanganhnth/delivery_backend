#!/usr/bin/env bash
set -euo pipefail

# Production operator helper. Run from a Kafka admin toolbox/container that has
# kafka-topics.sh and Kafka ACL authority; it deliberately does not rely on
# broker auto-topic-creation. Source topics must already be provisioned with
# their canonical partition counts before this script is used.

: "${KAFKA_BOOTSTRAP_SERVERS:?Set KAFKA_BOOTSTRAP_SERVERS (for example kafka-1:9092)}"

KAFKA_TOPICS_BIN="${KAFKA_TOPICS_BIN:-kafka-topics.sh}"
KAFKA_CONFIGS_BIN="${KAFKA_CONFIGS_BIN:-kafka-configs.sh}"
DLT_RETENTION_MS="${DLT_RETENTION_MS:-1209600000}" # 14 days
DLT_PARTITIONS="${DLT_PARTITIONS:-1}"
DLT_REPLICATION_FACTOR="${DLT_REPLICATION_FACTOR:-3}"

topics=(
  order.created.DLT
  restaurant.order-confirmed.DLT
  restaurant.order-rejected.DLT
  saga.command.update-order-status.DLT
  saga.command.create-delivery.DLT
  saga.command.cancel-delivery.DLT
  saga.command.cache-shipper-found.DLT
  saga.command.expire-shipper-offer.DLT
  saga.command.mark-shipper-not-found.DLT
  delivery.created.result.DLT
  delivery.created.failed.DLT
  delivery.status-updated.DLT
  delivery.shipper-offered.DLT
  delivery.completed.DLT
  order.created-retry-1000
  order.created-retry-2000
  order.created-retry-4000
  delivery.status-updated-retry-1000
  delivery.status-updated-retry-2000
  delivery.status-updated-retry-4000
  delivery.shipper-offered-retry-1000
  delivery.shipper-offered-retry-2000
  delivery.shipper-offered-retry-4000
  order.cancelled-retry-1000
  order.cancelled-retry-2000
  order.cancelled-retry-4000
  restaurant.order-confirmed-retry-1000
  restaurant.order-confirmed-retry-2000
  restaurant.order-confirmed-retry-4000
  delivery.created.result-retry-1000
  delivery.created.result-retry-2000
  delivery.created.result-retry-4000
  delivery.shipper-accepted-retry-1000
  delivery.shipper-accepted-retry-2000
  delivery.shipper-accepted-retry-4000
  delivery.shipper-rejected-retry-1000
  delivery.shipper-rejected-retry-2000
  delivery.shipper-rejected-retry-4000
  shipper.found-retry-1000
  shipper.found-retry-2000
  shipper.found-retry-4000
  shipper.not-found-retry-1000
  shipper.not-found-retry-2000
  shipper.not-found-retry-4000
  delivery.created.failed-retry-1000
  delivery.created.failed-retry-2000
  delivery.created.failed-retry-4000
  delivery.cancel.failed-retry-1000
  delivery.cancel.failed-retry-2000
  delivery.cancel.failed-retry-4000
  restaurant.order-rejected-retry-1000
  restaurant.order-rejected-retry-2000
  restaurant.order-rejected-retry-4000
  saga.command.update-order-status-retry-1000
  saga.command.update-order-status-retry-2000
  saga.command.update-order-status-retry-4000
  saga.command.create-delivery-retry-1000
  saga.command.create-delivery-retry-2000
  saga.command.create-delivery-retry-4000
  saga.command.cancel-delivery-retry-1000
  saga.command.cancel-delivery-retry-2000
  saga.command.cancel-delivery-retry-4000
  saga.command.cache-shipper-found-retry-1000
  saga.command.cache-shipper-found-retry-2000
  saga.command.cache-shipper-found-retry-4000
  saga.command.expire-shipper-offer-retry-1000
  saga.command.expire-shipper-offer-retry-2000
  saga.command.expire-shipper-offer-retry-4000
  saga.command.mark-shipper-not-found-retry-1000
  saga.command.mark-shipper-not-found-retry-2000
  saga.command.mark-shipper-not-found-retry-4000
  delivery.completed-retry-1000
  delivery.completed-retry-2000
  delivery.completed-retry-4000
)

for topic in "${topics[@]}"; do
  "$KAFKA_TOPICS_BIN" \
    --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$DLT_PARTITIONS" \
    --replication-factor "$DLT_REPLICATION_FACTOR" \
    --config "retention.ms=$DLT_RETENTION_MS" \
    --config cleanup.policy=delete
  # --create --if-not-exists does not reconcile an already auto-created topic.
  # Enforce retention on every run so a development/provisioning race cannot
  # silently leave a DLT with the broker's default retention.
  "$KAFKA_CONFIGS_BIN" \
    --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
    --entity-type topics \
    --entity-name "$topic" \
    --alter \
    --add-config "retention.ms=$DLT_RETENTION_MS,cleanup.policy=delete"
done

printf 'Provisioned core DLT topics with retention.ms=%s.\n' "$DLT_RETENTION_MS"

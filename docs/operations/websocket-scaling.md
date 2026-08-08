# Raw WebSocket Scaling

## Current design

The external contract remains `/ws/shipper-locations` with the existing
`subscribe_shipper`, `unsubscribe_shipper`, `update_location`, `ping`, and
`location_update` messages. Gateway forwards the handshake unchanged; Tracking
validates the Bearer token through Auth JWKS during the handshake and every
subscription still requires the fail-closed Delivery participant check.

Internally, authorized subscriptions are indexed by `deliveryId` room with a
reverse session index. The previous shipper-only map could keep a customer from
an old delivery subscribed when that shipper started a new delivery, and clean
disconnect scanned every shipper subscription set. A newer BUSY assignment
evicts the prior room for that shipper; only the matching AVAILABLE event closes
the current generation room.

`shipper.status-change` maintains a shared active-delivery projection in Redis.
Each Tracking instance consumes assignment events with an instance-specific
room group so local rooms close on the same lifecycle event. Location fan-out is
published through Redis Pub/Sub with an exact `deliveryId`; every node sends the
unchanged socket payload only to its local authorized members of that room.

## Backpressure and final-state recovery

Publisher work never waits for a slow socket. A bounded 4-worker executor
(configurable, maximum 32) queues at most 1,024 drain tasks by default. Each
session/delivery retains at most two pending states: updates with the same online
state coalesce to the latest point, while the latest offline/online transition
and newest location are retained. Control messages and async location sends are
serialized per session.

Redis GEO/detail remains the realtime source. Immediately after an authorized
subscribe/reconnect, Tracking sends the cached last location. Therefore Pub/Sub
loss, executor rejection, or coalescing can drop intermediate samples without
losing the final location needed for tracking. Publisher generation fencing,
disconnect grace, offline tombstones, and Match stale-event fencing are
unchanged.

## Evidence

- `WebSocketFanoutBenchmarkTest` compares 50,000 legacy subscription sets with
  indexed delivery rooms and writes
  `tracking-service/target/phase4-websocket-fanout/summary.tsv`. The observed run
  reduced disconnect lookup from 7,230,458 ns to 62,666 ns and reduced a reused
  shipper's audience from two deliveries to exactly one current room.
- `DeliveryRoomRegistryTest` proves 10,000 unrelated rooms do not affect target
  lookup, stale AVAILABLE cannot close a newer room, and old audiences are
  evicted.
- `LocationMessageDispatcherTest` offers 102 updates to a blocked subscriber and
  sends two: the offline transition and newest online location; 100 intermediate
  updates are coalesced.
- Existing authorization tests reject non-participant/area subscription and
  non-shipper publishing. Reconnect tests retain Redis publisher generation,
  supersession and tombstone behavior; the reconnect location test proves final
  Redis state recovery.

### Task 7: Backend-owned support chat

- Add the authenticated Gateway chat contract, persistence/realtime boundary, Flutter customer UI, and minimum `delivery_web` operator surface. Do not connect either client directly to Firebase chat.

## Binding authority and safety decisions

- The current repository has no support-chat backend contract; the deleted Flutter support implementation used Firebase/Firestore and is not an authority. Add a canonical backend-owned support service/schema with authenticated customer ownership and operator authorization. Route only exact Gateway paths; keep service ports and Firebase direct access out of both clients.
- Use bounded REST conversation/message operations plus a server-owned realtime/poll boundary (for example, cursor-based message refresh or an authenticated raw WebSocket) with explicit conversation IDs, message IDs, timestamps, sender role, and status. Avoid STOMP/SockJS and do not expose arbitrary user/conversation IDs without an ownership check.
- Persist messages and conversation state transactionally, enforce bounded content/attachments and idempotent client message keys, and make duplicate send/reconnect/replay safe. Customer can create/list/read/send/close only its own conversation; web operator can list/claim/read/reply/close only through role-enforced endpoints.
- Flutter should have data/domain/application/presentation layers with loading, empty, retryable error, send-pending/failed, closed, and reconnect states. Web should add only the minimum operator queue/detail/reply surface with observable failures. Do not retain or read Firebase Auth/Firestore as a fallback or cache of server truth.
- Add explicit `SUPPORT_CHAT_API_ENABLED=false` and realtime/relay flags default-off, with no production activation claim until persistence/security/replay proof is recorded.

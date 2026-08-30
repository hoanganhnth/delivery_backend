### Task 5: Livestream viewer and host controls

- Restore a Flutter customer viewer using the backend/Gateway contract and the existing realtime/media boundary, with lifecycle and failure handling.
- Add only the minimum `delivery_web` host/operator controls needed to create, start, stop, and inspect a stream; no customer-app admin navigation.

## Binding authority and safety decisions

- The backend authority is `livestream-service`: the existing HTTP controllers and DTOs use UUID livestream IDs, `GET /api/livestreams/active`, `GET /api/livestreams/{id}`, `POST /api/livestreams/{id}/join`, `GET /api/livestreams/{id}/products`, and host `POST /api/livestreams`, `/{id}/start`, `/{id}/end`; join returns the server-created Agora `token`, `channelName`, expiry, and metadata. Read the actual controller/DTO/service before implementation.
- Gateway and service are currently disabled by default (`LIVESTREAM_API_ENABLED=false` and no livestream route). Add an independent `LIVESTREAM_CLIENT_API_ENABLED=false` edge gate and keep both staging/prod defaults off. Do not expose wildcard routes, direct service ports, STOMP/SockJS, Firebase chat, or caller-controlled channel/token values.
- The existing backend create path does not prove restaurant ownership and the token path accepts a caller-supplied role. Do not silently open those gaps: either add minimal server-side ownership/role checks before enabling host routes, or keep the affected host/token operation fail-closed and mark the capability gated. Viewer UI must never manufacture a token or pretend a media session is connected.
- Flutter must use the authenticated Gateway Dio, strict UUID/enum/response parsing, a lifecycle-safe media port (join/leave/dispose, explicit unavailable/error state), and no network or SDK initialization in pure tests. If the Agora native SDK is not configured, show a recoverable “media unavailable” state rather than fake video.
- `delivery_web` may add only host/operator controls and their API adapter/tests. Do not restore the deleted customer-app Admin placeholder or add customer/admin role mixing; web actions must surface loading, success, and failure/retry states.

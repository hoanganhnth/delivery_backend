# Review package: c24832b..35b2448

## Commits
35b2448 feat(livestream): add restaurant host controls

## Files changed
 src/App.tsx                                        |   2 +
 src/app/dependencies.tsx                           |   4 +
 src/components/navigation/portalNavigation.ts      |   1 +
 .../__tests__/restaurant-livestream.test.tsx       |  94 +++++++++++++
 .../restaurant/pages/RestaurantLivestreamPage.tsx  | 152 +++++++++++++++++++++
 .../api/__tests__/livestreamService.test.ts        |  58 ++++++++
 src/services/api/endpoints.ts                      |   7 +
 src/services/api/livestreamService.ts              |  24 ++++
 src/utils/constants.ts                             |   1 +
 9 files changed, 343 insertions(+)

## Diff
diff --git a/src/App.tsx b/src/App.tsx
index dc42707..574cdf6 100644
--- a/src/App.tsx
+++ b/src/App.tsx
@@ -12,20 +12,21 @@ import {
 } from './app/dependencies';
 
 const LoginPage = lazy(() => import('./modules/auth/pages/LoginPageV2'));
 const AdminLoginPage = lazy(() => import('./modules/auth/pages/AdminLoginPage'));
 const RestaurantDashboard = lazy(() => import('./modules/restaurant/pages/RestaurantDashboard'));
 const RestaurantProfilePage = lazy(() => import('./modules/restaurant/pages/RestaurantProfilePage'));
 const RestaurantOrders = lazy(() => import('./modules/restaurant/pages/RestaurantOrders'));
 const MenuManagement = lazy(() => import('./modules/restaurant/pages/MenuManagement'));
 const RestaurantReviewsPage = lazy(() => import('./modules/restaurant/pages/RestaurantReviewsPage'));
 const RestaurantVouchersPage = lazy(() => import('./modules/restaurant/pages/RestaurantVouchersPage'));
+const RestaurantLivestreamPage = lazy(() => import('./modules/restaurant/pages/RestaurantLivestreamPage'));
 const AdminDashboard = lazy(() => import('./modules/admin/pages/AdminDashboard'));
 const AdminOrdersPage = lazy(() => import('./modules/admin/pages/AdminOrdersPage'));
 const AdminShippersPage = lazy(() => import('./modules/admin/pages/AdminShippersPage'));
 const AdminRatingsPage = lazy(() => import('./modules/admin/pages/AdminRatingsPage'));
 const AdminCouponsPage = lazy(() => import('./modules/admin/pages/AdminCouponsPage'));
 const AdminFlashSalePage = lazy(() => import('./modules/admin/pages/AdminFlashSalePage'));
 const CatalogImportPage = lazy(() => import('./modules/restaurant/pages/CatalogImportPage'));
 const UnauthorizedPage = lazy(() => import('./pages/UnauthorizedPage'));
 const SystemHandbookPage = lazy(() => import('./modules/system-handbook/pages/SystemHandbookPage'));
 const CustomerHomePage = lazy(() => import('./modules/customer/pages/CustomerHomePage'));
@@ -85,20 +86,21 @@ function PortalRoutes() {
                 <Route path={ROUTES.CUSTOMER_CHECKOUT} element={customerRoute(<CustomerCheckoutPage />)} />
                 <Route path={ROUTES.CUSTOMER_ADDRESSES} element={customerRoute(<CustomerAddressesPage />)} />
                 <Route path={ROUTES.CUSTOMER_ORDERS} element={customerRoute(<CustomerOrdersPage />)} />
                 <Route path="/customer/orders/:orderId" element={customerRoute(<CustomerOrderDetailPage />)} />
                 <Route path={ROUTES.RESTAURANT_DASHBOARD} element={restaurantRoute(<RestaurantDashboard />)} />
                 <Route path={ROUTES.RESTAURANT_PROFILE} element={restaurantRoute(<RestaurantProfilePage />)} />
                 <Route path={ROUTES.RESTAURANT_MENU} element={restaurantRoute(<MenuManagement />)} />
                 <Route path={ROUTES.RESTAURANT_ORDERS} element={restaurantRoute(<RestaurantOrders />)} />
                 <Route path={ROUTES.RESTAURANT_REVIEWS} element={restaurantRoute(<RestaurantReviewsPage />)} />
                 <Route path={ROUTES.RESTAURANT_VOUCHERS} element={restaurantRoute(<RestaurantVouchersPage />)} />
+                <Route path={ROUTES.RESTAURANT_LIVESTREAM} element={restaurantRoute(<RestaurantLivestreamPage />)} />
                 <Route path={ROUTES.RESTAURANT_CATALOG_IMPORT} element={restaurantRoute(<CatalogImportPage portal="restaurant" />)} />
                 <Route path={ROUTES.ADMIN_DASHBOARD} element={adminRoute(<AdminDashboard />)} />
                 <Route path={ROUTES.ADMIN_ORDERS} element={adminRoute(<AdminOrdersPage />)} />
                 <Route path={ROUTES.ADMIN_SHIPPERS} element={adminRoute(<AdminShippersPage />)} />
                 <Route path={ROUTES.ADMIN_RATINGS} element={adminRoute(<AdminRatingsPage />)} />
                 <Route path={ROUTES.ADMIN_COUPONS} element={adminRoute(<AdminCouponsPage />)} />
                 <Route path={ROUTES.ADMIN_FLASH_SALES} element={adminRoute(<AdminFlashSalePage />)} />
                 <Route path={ROUTES.ADMIN_CATALOG_IMPORT} element={adminRoute(<CatalogImportPage portal="admin" />)} />
                 <Route path={ROUTES.UNAUTHORIZED} element={<UnauthorizedPage />} />
                 <Route path="*" element={<Navigate to={ROUTES.HOME} replace />} />
diff --git a/src/app/dependencies.tsx b/src/app/dependencies.tsx
index 9a66a0c..45c8987 100644
--- a/src/app/dependencies.tsx
+++ b/src/app/dependencies.tsx
@@ -1,18 +1,19 @@
 import { createContext, useContext, type ReactNode } from 'react';
 import authService from '@/modules/auth/services/authService';
 import * as menuService from '@/modules/restaurant/services/menuService';
 import * as orderService from '@/modules/restaurant/services/orderService';
 import * as restaurantService from '@/modules/restaurant/services/restaurantService';
 import * as addressService from '@/services/api/addressService';
 import adminService from '@/services/api/adminService';
 import { flashSaleAdminService } from '@/services/api/flashSaleService';
+import { livestreamHostService } from '@/services/api/livestreamService';
 import * as promotionService from '@/services/api/promotionService';
 import TokenStorage from '@/services/storage/localStorage';
 import { toast } from 'react-hot-toast';
 
 export type AuthServicePort = Pick<
   typeof authService,
   | 'getCurrentUser'
   | 'getProfile'
   | 'hasRole'
   | 'hasStoredSession'
@@ -91,20 +92,21 @@ export type AdminServicePort = Pick<
 >;
 
 export type FlashSaleAdminServicePort = Pick<
   typeof flashSaleAdminService,
   | 'approveItem'
   | 'createCampaign'
   | 'getAllCampaigns'
   | 'getCampaignItems'
   | 'updateCampaignStatus'
 >;
+export type LivestreamHostServicePort = Pick<typeof livestreamHostService, 'inspect' | 'create' | 'start' | 'end'>;
 
 export interface NotificationPort {
   success(message: string): unknown;
   error(message: string): unknown;
 }
 
 export interface ClockPort {
   now(): Date;
 }
 
@@ -122,35 +124,37 @@ export type SessionStorePort = Pick<
 
 export interface AppDependencies {
   auth: AuthServicePort;
   restaurant: RestaurantServicePort;
   menu: MenuServicePort;
   order: OrderServicePort;
   promotion: PromotionServicePort;
   address: AddressServicePort;
   admin: AdminServicePort;
   flashSaleAdmin: FlashSaleAdminServicePort;
+  livestreamHost: LivestreamHostServicePort;
   notifications: NotificationPort;
   clock: ClockPort;
   delay: DelayPort;
   sessionStore: SessionStorePort;
 }
 
 export const productionDependencies: AppDependencies = {
   auth: authService,
   restaurant: restaurantService,
   menu: menuService,
   order: orderService,
   promotion: promotionService,
   address: addressService,
   admin: adminService,
   flashSaleAdmin: flashSaleAdminService,
+  livestreamHost: livestreamHostService,
   notifications: toast,
   clock: { now: () => new Date() },
   delay: {
     wait: (delayMs) =>
       new Promise((resolve) => window.setTimeout(resolve, delayMs)),
   },
   sessionStore: TokenStorage,
 };
 
 const AppDependenciesContext = createContext<AppDependencies | null>(null);
diff --git a/src/components/navigation/portalNavigation.ts b/src/components/navigation/portalNavigation.ts
index 0da9c6f..be90414 100644
--- a/src/components/navigation/portalNavigation.ts
+++ b/src/components/navigation/portalNavigation.ts
@@ -6,20 +6,21 @@ export interface PortalNavigationItem {
   label: string;
 }
 
 export const restaurantNavigation: PortalNavigationItem[] = [
   { icon: 'dashboard', label: 'Dashboard', to: ROUTES.RESTAURANT_DASHBOARD },
   { icon: 'shopping_bag', label: 'Đơn hàng', to: ROUTES.RESTAURANT_ORDERS },
   { icon: 'menu_book', label: 'Quản lý Menu', to: ROUTES.RESTAURANT_MENU },
   { icon: 'storefront', label: 'Hồ sơ cửa hàng', to: ROUTES.RESTAURANT_PROFILE },
   { icon: 'reviews', label: 'Đánh giá', to: ROUTES.RESTAURANT_REVIEWS },
   { icon: 'local_offer', label: 'Voucher nhà hàng', to: ROUTES.RESTAURANT_VOUCHERS },
+  { icon: 'videocam', label: 'Livestream', to: ROUTES.RESTAURANT_LIVESTREAM },
   { icon: 'upload_file', label: 'Import dữ liệu', to: ROUTES.RESTAURANT_CATALOG_IMPORT },
 ];
 
 export const adminNavigation: PortalNavigationItem[] = [
   { to: ROUTES.ADMIN_DASHBOARD, icon: 'dashboard', label: 'Dashboard' },
   { to: ROUTES.ADMIN_ORDERS, icon: 'receipt_long', label: 'Đơn hàng' },
   { to: ROUTES.ADMIN_SHIPPERS, icon: 'local_shipping', label: 'Shipper' },
   { to: ROUTES.ADMIN_RATINGS, icon: 'reviews', label: 'Đánh giá' },
   { to: ROUTES.ADMIN_COUPONS, icon: 'sell', label: 'Coupon' },
   { to: ROUTES.ADMIN_FLASH_SALES, icon: 'bolt', label: 'Flash Sale' },
diff --git a/src/modules/restaurant/__tests__/restaurant-livestream.test.tsx b/src/modules/restaurant/__tests__/restaurant-livestream.test.tsx
new file mode 100644
index 0000000..03db724
--- /dev/null
+++ b/src/modules/restaurant/__tests__/restaurant-livestream.test.tsx
@@ -0,0 +1,94 @@
+import { screen, waitFor } from '@testing-library/react';
+import userEvent from '@testing-library/user-event';
+import { describe, expect, it, vi } from 'vitest';
+import type { AppDependencies } from '@/app/dependencies';
+import type { Livestream } from '@/services/api/livestreamService';
+import { USER_ROLES } from '@/utils/constants';
+import { buildUser } from '@/test/builders';
+import {
+  createAuthFake,
+  createLivestreamHostFake,
+  createRestaurantFake,
+  createSessionStoreFake,
+  createTestDependencies,
+} from '@/test/createTestDependencies';
+import { renderApp } from '@/test/renderApp';
+
+const stream: Livestream = {
+  id: '00000000-0000-4000-8000-000000000001',
+  restaurantId: 201,
+  title: 'Bếp trực tiếp',
+  status: 'CREATED',
+  streamProvider: 'AGORA',
+};
+
+function ownerDependencies(overrides: Partial<AppDependencies> = {}) {
+  const owner = buildUser({ role: USER_ROLES.RESTAURANT_OWNER });
+  return createTestDependencies({
+    auth: createAuthFake({
+      isAuthenticated: vi.fn(() => true),
+      getCurrentUser: vi.fn(() => owner),
+      getProfile: vi.fn(async () => owner),
+    }),
+    restaurant: createRestaurantFake(),
+    sessionStore: createSessionStoreFake({
+      hasToken: vi.fn(() => true),
+      getCurrentRestaurantId: vi.fn(() => 201),
+    }),
+    ...overrides,
+  });
+}
+
+describe('restaurant livestream controls', () => {
+  it('inspects, creates, starts, and ends a stream through injected Gateway actions', async () => {
+    const user = userEvent.setup();
+    const createdStream = { ...stream, id: '00000000-0000-4000-8000-000000000002' };
+    const inspect = vi.fn()
+      .mockResolvedValueOnce([])
+      .mockResolvedValueOnce([{ ...createdStream, status: 'LIVE' as const }])
+      .mockResolvedValueOnce([{ ...createdStream, status: 'ENDED' as const }]);
+    const create = vi.fn(async (request) => ({ ...createdStream, title: request.title }));
+    const start = vi.fn(async () => undefined);
+    const end = vi.fn(async () => undefined);
+
+    renderApp({
+      route: '/restaurant/livestream',
+      dependencies: ownerDependencies({
+        livestreamHost: createLivestreamHostFake({ inspect, create, start, end }),
+      }),
+    });
+
+    expect(await screen.findByRole('heading', { name: 'Livestream' })).toBeInTheDocument();
+    await user.type(screen.getByLabelText('Tiêu đề livestream'), 'New show');
+    await user.click(screen.getByRole('button', { name: 'Tạo livestream' }));
+    await waitFor(() => expect(create).toHaveBeenCalledWith({
+      restaurantId: 201,
+      title: 'New show',
+      streamProvider: 'AGORA',
+    }));
+    expect(await screen.findByText('New show')).toBeInTheDocument();
+    await user.click(screen.getByRole('button', { name: 'Bắt đầu' }));
+    await waitFor(() => expect(start).toHaveBeenCalledWith(createdStream.id));
+    await user.click(screen.getByRole('button', { name: 'Kết thúc' }));
+    await waitFor(() => expect(end).toHaveBeenCalledWith(createdStream.id));
+  });
+
+  it('shows a recoverable inspect error and retries', async () => {
+    const user = userEvent.setup();
+    const inspect = vi.fn()
+      .mockRejectedValueOnce(new Error('offline'))
+      .mockResolvedValueOnce([]);
+
+    renderApp({
+      route: '/restaurant/livestream',
+      dependencies: ownerDependencies({
+        livestreamHost: createLivestreamHostFake({ inspect }),
+      }),
+    });
+
+    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải livestream.');
+    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
+    await waitFor(() => expect(inspect).toHaveBeenCalledTimes(2));
+    expect(await screen.findByText('Chưa có livestream.')).toBeInTheDocument();
+  });
+});
diff --git a/src/modules/restaurant/pages/RestaurantLivestreamPage.tsx b/src/modules/restaurant/pages/RestaurantLivestreamPage.tsx
new file mode 100644
index 0000000..5f68e04
--- /dev/null
+++ b/src/modules/restaurant/pages/RestaurantLivestreamPage.tsx
@@ -0,0 +1,152 @@
+import { useCallback, useEffect, useState } from 'react';
+import RestaurantHeader from '@/components/layout/RestaurantHeader';
+import RestaurantSidebar from '@/components/layout/RestaurantSidebar';
+import { useAppDependencies } from '@/app/dependencies';
+import { useRestaurant } from '../hooks/useRestaurant';
+import type { Livestream } from '@/services/api/livestreamService';
+
+export default function RestaurantLivestreamPage() {
+  const { currentRestaurant } = useRestaurant();
+  const { livestreamHost, notifications } = useAppDependencies();
+  const [streams, setStreams] = useState<Livestream[]>([]);
+  const [loading, setLoading] = useState(true);
+  const [error, setError] = useState<string | null>(null);
+  const [title, setTitle] = useState('');
+  const [saving, setSaving] = useState(false);
+  const [transitioningId, setTransitioningId] = useState<string | null>(null);
+
+  const load = useCallback(async () => {
+    if (!currentRestaurant) {
+      setLoading(false);
+      return;
+    }
+    setLoading(true);
+    setError(null);
+    try {
+      setStreams(await livestreamHost.inspect(currentRestaurant.id));
+    } catch {
+      setError('Không thể tải livestream.');
+    } finally {
+      setLoading(false);
+    }
+  }, [currentRestaurant, livestreamHost]);
+
+  useEffect(() => {
+    void load();
+  }, [load]);
+
+  const create = async () => {
+    if (!currentRestaurant || !title.trim() || saving) return;
+    setSaving(true);
+    try {
+      const stream = await livestreamHost.create({
+        title: title.trim(),
+        restaurantId: currentRestaurant.id,
+        streamProvider: 'AGORA',
+      });
+      setStreams((current) => [stream, ...current]);
+      setTitle('');
+      notifications.success('Đã tạo livestream');
+    } catch {
+      notifications.error('Không thể tạo livestream');
+    } finally {
+      setSaving(false);
+    }
+  };
+
+  const transition = async (stream: Livestream, action: 'start' | 'end') => {
+    if (transitioningId) return;
+    setTransitioningId(stream.id);
+    try {
+      await livestreamHost[action](stream.id);
+      await load();
+      notifications.success(
+        action === 'start' ? 'Đã bắt đầu livestream' : 'Đã kết thúc livestream',
+      );
+    } catch {
+      notifications.error('Không thể cập nhật livestream');
+    } finally {
+      setTransitioningId(null);
+    }
+  };
+
+  return (
+    <div className="flex min-h-screen bg-background-light dark:bg-background-dark">
+      <RestaurantSidebar
+        restaurantName={currentRestaurant?.name}
+        restaurantImage={currentRestaurant?.image}
+      />
+      <main className="min-w-0 flex-1">
+        <RestaurantHeader
+          title="Livestream"
+          subtitle="Host controls use Gateway-only server-issued media credentials"
+        />
+        <section className="space-y-5 p-6 lg:p-8">
+          <div className="rounded-2xl border bg-white p-5 dark:bg-surface-dark">
+            <label className="block text-sm font-semibold">
+              Tiêu đề livestream
+              <input
+                aria-label="Tiêu đề livestream"
+                value={title}
+                onChange={(event) => setTitle(event.target.value)}
+                className="mt-1 w-full rounded border p-2"
+              />
+            </label>
+            <button
+              type="button"
+              onClick={() => void create()}
+              disabled={saving || !currentRestaurant}
+              className="mt-3 rounded bg-primary px-4 py-2 font-bold text-white disabled:opacity-50"
+            >
+              {saving ? 'Đang tạo…' : 'Tạo livestream'}
+            </button>
+          </div>
+
+          {loading ? (
+            <p>Đang tải…</p>
+          ) : error ? (
+            <div>
+              <p role="alert">{error}</p>
+              <button type="button" onClick={() => void load()}>
+                Thử lại
+              </button>
+            </div>
+          ) : streams.length === 0 ? (
+            <p>Chưa có livestream.</p>
+          ) : (
+            streams.map((stream) => {
+              const isTransitioning = transitioningId === stream.id;
+              return (
+                <article
+                  key={stream.id}
+                  className="rounded-xl border bg-white p-4 dark:bg-surface-dark"
+                >
+                  <b>{stream.title}</b>
+                  <p>{stream.status}</p>
+                  {stream.status === 'CREATED' && (
+                    <button
+                      type="button"
+                      disabled={isTransitioning}
+                      onClick={() => void transition(stream, 'start')}
+                    >
+                      {isTransitioning ? 'Đang cập nhật…' : 'Bắt đầu'}
+                    </button>
+                  )}
+                  {stream.status === 'LIVE' && (
+                    <button
+                      type="button"
+                      disabled={isTransitioning}
+                      onClick={() => void transition(stream, 'end')}
+                    >
+                      {isTransitioning ? 'Đang cập nhật…' : 'Kết thúc'}
+                    </button>
+                  )}
+                </article>
+              );
+            })
+          )}
+        </section>
+      </main>
+    </div>
+  );
+}
diff --git a/src/services/api/__tests__/livestreamService.test.ts b/src/services/api/__tests__/livestreamService.test.ts
new file mode 100644
index 0000000..ceb5172
--- /dev/null
+++ b/src/services/api/__tests__/livestreamService.test.ts
@@ -0,0 +1,58 @@
+import { beforeEach, describe, expect, it, vi } from 'vitest';
+import apiClient, { handleResponse } from '../apiClient';
+import { livestreamHostService } from '../livestreamService';
+
+vi.mock('../apiClient', () => ({
+  default: { get: vi.fn(), post: vi.fn() },
+  handleResponse: vi.fn((response: { data: unknown }) =>
+    (response.data as { data: unknown }).data),
+}));
+
+const client = vi.mocked(apiClient);
+const response = (data: unknown) => ({ data: { status: 1, message: null, data } });
+const stream = {
+  id: '00000000-0000-4000-8000-000000000001',
+  restaurantId: 42,
+  title: 'Friday kitchen',
+  status: 'CREATED',
+  streamProvider: 'AGORA',
+};
+
+describe('livestream host Gateway adapter', () => {
+  beforeEach(() => vi.clearAllMocks());
+
+  it('uses exact inspect/create/start/end Gateway routes', async () => {
+    client.get.mockResolvedValue(response([stream]) as never);
+    client.post
+      .mockResolvedValueOnce(response(stream) as never)
+      .mockResolvedValue({ data: { status: 1, message: null, data: null } } as never);
+
+    await expect(livestreamHostService.inspect(42)).resolves.toEqual([stream]);
+    await expect(livestreamHostService.create({
+      restaurantId: 42,
+      title: 'Friday kitchen',
+      streamProvider: 'AGORA',
+    })).resolves.toEqual(stream);
+    await livestreamHostService.start(stream.id);
+    await livestreamHostService.end(stream.id);
+
+    expect(client.get).toHaveBeenCalledWith('/api/livestreams/restaurant/42');
+    expect(client.post).toHaveBeenNthCalledWith(1, '/api/livestreams', {
+      restaurantId: 42,
+      title: 'Friday kitchen',
+      streamProvider: 'AGORA',
+    });
+    expect(client.post).toHaveBeenNthCalledWith(2, `/api/livestreams/${stream.id}/start`);
+    expect(client.post).toHaveBeenNthCalledWith(3, `/api/livestreams/${stream.id}/end`);
+  });
+
+  it('rejects malformed UUID/status/provider responses', async () => {
+    client.get.mockResolvedValue(response([{
+      ...stream,
+      id: 'not-a-uuid',
+    }]) as never);
+
+    await expect(livestreamHostService.inspect(42)).rejects.toThrow('UUID');
+    expect(handleResponse).toHaveBeenCalled();
+  });
+});
diff --git a/src/services/api/endpoints.ts b/src/services/api/endpoints.ts
index 95f7536..1467f2b 100644
--- a/src/services/api/endpoints.ts
+++ b/src/services/api/endpoints.ts
@@ -107,14 +107,21 @@ export const API_ENDPOINTS = {
       GET_CAMPAIGN_ITEMS: (id: number) => `/api/flashsales/admin/campaigns/${id}/items`,
       GET_CAMPAIGN_ITEMS_PAGE: (id: number) => `/api/flashsales/admin/campaigns/${id}/items/page`,
       UPDATE_CAMPAIGN_STATUS: (id: number) => `/api/flashsales/admin/campaigns/${id}/status`,
       APPROVE_ITEM: (id: number) => `/api/flashsales/admin/items/${id}/approve`,
     }
   },
 
   ANALYTICS: {
     ADMIN_DASHBOARD: '/api/analytics/dashboard/admin',
   },
+  LIVESTREAM: {
+    ACTIVE: '/api/livestreams/active',
+    CREATE: '/api/livestreams',
+    START: (id: string) => `/api/livestreams/${id}/start`,
+    END: (id: string) => `/api/livestreams/${id}/end`,
+    INSPECT_RESTAURANT: (restaurantId: number) => `/api/livestreams/restaurant/${restaurantId}`,
+  },
 
 } as const;
 
 export default API_ENDPOINTS;
diff --git a/src/services/api/livestreamService.ts b/src/services/api/livestreamService.ts
new file mode 100644
index 0000000..f65b91a
--- /dev/null
+++ b/src/services/api/livestreamService.ts
@@ -0,0 +1,24 @@
+import apiClient, { handleResponse } from './apiClient';
+import { API_ENDPOINTS } from './endpoints';
+import { requireArray, requireEnum, requireInteger, requireRecord, requireString } from './contract';
+
+const statuses = ['CREATED', 'LIVE', 'ENDED'] as const;
+const providers = ['AGORA'] as const;
+const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
+
+export interface Livestream { id: string; restaurantId: number; title: string; status: typeof statuses[number]; streamProvider: 'AGORA'; }
+export interface CreateLivestreamRequest { title: string; description?: string; restaurantId: number; streamProvider: 'AGORA'; }
+
+const parse = (value: unknown, label: string): Livestream => {
+  const row = requireRecord(value, label);
+  const id = requireString(row.id, `${label}.id`);
+  if (!uuid.test(id)) throw new Error(`${label}.id must be a UUID`);
+  return { id, restaurantId: requireInteger(row.restaurantId, `${label}.restaurantId`, 1), title: requireString(row.title, `${label}.title`), status: requireEnum(row.status, statuses, `${label}.status`), streamProvider: requireEnum(row.streamProvider, providers, `${label}.streamProvider`) };
+};
+
+export const livestreamHostService = {
+  inspect: async (restaurantId: number): Promise<Livestream[]> => requireArray(handleResponse(await apiClient.get(API_ENDPOINTS.LIVESTREAM.INSPECT_RESTAURANT(restaurantId))), 'livestreams', parse),
+  create: async (request: CreateLivestreamRequest): Promise<Livestream> => parse(handleResponse(await apiClient.post(API_ENDPOINTS.LIVESTREAM.CREATE, request)), 'livestream'),
+  start: async (id: string): Promise<void> => { await apiClient.post(API_ENDPOINTS.LIVESTREAM.START(id)); },
+  end: async (id: string): Promise<void> => { await apiClient.post(API_ENDPOINTS.LIVESTREAM.END(id)); },
+};
diff --git a/src/utils/constants.ts b/src/utils/constants.ts
index 4611991..bd52573 100644
--- a/src/utils/constants.ts
+++ b/src/utils/constants.ts
@@ -83,20 +83,21 @@ export const ROUTES = {
   ADMIN_COUPONS: '/admin/coupons',
   ADMIN_FLASH_SALES: '/admin/flash-sales',
   ADMIN_CATALOG_IMPORT: '/admin/catalog-import',
   RESTAURANT_DASHBOARD: '/restaurant/dashboard', // Shop Owner dashboard
   RESTAURANT_PROFILE: '/restaurant/profile',
   RESTAURANT_MENU: '/restaurant/menu',
   RESTAURANT_ORDERS: '/restaurant/orders',
   RESTAURANT_REVIEWS: '/restaurant/reviews',
   RESTAURANT_VOUCHERS: '/restaurant/vouchers',
   RESTAURANT_CATALOG_IMPORT: '/restaurant/catalog-import',
+  RESTAURANT_LIVESTREAM: '/restaurant/livestream',
   
   // Error pages
   UNAUTHORIZED: '/unauthorized',
 } as const;
 
 export const isCustomerRoute = (pathname: string): boolean =>
   pathname.startsWith('/customer/') ||
   pathname === ROUTES.CUSTOMER_CART ||
   pathname === ROUTES.CUSTOMER_RESTAURANTS ||
   pathname.startsWith(`${ROUTES.CUSTOMER_RESTAURANTS}/`);

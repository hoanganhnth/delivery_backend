# Review package: 8f7b9a7..f125e1b

## Commits
f125e1b feat(livestream): restore customer viewer boundary

## Files changed
 lib/core/config/runtime_config.dart                |   4 +
 lib/core/routing/app_router.dart                   |  58 +++++++++--
 lib/core/routing/constants/app_routes.dart         |   5 +
 .../application/livestream_viewer_view_model.dart  | 116 +++++++++++++++++++++
 .../livestream/data/livestream_gateway.dart        |  64 ++++++++++++
 .../data/livestream_repository_impl.dart           |  12 +++
 .../domain/entities/livestream_join_session.dart   |  19 ++++
 .../domain/repositories/livestream_repository.dart |   5 +
 .../domain/usecases/join_livestream_use_case.dart  |  12 +++
 .../presentation/livestream_viewer_page.dart       |  58 +++++++++++
 .../livestream_viewer_view_model_test.dart         | 113 ++++++++++++++++++++
 .../livestream/data/livestream_gateway_test.dart   |  51 +++++++++
 .../usecases/join_livestream_use_case_test.dart    |  34 ++++++
 13 files changed, 541 insertions(+), 10 deletions(-)

## Diff
diff --git a/lib/core/config/runtime_config.dart b/lib/core/config/runtime_config.dart
index f329329..fe77a25 100644
--- a/lib/core/config/runtime_config.dart
+++ b/lib/core/config/runtime_config.dart
@@ -13,20 +13,24 @@ class RuntimeConfig {
     defaultValue: false,
   );
   static const voucherStackingEnabled = bool.fromEnvironment(
     'VOUCHER_STACKING_ENABLED',
     defaultValue: false,
   );
   static const flashSaleCheckoutEnabled = bool.fromEnvironment(
     'FLASHSALE_CHECKOUT_ENABLED',
     defaultValue: false,
   );
+  static const livestreamViewerEnabled = bool.fromEnvironment(
+    'LIVESTREAM_CLIENT_API_ENABLED',
+    defaultValue: false,
+  );
   static const vnpayPaymentEnabled = bool.fromEnvironment(
     'VNPAY_PAYMENT_ENABLED',
     defaultValue: false,
   );
   static const _vnpayReturnUri = String.fromEnvironment(
     'VNPAY_RETURN_URI',
     defaultValue: 'delivery://payments/vnpay-return',
   );
 
   static Uri get vnpayReturnUri => Uri.parse(_vnpayReturnUri);
diff --git a/lib/core/routing/app_router.dart b/lib/core/routing/app_router.dart
index 02f3a4a..06c5546 100644
--- a/lib/core/routing/app_router.dart
+++ b/lib/core/routing/app_router.dart
@@ -9,61 +9,72 @@
 library;
 
 import 'package:delivery_app/features/home/presentation/pages/home_page.dart';
 import 'package:delivery_app/features/orders/presentation/screens/order_detail_screen.dart';
 import 'package:delivery_app/features/orders/presentation/screens/refund_status_history_screen.dart';
 import 'package:flutter/widgets.dart';
 import 'package:flutter/foundation.dart';
 import 'package:go_router/go_router.dart';
 import 'package:delivery_app/features/auth/presentation/screens/login_screen.dart';
 import 'package:delivery_app/features/auth/presentation/screens/register_screen.dart';
+import 'package:delivery_app/features/auth/presentation/pages/forgot_password_page.dart';
+import 'package:delivery_app/features/auth/presentation/pages/reset_password_page.dart';
 import 'package:delivery_app/features/notification/presentation/screens/notification_screen.dart';
 import 'package:delivery_app/features/search/presentation/screens/search_screen.dart';
 import 'package:delivery_app/features/main/presentation/pages/main_screen.dart';
 import 'package:delivery_app/features/profile/profile.dart';
 import 'package:delivery_app/features/settings/settings.dart';
 import 'package:delivery_app/features/debug/debug.dart';
 import 'package:delivery_app/features/orders/orders.dart';
 import 'package:delivery_app/features/restaurants/restaurants.dart';
+import 'package:delivery_app/features/livestream/presentation/livestream_viewer_page.dart';
 import 'package:delivery_app/features/cart/cart.dart';
 import 'package:delivery_app/features/user_address/presentation/screens/address_list_screen.dart';
 import 'package:delivery_app/features/user_address/presentation/screens/add_edit_address_screen.dart';
 import 'package:delivery_app/features/user_address/domain/entities/user_address_entity.dart';
 import 'package:delivery_app/features/splash/presentation/screens/splash_screen.dart';
 import 'package:delivery_app/core/widgets/amber_widgets.dart';
 import 'package:delivery_app/core/routing/constants/app_routes.dart';
 import 'package:delivery_app/core/routing/models/app_router_config.dart';
 import 'package:delivery_app/core/routing/models/i_auth_checker.dart';
 import 'package:delivery_app/core/routing/guards/guard_manager.dart';
 
+final _livestreamUuid = RegExp(
+  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$',
+);
+
 /// Replaceable screen factory for router tests and previews. Production keeps
 /// the concrete pages below; tests can verify redirects and route parameters
 /// without constructing network, storage, Firebase or Mapbox dependencies.
 class AppRouterPages {
   const AppRouterPages();
 
   Widget splash() => const SplashScreen();
   Widget login() => const LoginScreen();
   Widget register() => const RegisterScreen();
+  Widget forgotPassword() => const ForgotPasswordPage();
+  Widget resetPassword(String token) => ResetPasswordPage(token: token);
   Widget main() => const MainScreen();
   Widget search() => const SearchScreen();
   Widget notifications() => const NotificationScreen();
   Widget home() => const HomePage();
   Widget profile() => const ProfileScreen();
   Widget settings() => const SettingsScreen();
   Widget debugTools() => const DebugToolsScreen();
   Widget orders() => const OrdersScreen();
   Widget refundHistory() => const RefundStatusHistoryScreen();
   Widget orderDetail(int orderId) => OrderDetailScreen(orderId: orderId);
   Widget restaurants() => const AllRestaurantsScreen();
   Widget restaurantDetail(int restaurantId) =>
       RestaurantDetailScreen(restaurantId: restaurantId);
+  Widget livestreamViewer(String livestreamId) =>
+      LivestreamViewerPage(livestreamId: livestreamId);
   Widget cart() => const CartScreen();
   Widget checkout() => const CheckoutScreen();
   Widget orderConfirmation() => const OrderConfirmationScreen();
   Widget addressList() => const AddressListScreen();
   Widget addAddress() => const AddEditAddressScreen();
   Widget editAddress(UserAddressEntity? address, {int? addressId}) =>
       AddEditAddressScreen(address: address, addressId: addressId);
   Widget notFound() => const NotFoundScreen();
   Widget error() => const ErrorScreen();
 }
@@ -77,27 +88,28 @@ GoRouter createAppRouter({
   required IAuthNotifier authNotifier,
   required AppRouterConfig config,
   AppRouterPages pages = const AppRouterPages(),
 }) {
   final guardManager = GuardManager(authNotifier);
 
   return GoRouter(
     refreshListenable: authNotifier,
     initialLocation: config.initialLocation,
     debugLogDiagnostics: config.debugLogDiagnostics,
-    redirect: config.enableRedirects
-        ? (context, state) {
-            // Splash handles its own navigation — skip guard
-            if (state.uri.path == AppRoutes.splash) return null;
-            return guardManager.applyAuthGuard(context, state);
-          }
-        : null,
+    redirect:
+        config.enableRedirects
+            ? (context, state) {
+              // Splash handles its own navigation — skip guard
+              if (state.uri.path == AppRoutes.splash) return null;
+              return guardManager.applyAuthGuard(context, state);
+            }
+            : null,
     routes: [
       // Root route — redirect to splash
       GoRoute(
         path: AppRoutes.root,
         name: 'root',
         redirect: (context, state) => AppRoutes.splash,
       ),
 
       // Splash
       GoRoute(
@@ -110,20 +122,32 @@ GoRouter createAppRouter({
       GoRoute(
         path: AppRoutes.login,
         name: 'login',
         builder: (context, state) => pages.login(),
       ),
       GoRoute(
         path: AppRoutes.register,
         name: 'register',
         builder: (context, state) => pages.register(),
       ),
+      GoRoute(
+        path: AppRoutes.forgotPassword,
+        name: 'forgot-password',
+        builder: (context, state) => pages.forgotPassword(),
+      ),
+      GoRoute(
+        path: AppRoutes.resetPassword,
+        name: 'reset-password',
+        builder:
+            (context, state) =>
+                pages.resetPassword(state.uri.queryParameters['token'] ?? ''),
+      ),
       // Main navigation
       GoRoute(
         path: AppRoutes.main,
         name: 'main',
         builder: (context, state) => pages.main(),
       ),
       GoRoute(
         path: AppRoutes.search,
         name: 'search',
         builder: (context, state) => pages.search(),
@@ -229,39 +253,50 @@ GoRouter createAppRouter({
       GoRoute(
         path: AppRoutes.checkout,
         name: 'checkout',
         builder: (context, state) => pages.checkout(),
       ),
       GoRoute(
         path: AppRoutes.orderConfirmation,
         name: 'order-confirmation',
         builder: (context, state) => pages.orderConfirmation(),
       ),
+      GoRoute(
+        path: AppRoutes.livestreamViewer,
+        name: 'livestream-viewer',
+        builder: (context, state) {
+          final livestreamId = state.pathParameters['livestreamId'] ?? '';
+          return _livestreamUuid.hasMatch(livestreamId)
+              ? pages.livestreamViewer(livestreamId)
+              : pages.notFound();
+        },
+      ),
 
       // Address management
       GoRoute(
         path: AppRoutes.addressList,
         name: 'address-list',
         builder: (context, state) => pages.addressList(),
       ),
       GoRoute(
         path: AppRoutes.addAddress,
         name: 'add-address',
         builder: (context, state) => pages.addAddress(),
       ),
       GoRoute(
         path: AppRoutes.editAddress,
         name: 'edit-address',
         builder: (context, state) {
-          final address = state.extra is UserAddressEntity
-              ? state.extra! as UserAddressEntity
-              : null;
+          final address =
+              state.extra is UserAddressEntity
+                  ? state.extra! as UserAddressEntity
+                  : null;
           final addressId = parsePositiveRouteId(
             state.uri.queryParameters['addressId'],
           );
           return pages.editAddress(address, addressId: addressId);
         },
       ),
 
       // 404
       GoRoute(
         path: AppRoutes.notFound,
@@ -275,20 +310,23 @@ GoRouter createAppRouter({
 
 int? parsePositiveRouteId(String? rawValue) {
   final value = int.tryParse(rawValue ?? '');
   return value != null && value > 0 ? value : null;
 }
 
 /// Navigation extension for [GoRouter] — named-route shortcuts.
 extension GoRouterExtension on GoRouter {
   void pushLogin() => pushNamed('login');
   void pushRegister() => pushNamed('register');
+  void pushForgotPassword() => pushNamed('forgot-password');
+  void pushResetPassword(String token) =>
+      pushNamed('reset-password', queryParameters: {'token': token});
   void pushHome() => pushNamed('home');
   void pushProfile() => pushNamed('profile');
   void pushSettings() => pushNamed('settings');
   void pushDebugTools() => pushNamed('debug-tools');
   void pushOrders() => pushNamed('orders');
   void pushRefundHistory() => pushNamed('refund-history');
   void pushRestaurants() => pushNamed('restaurants');
   void pushCart() => pushNamed('cart');
 
   void pushOrderDetails(String orderId) =>
diff --git a/lib/core/routing/constants/app_routes.dart b/lib/core/routing/constants/app_routes.dart
index d438610..852b214 100644
--- a/lib/core/routing/constants/app_routes.dart
+++ b/lib/core/routing/constants/app_routes.dart
@@ -1,50 +1,55 @@
 /// App route constants
 /// This file contains all route paths used in the application
 class AppRoutes {
   // Root routes
   static const String root = '/';
   static const String splash = '/splash';
 
   // Auth routes
   static const String login = '/login';
   static const String register = '/register';
+  static const String forgotPassword = '/forgot-password';
+  static const String resetPassword = '/reset-password';
 
   // Main app routes
   static const String main = '/main';
   static const String home = '/home';
   static const String profile = '/profile';
   static const String settings = '/settings';
   static const String debugTools = '/debug-tools';
   static const String search = '/search';
   static const String notifications = '/notifications';
 
   // Delivery routes
   static const String orders = '/orders';
   static const String refundHistory = '/refunds';
   static const String orderDetails = '/orders/:orderId';
   static const String trackOrder = '/orders/:orderId/track';
 
   // Restaurant routes
   static const String restaurants = '/restaurants';
   static const String restaurantDetails = '/restaurants/:restaurantId';
+  static const String livestreamViewer = '/livestreams/:livestreamId';
 
   // Cart and checkout
   static const String cart = '/cart';
   static const String checkout = '/checkout';
   static const String orderConfirmation = '/order-confirmation';
 
   // Address management
   static const String addressList = '/address-list';
   static const String addAddress = '/add-address';
   static const String editAddress = '/edit-address';
 
   // Error routes
   static const String notFound = '/404';
   static const String error = '/error';
 
   // Helper methods to generate dynamic routes
   static String orderDetailsPath(String orderId) => '/orders/$orderId';
   static String trackOrderPath(String orderId) => '/orders/$orderId/track';
   static String restaurantDetailsPath(String restaurantId) =>
       '/restaurants/$restaurantId';
+  static String livestreamViewerPath(String livestreamId) =>
+      '/livestreams/$livestreamId';
 }
diff --git a/lib/features/livestream/application/livestream_viewer_view_model.dart b/lib/features/livestream/application/livestream_viewer_view_model.dart
new file mode 100644
index 0000000..2b5b34a
--- /dev/null
+++ b/lib/features/livestream/application/livestream_viewer_view_model.dart
@@ -0,0 +1,116 @@
+import 'dart:async';
+
+import 'package:flutter_riverpod/flutter_riverpod.dart';
+import 'package:delivery_app/core/config/runtime_config.dart';
+import 'package:delivery_app/features/auth/di/auth_network_providers.dart';
+
+import '../data/livestream_gateway.dart';
+import '../data/livestream_repository_impl.dart';
+import '../domain/entities/livestream_join_session.dart';
+import '../domain/repositories/livestream_repository.dart';
+import '../domain/usecases/join_livestream_use_case.dart';
+
+abstract interface class LivestreamMediaPort {
+  Future<void> join(LivestreamJoinSession session);
+  Future<void> leave();
+}
+
+final class UnsupportedLivestreamMediaPort implements LivestreamMediaPort {
+  const UnsupportedLivestreamMediaPort();
+  @override
+  Future<void> join(LivestreamJoinSession session) =>
+      Future.error(const LivestreamMediaUnavailableException());
+  @override
+  Future<void> leave() async {}
+}
+
+final class LivestreamMediaUnavailableException implements Exception {
+  const LivestreamMediaUnavailableException();
+}
+
+enum LivestreamViewerPhase { disabled, idle, loading, mediaUnavailable, error }
+
+final class LivestreamViewerState {
+  const LivestreamViewerState({
+    this.phase = LivestreamViewerPhase.idle,
+    this.session,
+    this.message,
+  });
+  final LivestreamViewerPhase phase;
+  final LivestreamJoinSession? session;
+  final String? message;
+}
+
+final livestreamRepositoryProvider = Provider<LivestreamRepository>(
+  (ref) => LivestreamRepositoryImpl(
+    LivestreamGateway(ref.watch(authAwareDioProvider)),
+  ),
+);
+final joinLivestreamUseCaseProvider = Provider<JoinLivestreamUseCase>(
+  (ref) => JoinLivestreamUseCase(ref.watch(livestreamRepositoryProvider)),
+);
+final livestreamMediaPortProvider = Provider<LivestreamMediaPort>(
+  (ref) => const UnsupportedLivestreamMediaPort(),
+);
+final livestreamEnabledProvider = Provider<bool>(
+  (ref) => RuntimeConfig.livestreamViewerEnabled,
+);
+
+final livestreamViewerProvider =
+    NotifierProvider.family<
+      LivestreamViewerViewModel,
+      LivestreamViewerState,
+      String
+    >((livestreamId) => LivestreamViewerViewModel(livestreamId));
+
+class LivestreamViewerViewModel extends Notifier<LivestreamViewerState> {
+  LivestreamViewerViewModel(this._livestreamId);
+
+  final String _livestreamId;
+  bool _disposed = false;
+  @override
+  LivestreamViewerState build() {
+    final media = ref.read(livestreamMediaPortProvider);
+    ref.onDispose(() {
+      _disposed = true;
+      unawaited(media.leave());
+    });
+    return ref.read(livestreamEnabledProvider)
+        ? const LivestreamViewerState()
+        : const LivestreamViewerState(phase: LivestreamViewerPhase.disabled);
+  }
+
+  Future<void> join() async {
+    if (!ref.read(livestreamEnabledProvider)) return;
+    state = const LivestreamViewerState(phase: LivestreamViewerPhase.loading);
+    try {
+      final session = await ref.read(joinLivestreamUseCaseProvider)(_livestreamId);
+      if (_disposed) return;
+      try {
+        await ref.read(livestreamMediaPortProvider).join(session);
+        if (!_disposed) state = LivestreamViewerState(session: session);
+      } on LivestreamMediaUnavailableException {
+        if (!_disposed) {
+          state = LivestreamViewerState(
+            phase: LivestreamViewerPhase.mediaUnavailable,
+            session: session,
+          );
+        }
+      }
+    } on FormatException catch (_) {
+      if (!_disposed) {
+        state = const LivestreamViewerState(
+          phase: LivestreamViewerPhase.error,
+          message: 'Livestream data is unavailable',
+        );
+      }
+    } catch (_) {
+      if (!_disposed) {
+        state = const LivestreamViewerState(
+          phase: LivestreamViewerPhase.error,
+          message: 'Unable to join livestream',
+        );
+      }
+    }
+  }
+}
diff --git a/lib/features/livestream/data/livestream_gateway.dart b/lib/features/livestream/data/livestream_gateway.dart
new file mode 100644
index 0000000..9a4ff54
--- /dev/null
+++ b/lib/features/livestream/data/livestream_gateway.dart
@@ -0,0 +1,64 @@
+import 'package:dio/dio.dart';
+
+import '../domain/entities/livestream_join_session.dart';
+
+final class LivestreamGateway {
+  const LivestreamGateway(this._dio);
+  final Dio _dio;
+
+  Future<LivestreamJoinSession> join(String livestreamId) async {
+    if (!_uuid.hasMatch(livestreamId)) {
+      throw const FormatException('Invalid livestream identity');
+    }
+    final response = await _dio.post<Map<String, dynamic>>(
+      '/livestreams/$livestreamId/join',
+    );
+    final envelope = response.data;
+    if (envelope == null ||
+        envelope['status'] != 1 ||
+        envelope['data'] is! Map) {
+      throw const FormatException('Invalid livestream join envelope');
+    }
+    final data = Map<String, dynamic>.from(envelope['data'] as Map);
+    final id = data['livestreamId'];
+    final channel = data['channelName'];
+    final token = data['token'];
+    final uid = data['uid'];
+    final expiresAt = data['tokenExpiresAt'];
+    final title = data['title'];
+    final restaurantId = data['restaurantId'];
+    final parsedExpiry = expiresAt is String
+        ? DateTime.tryParse(expiresAt)?.toUtc()
+        : null;
+    if (id is! String ||
+        id != livestreamId ||
+        !_uuid.hasMatch(id) ||
+        channel is! String ||
+        channel.trim().isEmpty ||
+        token is! String ||
+        token.trim().isEmpty ||
+        uid is! num ||
+        uid.toInt() <= 0 ||
+        parsedExpiry == null ||
+        !parsedExpiry.isAfter(DateTime.now().toUtc()) ||
+        title is! String ||
+        title.trim().isEmpty ||
+        restaurantId is! num ||
+        restaurantId.toInt() <= 0) {
+      throw const FormatException('Invalid livestream join response');
+    }
+    return LivestreamJoinSession(
+      livestreamId: id,
+      channelName: channel.trim(),
+      token: token.trim(),
+      uid: uid.toInt(),
+      expiresAt: parsedExpiry,
+      title: title.trim(),
+      restaurantId: restaurantId.toInt(),
+    );
+  }
+}
+
+final _uuid = RegExp(
+  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$',
+);
diff --git a/lib/features/livestream/data/livestream_repository_impl.dart b/lib/features/livestream/data/livestream_repository_impl.dart
new file mode 100644
index 0000000..e650d7e
--- /dev/null
+++ b/lib/features/livestream/data/livestream_repository_impl.dart
@@ -0,0 +1,12 @@
+import '../domain/entities/livestream_join_session.dart';
+import '../domain/repositories/livestream_repository.dart';
+import 'livestream_gateway.dart';
+
+final class LivestreamRepositoryImpl implements LivestreamRepository {
+  const LivestreamRepositoryImpl(this._gateway);
+  final LivestreamGateway _gateway;
+
+  @override
+  Future<LivestreamJoinSession> join(String livestreamId) =>
+      _gateway.join(livestreamId);
+}
diff --git a/lib/features/livestream/domain/entities/livestream_join_session.dart b/lib/features/livestream/domain/entities/livestream_join_session.dart
new file mode 100644
index 0000000..6bbe676
--- /dev/null
+++ b/lib/features/livestream/domain/entities/livestream_join_session.dart
@@ -0,0 +1,19 @@
+final class LivestreamJoinSession {
+  const LivestreamJoinSession({
+    required this.livestreamId,
+    required this.channelName,
+    required this.token,
+    required this.uid,
+    required this.expiresAt,
+    required this.title,
+    required this.restaurantId,
+  });
+
+  final String livestreamId;
+  final String channelName;
+  final String token;
+  final int uid;
+  final DateTime expiresAt;
+  final String title;
+  final int restaurantId;
+}
diff --git a/lib/features/livestream/domain/repositories/livestream_repository.dart b/lib/features/livestream/domain/repositories/livestream_repository.dart
new file mode 100644
index 0000000..a4a00d2
--- /dev/null
+++ b/lib/features/livestream/domain/repositories/livestream_repository.dart
@@ -0,0 +1,5 @@
+import '../entities/livestream_join_session.dart';
+
+abstract interface class LivestreamRepository {
+  Future<LivestreamJoinSession> join(String livestreamId);
+}
diff --git a/lib/features/livestream/domain/usecases/join_livestream_use_case.dart b/lib/features/livestream/domain/usecases/join_livestream_use_case.dart
new file mode 100644
index 0000000..abb4c54
--- /dev/null
+++ b/lib/features/livestream/domain/usecases/join_livestream_use_case.dart
@@ -0,0 +1,12 @@
+import '../entities/livestream_join_session.dart';
+import '../repositories/livestream_repository.dart';
+
+final class JoinLivestreamUseCase {
+  const JoinLivestreamUseCase(this._repository);
+
+  final LivestreamRepository _repository;
+
+  Future<LivestreamJoinSession> call(String livestreamId) {
+    return _repository.join(livestreamId);
+  }
+}
diff --git a/lib/features/livestream/presentation/livestream_viewer_page.dart b/lib/features/livestream/presentation/livestream_viewer_page.dart
new file mode 100644
index 0000000..cc0a868
--- /dev/null
+++ b/lib/features/livestream/presentation/livestream_viewer_page.dart
@@ -0,0 +1,58 @@
+import 'dart:async';
+import 'package:flutter/material.dart';
+import 'package:flutter_riverpod/flutter_riverpod.dart';
+import '../application/livestream_viewer_view_model.dart';
+
+class LivestreamViewerPage extends ConsumerStatefulWidget {
+  const LivestreamViewerPage({super.key, required this.livestreamId});
+  final String livestreamId;
+  @override
+  ConsumerState<LivestreamViewerPage> createState() =>
+      _LivestreamViewerPageState();
+}
+
+class _LivestreamViewerPageState extends ConsumerState<LivestreamViewerPage> {
+  @override
+  void initState() {
+    super.initState();
+    WidgetsBinding.instance.addPostFrameCallback(
+      (_) => unawaited(
+        ref.read(livestreamViewerProvider(widget.livestreamId).notifier).join(),
+      ),
+    );
+  }
+
+  @override
+  Widget build(BuildContext context) {
+    final state = ref.watch(livestreamViewerProvider(widget.livestreamId));
+    void retry() => unawaited(
+      ref.read(livestreamViewerProvider(widget.livestreamId).notifier).join(),
+    );
+    return Scaffold(
+      appBar: AppBar(title: const Text('Livestream')),
+      body: Center(
+        child: switch (state.phase) {
+          LivestreamViewerPhase.disabled => const Text(
+            'Livestream is currently unavailable',
+          ),
+          LivestreamViewerPhase.loading => const CircularProgressIndicator(),
+          LivestreamViewerPhase.mediaUnavailable => Column(
+            mainAxisSize: MainAxisSize.min,
+            children: [
+              const Text('Media unavailable on this device'),
+              TextButton(onPressed: retry, child: const Text('Retry')),
+            ],
+          ),
+          LivestreamViewerPhase.error => Column(
+            mainAxisSize: MainAxisSize.min,
+            children: [
+              Text(state.message ?? 'Unable to join'),
+              TextButton(onPressed: retry, child: const Text('Retry')),
+            ],
+          ),
+          _ => Text(state.session?.title ?? 'Ready to join livestream'),
+        },
+      ),
+    );
+  }
+}
diff --git a/test/features/livestream/application/livestream_viewer_view_model_test.dart b/test/features/livestream/application/livestream_viewer_view_model_test.dart
new file mode 100644
index 0000000..5fbd345
--- /dev/null
+++ b/test/features/livestream/application/livestream_viewer_view_model_test.dart
@@ -0,0 +1,113 @@
+import 'package:delivery_app/features/livestream/application/livestream_viewer_view_model.dart';
+import 'package:delivery_app/features/livestream/domain/entities/livestream_join_session.dart';
+import 'package:delivery_app/features/livestream/domain/repositories/livestream_repository.dart';
+import 'package:flutter_riverpod/flutter_riverpod.dart';
+import 'package:flutter_test/flutter_test.dart';
+
+void main() {
+  const id = '00000000-0000-4000-8000-000000000001';
+
+  test('disabled capability does not attempt a media or repository join', () async {
+    final repository = _FakeRepository();
+    final media = _FakeMediaPort();
+    final container = ProviderContainer(overrides: [
+      livestreamEnabledProvider.overrideWithValue(false),
+      livestreamRepositoryProvider.overrideWithValue(repository),
+      livestreamMediaPortProvider.overrideWithValue(media),
+    ]);
+    addTearDown(container.dispose);
+
+    await container.read(livestreamViewerProvider(id).notifier).join();
+
+    expect(container.read(livestreamViewerProvider(id)).phase,
+        LivestreamViewerPhase.disabled);
+    expect(repository.joinCount, 0);
+    expect(media.joinCount, 0);
+  });
+
+  test('media SDK absence is exposed as recoverable mediaUnavailable state', () async {
+    final container = ProviderContainer(overrides: [
+      livestreamEnabledProvider.overrideWithValue(true),
+      livestreamRepositoryProvider.overrideWithValue(_FakeRepository()),
+      livestreamMediaPortProvider.overrideWithValue(
+        const UnsupportedLivestreamMediaPort(),
+      ),
+    ]);
+    addTearDown(container.dispose);
+
+    await container.read(livestreamViewerProvider(id).notifier).join();
+
+    final state = container.read(livestreamViewerProvider(id));
+    expect(state.phase, LivestreamViewerPhase.mediaUnavailable);
+    expect(state.session?.channelName, 'server-channel');
+  });
+
+  test('disposing the viewer leaves the media session', () async {
+    final media = _FakeMediaPort();
+    final container = ProviderContainer(overrides: [
+      livestreamEnabledProvider.overrideWithValue(true),
+      livestreamRepositoryProvider.overrideWithValue(_FakeRepository()),
+      livestreamMediaPortProvider.overrideWithValue(media),
+    ]);
+
+    await container.read(livestreamViewerProvider(id).notifier).join();
+    expect(media.joinCount, 1);
+    container.dispose();
+    await Future<void>.delayed(Duration.zero);
+
+    expect(media.leaveCount, 1);
+  });
+
+  test('repository format failures become retryable error state', () async {
+    final container = ProviderContainer(overrides: [
+      livestreamEnabledProvider.overrideWithValue(true),
+      livestreamRepositoryProvider.overrideWithValue(_FailingRepository()),
+      livestreamMediaPortProvider.overrideWithValue(_FakeMediaPort()),
+    ]);
+    addTearDown(container.dispose);
+
+    await container.read(livestreamViewerProvider(id).notifier).join();
+
+    final state = container.read(livestreamViewerProvider(id));
+    expect(state.phase, LivestreamViewerPhase.error);
+    expect(state.message, 'Livestream data is unavailable');
+  });
+}
+
+LivestreamJoinSession _session(String id) => LivestreamJoinSession(
+      livestreamId: id,
+      channelName: 'server-channel',
+      token: 'server-token',
+      uid: 501,
+      expiresAt: DateTime.utc(2099),
+      title: 'Live kitchen',
+      restaurantId: 42,
+    );
+
+final class _FakeRepository implements LivestreamRepository {
+  int joinCount = 0;
+
+  @override
+  Future<LivestreamJoinSession> join(String livestreamId) async {
+    joinCount++;
+    return _session(livestreamId);
+  }
+}
+
+final class _FailingRepository implements LivestreamRepository {
+  @override
+  Future<LivestreamJoinSession> join(String livestreamId) {
+    return Future.error(const FormatException('bad response'));
+  }
+}
+
+final class _FakeMediaPort implements LivestreamMediaPort {
+  int joinCount = 0;
+  int leaveCount = 0;
+
+  @override
+  Future<void> join(LivestreamJoinSession session) async => joinCount++;
+
+  @override
+  Future<void> leave() async => leaveCount++;
+}
diff --git a/test/features/livestream/data/livestream_gateway_test.dart b/test/features/livestream/data/livestream_gateway_test.dart
new file mode 100644
index 0000000..a014efb
--- /dev/null
+++ b/test/features/livestream/data/livestream_gateway_test.dart
@@ -0,0 +1,51 @@
+import 'package:delivery_app/features/livestream/data/livestream_gateway.dart';
+import 'package:dio/dio.dart';
+import 'package:flutter_test/flutter_test.dart';
+import 'package:http_mock_adapter/http_mock_adapter.dart';
+
+void main() {
+  test('parses only a server-issued UUID, token, channel, and expiry', () async {
+    final dio = Dio(BaseOptions(baseUrl: 'https://gateway.example.test/api'));
+    final adapter = DioAdapter(dio: dio);
+    const id = '00000000-0000-4000-8000-000000000001';
+    adapter.onPost('/livestreams/$id/join', (server) => server.reply(200, {
+          'status': 1,
+          'data': {
+            'livestreamId': id,
+            'channelName': 'server-channel',
+            'token': 'server-token',
+            'uid': 501,
+            'tokenExpiresAt': '2099-01-01T00:00:00Z',
+            'title': 'Live kitchen',
+            'restaurantId': 42,
+          },
+        }));
+
+    final session = await LivestreamGateway(dio).join(id);
+
+    expect(session.livestreamId, id);
+    expect(session.channelName, 'server-channel');
+    expect(session.token, 'server-token');
+    expect(session.uid, 501);
+  });
+
+  test('rejects a malformed or caller-mismatched join response', () async {
+    final dio = Dio(BaseOptions(baseUrl: 'https://gateway.example.test/api'));
+    final adapter = DioAdapter(dio: dio);
+    const id = '00000000-0000-4000-8000-000000000001';
+    adapter.onPost('/livestreams/$id/join', (server) => server.reply(200, {
+          'status': 1,
+          'data': {
+            'livestreamId': '00000000-0000-4000-8000-000000000002',
+            'channelName': 'server-channel',
+            'token': 'server-token',
+            'uid': 501,
+            'tokenExpiresAt': '2099-01-01T00:00:00Z',
+            'title': 'Live kitchen',
+            'restaurantId': 42,
+          },
+        }));
+
+    expect(() => LivestreamGateway(dio).join(id), throwsFormatException);
+  });
+}
diff --git a/test/features/livestream/domain/usecases/join_livestream_use_case_test.dart b/test/features/livestream/domain/usecases/join_livestream_use_case_test.dart
new file mode 100644
index 0000000..30b433e
--- /dev/null
+++ b/test/features/livestream/domain/usecases/join_livestream_use_case_test.dart
@@ -0,0 +1,34 @@
+import 'package:delivery_app/features/livestream/domain/entities/livestream_join_session.dart';
+import 'package:delivery_app/features/livestream/domain/repositories/livestream_repository.dart';
+import 'package:delivery_app/features/livestream/domain/usecases/join_livestream_use_case.dart';
+import 'package:flutter_test/flutter_test.dart';
+
+void main() {
+  test('joins only through the repository boundary', () async {
+    final repository = _FakeLivestreamRepository();
+    final useCase = JoinLivestreamUseCase(repository);
+
+    final session = await useCase('00000000-0000-4000-8000-000000000001');
+
+    expect(repository.joinedId, '00000000-0000-4000-8000-000000000001');
+    expect(session.channelName, 'server-channel');
+  });
+}
+
+final class _FakeLivestreamRepository implements LivestreamRepository {
+  String? joinedId;
+
+  @override
+  Future<LivestreamJoinSession> join(String livestreamId) async {
+    joinedId = livestreamId;
+    return LivestreamJoinSession(
+      livestreamId: livestreamId,
+      channelName: 'server-channel',
+      token: 'server-token',
+      uid: 10,
+      expiresAt: DateTime.utc(2027),
+      title: 'Server title',
+      restaurantId: 42,
+    );
+  }
+}

package com.delivery.livestream.domain;

/** Compatibility rules; adapters translate failures to the existing transport exceptions. */
public final class LivestreamPolicy {
    private LivestreamPolicy() { }
    public enum Failure { STATUS, PERMISSION, DUPLICATE }
    public static final class Rejection extends RuntimeException {
        private final Failure failure;
        public Rejection(Failure failure, String message) { super(message); this.failure = failure; }
        public Failure failure() { return failure; }
    }
    private static Rejection rejection(Failure failure, String message) { return new Rejection(failure, message); }
    public static void provider(String provider) {
        if (!"AGORA".equals(provider)) throw rejection(Failure.STATUS, "Chỉ hỗ trợ Agora");
    }
    public static void seller(Long owner, Long actor, boolean admin) {
        if (!admin && !owner.equals(actor)) throw rejection(Failure.PERMISSION, "Bạn không có quyền thao tác với livestream này");
    }
    public static void start(String status) {
        if (!"CREATED".equals(status)) throw rejection(Failure.STATUS, "Không thể bắt đầu livestream. Trạng thái hiện tại: " + status);
    }
    public static void end(String status) {
        if (!"LIVE".equals(status)) throw rejection(Failure.STATUS, "Không thể kết thúc livestream. Trạng thái hiện tại: " + status);
    }
    public static void join(String status) {
        if (!"LIVE".equals(status)) throw rejection(Failure.STATUS, "Livestream chưa bắt đầu hoặc đã kết thúc. Trạng thái: " + status);
    }
    public static void productScope(Long roomRestaurant, Long requestedRestaurant) {
        if (requestedRestaurant != null && !roomRestaurant.equals(requestedRestaurant))
            throw rejection(Failure.PERMISSION, "Sản phẩm không thuộc restaurant của livestream");
    }
    public static void productStatus(String status, String verb) {
        if (!"LIVE".equals(status) && !"CREATED".equals(status))
            throw rejection(Failure.STATUS, "Chỉ có thể " + verb + " sản phẩm khi livestream đang chuẩn bị hoặc đang diễn ra");
    }
    public static void duplicate(Boolean pinned) {
        if (Boolean.TRUE.equals(pinned)) throw rejection(Failure.DUPLICATE, "Sản phẩm đã được pin trong livestream");
    }
    public static long increment(Long count) { return (count != null ? count : 0L) + 1; }
    public static void token(String role, String status) {
        if ("VIEWER".equals(role) && !"LIVE".equals(status))
            throw rejection(Failure.STATUS, "Livestream chưa bắt đầu. Không thể tạo token cho viewer.");
    }
    public static int uid(Long user) { return user.intValue(); }
    public static int tokenTtl() { return 3600; }
    public static String renewalRole(Long user, Long seller, boolean admin, boolean shopOwner) {
        return user.equals(seller) && (admin || shopOwner) ? "HOST" : "VIEWER";
    }
    public static void renewalStatus(String status) {
        if (!"LIVE".equals(status)) throw rejection(Failure.STATUS, "Chỉ gia hạn token cho livestream đang phát");
    }
    public static boolean hostRequiresOwnership(Long user, boolean admin, boolean shopOwner) {
        if (user == null || (!admin && !shopOwner))
            throw rejection(Failure.PERMISSION, "ADMIN or SHOP_OWNER role is required");
        return !admin;
    }
    public static void moderator(Long principal, boolean admin) {
        if (principal == null || !admin) throw rejection(Failure.PERMISSION, "ADMIN role is required for moderation");
    }
    public static void checkoutRoom(String status, Long restaurant, Long requested, boolean context) {
        if (!"LIVE".equals(status)) throw rejection(Failure.STATUS, context ? "Checkout chỉ áp dụng khi phòng đang LIVE" : "Giá livestream chỉ áp dụng khi phòng đang LIVE");
        if (!requested.equals(restaurant)) throw rejection(Failure.PERMISSION, "Restaurant không thuộc livestream");
    }
    public static void completePins(int found, int requested) {
        if (found != requested) throw rejection(Failure.PERMISSION, "Một hoặc nhiều sản phẩm livestream không còn khả dụng");
    }
    public static void replay(String expected, String stored) {
        if (!expected.equals(stored)) throw new IllegalArgumentException("Idempotency key was already used for another checkout context");
    }
}

package com.delivery.analytics_service.repository;

import com.delivery.analytics_service.entity.DailyItemSales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface DailyItemSalesRepository extends JpaRepository<DailyItemSales, Long> {

    Optional<DailyItemSales> findByStatDateAndRestaurantIdAndMenuItemId(
            LocalDate statDate, Long restaurantId, Long menuItemId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO daily_item_sales (
                stat_date, restaurant_id, menu_item_id, menu_item_name,
                ordered_quantity, cancelled_quantity, ordered_revenue,
                cancelled_revenue, updated_at
            ) VALUES (
                :statDate, :restaurantId, :menuItemId, :menuItemName,
                :orderedQuantity, :cancelledQuantity, :orderedRevenue,
                :cancelledRevenue, :updatedAt
            ) ON CONFLICT (stat_date, restaurant_id, menu_item_id) DO UPDATE SET
                menu_item_name = EXCLUDED.menu_item_name,
                ordered_quantity = daily_item_sales.ordered_quantity + EXCLUDED.ordered_quantity,
                cancelled_quantity = daily_item_sales.cancelled_quantity + EXCLUDED.cancelled_quantity,
                ordered_revenue = daily_item_sales.ordered_revenue + EXCLUDED.ordered_revenue,
                cancelled_revenue = daily_item_sales.cancelled_revenue + EXCLUDED.cancelled_revenue,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int incrementPostgres(
            @Param("statDate") LocalDate statDate,
            @Param("restaurantId") Long restaurantId,
            @Param("menuItemId") Long menuItemId,
            @Param("menuItemName") String menuItemName,
            @Param("orderedQuantity") long orderedQuantity,
            @Param("cancelledQuantity") long cancelledQuantity,
            @Param("orderedRevenue") BigDecimal orderedRevenue,
            @Param("cancelledRevenue") BigDecimal cancelledRevenue,
            @Param("updatedAt") LocalDateTime updatedAt);
}

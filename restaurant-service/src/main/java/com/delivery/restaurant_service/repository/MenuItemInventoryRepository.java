package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.MenuItemInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuItemInventoryRepository extends JpaRepository<MenuItemInventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from MenuItemInventory inventory "
            + "where inventory.menuItemId in :ids order by inventory.menuItemId")
    List<MenuItemInventory> findAllByMenuItemIdInForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from MenuItemInventory inventory where inventory.menuItemId = :id")
    Optional<MenuItemInventory> findByMenuItemIdForUpdate(@Param("id") Long id);
}

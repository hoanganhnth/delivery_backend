package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.RestaurantTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface  RestaurantTransactionRepository extends JpaRepository<RestaurantTransaction, Long> {

}

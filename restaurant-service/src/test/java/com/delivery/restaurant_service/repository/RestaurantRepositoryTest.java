package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class RestaurantRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RestaurantRepository restaurantRepository;

    private Restaurant restaurant1;
    private Restaurant restaurant2;

    @BeforeEach
    void setUp() {
        restaurant1 = new Restaurant();
        restaurant1.setName("Pizza Palace");
        restaurant1.setAddress("123 Pizza Street");
        restaurant1.setPhone("0123456789");
        restaurant1.setCreatorId(1L);
        restaurant1 = entityManager.persistAndFlush(restaurant1);

        restaurant2 = new Restaurant();
        restaurant2.setName("Burger King");
        restaurant2.setAddress("456 Burger Avenue");
        restaurant2.setPhone("0987654321");
        restaurant2.setCreatorId(2L);
        restaurant2 = entityManager.persistAndFlush(restaurant2);
    }

    @Test
    void findAll_ShouldReturnAllRestaurants() {
        // When
        List<Restaurant> restaurants = restaurantRepository.findAll();

        // Then
        assertNotNull(restaurants);
        assertEquals(2, restaurants.size());
    }

    @Test
    void findById_ShouldReturnRestaurant_WhenExists() {
        // When
        Optional<Restaurant> found = restaurantRepository.findById(restaurant1.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("Pizza Palace", found.get().getName());
        assertEquals("123 Pizza Street", found.get().getAddress());
    }

    @Test
    void findById_ShouldReturnEmpty_WhenNotExists() {
        // When
        Optional<Restaurant> found = restaurantRepository.findById(999L);

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    void findByNameContainingIgnoreCase_ShouldReturnMatchingRestaurants() {
        // When
        List<Restaurant> results = restaurantRepository.findByNameContainingIgnoreCase("pizza");

        // Then
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Pizza Palace", results.get(0).getName());
    }

    @Test
    void findByNameContainingIgnoreCase_ShouldBeCaseInsensitive() {
        // When
        List<Restaurant> results = restaurantRepository.findByNameContainingIgnoreCase("PIZZA");

        // Then
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Pizza Palace", results.get(0).getName());
    }

    @Test
    void findByNameContainingIgnoreCase_ShouldReturnEmptyList_WhenNoMatch() {
        // When
        List<Restaurant> results = restaurantRepository.findByNameContainingIgnoreCase("NonExistent");

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void save_ShouldPersistNewRestaurant() {
        // Given
        Restaurant newRestaurant = new Restaurant();
        newRestaurant.setName("Sushi Master");
        newRestaurant.setAddress("789 Sushi Lane");
        newRestaurant.setPhone("0111222333");
        newRestaurant.setCreatorId(3L);

        // When
        Restaurant saved = restaurantRepository.save(newRestaurant);

        // Then
        assertNotNull(saved.getId());
        assertEquals("Sushi Master", saved.getName());

        // Verify in database
        Restaurant fromDb = entityManager.find(Restaurant.class, saved.getId());
        assertNotNull(fromDb);
        assertEquals("Sushi Master", fromDb.getName());
    }

    @Test
    void delete_ShouldRemoveRestaurant() {
        // Given
        Long restaurantId = restaurant1.getId();

        // When
        restaurantRepository.delete(restaurant1);
        entityManager.flush();

        // Then
        Restaurant fromDb = entityManager.find(Restaurant.class, restaurantId);
        assertNull(fromDb);
    }

    @Test
    void updateRestaurant_ShouldPersistChanges() {
        // Given
        restaurant1.setName("Updated Pizza Palace");
        restaurant1.setAddress("999 New Address");

        // When
        Restaurant updated = restaurantRepository.save(restaurant1);
        entityManager.flush();

        // Then
        assertEquals("Updated Pizza Palace", updated.getName());
        assertEquals("999 New Address", updated.getAddress());

        // Verify in database
        Restaurant fromDb = entityManager.find(Restaurant.class, restaurant1.getId());
        assertEquals("Updated Pizza Palace", fromDb.getName());
        assertEquals("999 New Address", fromDb.getAddress());
    }

    // Test với owner relationships
    @Test
    void findByOwnerId_ShouldReturnRestaurantsForOwner() {
        // Giả sử bạn có method này
        // List<Restaurant> restaurants = restaurantRepository.findByOwnerId(1L);

        // Then
        // assertNotNull(restaurants);
        // assertEquals(1, restaurants.size());
        // assertEquals("Pizza Palace", restaurants.get(0).getName());
    }
}
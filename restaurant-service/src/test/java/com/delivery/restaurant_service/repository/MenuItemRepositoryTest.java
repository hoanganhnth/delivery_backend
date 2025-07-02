package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.MenuItem;
import com.delivery.restaurant_service.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class MenuItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MenuItemRepository menuItemRepository;

    private Restaurant restaurant;
    private MenuItem menuItem1;
    private MenuItem menuItem2;

    @BeforeEach
    void setUp() {
        // Tạo Restaurant
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test Street");
        restaurant.setCreatorId(1L);
        restaurant = entityManager.persistAndFlush(restaurant);

        // Tạo MenuItem available
        menuItem1 = new MenuItem();
        menuItem1.setName("Available Pizza");
        menuItem1.setDescription("Delicious pizza");
        menuItem1.setPrice(BigDecimal.valueOf(25.99));
        menuItem1.setStatus(MenuItem.Status.AVAILABLE);
        menuItem1.setRestaurant(restaurant);
        menuItem1 = entityManager.persistAndFlush(menuItem1);

        // Tạo MenuItem not available
        menuItem2 = new MenuItem();
        menuItem2.setName("Unavailable Burger");
        menuItem2.setDescription("Out of stock burger");
        menuItem2.setPrice(BigDecimal.valueOf(15.99));
        menuItem2.setStatus(MenuItem.Status.SOLD_OUT);
        menuItem2.setRestaurant(restaurant);
        menuItem2 = entityManager.persistAndFlush(menuItem2);
    }

    @Test
    void findByRestaurantId_ShouldReturnAllMenuItems_WhenRestaurantExists() {
        // When
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurant.getId());

        // Then
        assertNotNull(menuItems);
        assertEquals(2, menuItems.size());
        assertTrue(menuItems.stream().anyMatch(item -> item.getName().equals("Available Pizza")));
        assertTrue(menuItems.stream().anyMatch(item -> item.getName().equals("Unavailable Burger")));
    }

    @Test
    void findByRestaurantId_ShouldReturnEmptyList_WhenRestaurantNotExists() {
        // When
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(999L);

        // Then
        assertNotNull(menuItems);
        assertTrue(menuItems.isEmpty());
    }

    @Test
    void findByRestaurantIdAndAvailableTrue_ShouldReturnOnlyAvailableItems() {
        // When
        List<MenuItem> availableItems = menuItemRepository.findByRestaurantIdAndStatus(restaurant.getId(), MenuItem.Status.AVAILABLE);

        // Then
        assertNotNull(availableItems);
        assertEquals(1, availableItems.size());
        assertEquals("Available Pizza", availableItems.get(0).getName());
        assertSame(availableItems.get(0).getStatus(), MenuItem.Status.AVAILABLE);
    }

    @Test
    void findByRestaurantIdAndAvailableTrue_ShouldReturnEmptyList_WhenNoAvailableItems() {
        // Given - Set all items to unavailable
        menuItem1.setStatus(MenuItem.Status.SOLD_OUT);
        entityManager.persistAndFlush(menuItem1);

        // When
        List<MenuItem> availableItems = menuItemRepository.findByRestaurantIdAndStatus(restaurant.getId(), MenuItem.Status.AVAILABLE);

        // Then
        assertNotNull(availableItems);
        assertTrue(availableItems.isEmpty());
    }

    @Test
    void findById_ShouldReturnMenuItem_WhenExists() {
        // When
        Optional<MenuItem> found = menuItemRepository.findById(menuItem1.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals("Available Pizza", found.get().getName());
        assertEquals(BigDecimal.valueOf(25.99), found.get().getPrice());
    }

    @Test
    void save_ShouldPersistNewMenuItem() {
        // Given
        MenuItem newItem = new MenuItem();
        newItem.setName("New Pasta");
        newItem.setDescription("Fresh pasta");
        newItem.setPrice(BigDecimal.valueOf(18.50));
        newItem.setStatus(MenuItem.Status.AVAILABLE);
        newItem.setRestaurant(restaurant);

        // When
        MenuItem saved = menuItemRepository.save(newItem);

        // Then
        assertNotNull(saved.getId());
        assertEquals("New Pasta", saved.getName());
        assertEquals(BigDecimal.valueOf(18.50), saved.getPrice());

        // Verify in database
        MenuItem fromDb = entityManager.find(MenuItem.class, saved.getId());
        assertNotNull(fromDb);
        assertEquals("New Pasta", fromDb.getName());
    }

    @Test
    void delete_ShouldRemoveMenuItem() {
        // Given
        Long menuItemId = menuItem1.getId();

        // When
        menuItemRepository.delete(menuItem1);
        entityManager.flush();

        // Then
        MenuItem fromDb = entityManager.find(MenuItem.class, menuItemId);
        assertNull(fromDb);
    }

    @Test
    void updateMenuItem_ShouldPersistChanges() {
        // Given
        menuItem1.setName("Updated Pizza Name");
        menuItem1.setPrice(BigDecimal.valueOf(35.99));

        // When
        MenuItem updated = menuItemRepository.save(menuItem1);
        entityManager.flush();

        // Then
        assertEquals("Updated Pizza Name", updated.getName());
        assertEquals(BigDecimal.valueOf(35.99), updated.getPrice());

        // Verify in database
        MenuItem fromDb = entityManager.find(MenuItem.class, menuItem1.getId());
        assertEquals("Updated Pizza Name", fromDb.getName());
        assertEquals(BigDecimal.valueOf(35.99), fromDb.getPrice());
    }

    // Test custom query methods nếu có
    @Test
    void findByNameContainingIgnoreCase_ShouldReturnMatchingItems() {
        // Giả sử bạn có method này trong repository
        // List<MenuItem> items = menuItemRepository.findByNameContainingIgnoreCase("pizza");

        // Then
        // assertNotNull(items);
        // assertEquals(1, items.size());
        // assertEquals("Available Pizza", items.get(0).getName());
    }
}
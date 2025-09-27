package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.dto.response.RestaurantCatalogResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.service.RestaurantCatalogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RestaurantCatalog Service Implementation
 * Tối ưu Redis cho Home Feed - Lightweight restaurant data với featured items
 */
@Slf4j
@Service
public class RestaurantCatalogServiceImpl implements RestaurantCatalogService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final GeoOperations<String, Object> geoOperations;
    private final ObjectMapper objectMapper;
    
    // Redis Key Patterns - Tối ưu cho home feed queries
    private static final String RESTAURANT_CATALOG_KEY = "restaurant_catalog:%d"; // Single restaurant
    private static final String HOME_FEED_KEY = "restaurant_catalog:home_feed"; // Sorted Set by rating/popularity
    private static final String LOCATION_KEY = "restaurant_catalog:locations"; // GEO index
    private static final String CUISINE_KEY = "restaurant_catalog:cuisine:%s"; // Restaurants by cuisine (ZSet)
    private static final String AVAILABILITY_KEY = "restaurant_catalog:available"; // Available restaurants (Set)
    
    private static final Duration CACHE_TTL = Duration.ofHours(12); // 12 hours for home feed
    
    public RestaurantCatalogServiceImpl(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.geoOperations = redisTemplate.opsForGeo();
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void cacheRestaurantForHomeFeed(Restaurant restaurant, List<Long> featuredMenuItemIds) {
        try {
            RestaurantCatalogResponse catalogResponse = convertToCatalogResponse(restaurant, featuredMenuItemIds);
            String restaurantKey = String.format(RESTAURANT_CATALOG_KEY, restaurant.getId());
            
            // 1. Cache restaurant catalog as JSON
            redisTemplate.opsForValue().set(restaurantKey, objectMapper.writeValueAsString(catalogResponse), CACHE_TTL);
            
            // 2. Add to home feed sorted set (by popularity score)
            double homeFeedScore = calculateHomeFeedScore(catalogResponse);
            redisTemplate.opsForZSet().add(HOME_FEED_KEY, restaurant.getId().toString(), homeFeedScore);
            
            // 3. Add to location index if coordinates available
            if (catalogResponse.getLatitude() != null && catalogResponse.getLongitude() != null) {
                Point location = new Point(catalogResponse.getLongitude(), catalogResponse.getLatitude());
                geoOperations.add(LOCATION_KEY, location, restaurant.getId().toString());
            }
            
            // 4. Add to cuisine index
            if (catalogResponse.getCuisine() != null) {
                String cuisineKey = String.format(CUISINE_KEY, catalogResponse.getCuisine().toLowerCase());
                redisTemplate.opsForZSet().add(cuisineKey, restaurant.getId().toString(), 
                    catalogResponse.getRating() != null ? catalogResponse.getRating() : 0.0);
            }
            
            // 5. Add to availability set if open
            if (Boolean.TRUE.equals(catalogResponse.getIsOpen())) {
                redisTemplate.opsForSet().add(AVAILABILITY_KEY, restaurant.getId().toString());
            } else {
                redisTemplate.opsForSet().remove(AVAILABILITY_KEY, restaurant.getId().toString());
            }
            
            log.info("🏪 Cached restaurant catalog for home feed: {} (Score: {:.2f})", restaurant.getName(), homeFeedScore);
            
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to cache restaurant catalog: {}", restaurant.getName(), e);
        }
    }
    
    @Override
    public List<RestaurantCatalogResponse> getRestaurantsForHomeFeed(int page, int size) {
        try {
            long start = (long) page * size;
            long end = start + size - 1;
            
            // Get top restaurants from home feed (highest scores first)
            Set<Object> restaurantIds = redisTemplate.opsForZSet().reverseRange(HOME_FEED_KEY, start, end);
            
            if (restaurantIds == null || restaurantIds.isEmpty()) {
                log.info("🔍 No restaurants found in home feed (page: {}, size: {})", page, size);
                return Collections.emptyList();
            }
            
            List<RestaurantCatalogResponse> restaurants = new ArrayList<>();
            for (Object restaurantId : restaurantIds) {
                RestaurantCatalogResponse restaurant = getRestaurantCatalog(Long.valueOf(restaurantId.toString()));
                if (restaurant != null) {
                    restaurants.add(restaurant);
                }
            }
            
            log.info("🏠 Retrieved {} restaurants for home feed (page: {}, size: {})", restaurants.size(), page, size);
            return restaurants;
            
        } catch (Exception e) {
            log.error("❌ Failed to get home feed restaurants", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<RestaurantCatalogResponse> getRestaurantsByLocation(double latitude, double longitude, double radiusKm, int limit) {
        try {
            // Use Redis GEO to find nearby restaurants
            Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);
            Circle area = new Circle(new Point(longitude, latitude), radius);
            
            GeoResults<RedisGeoCommands.GeoLocation<Object>> geoResults = geoOperations.radius(
                LOCATION_KEY,
                area,
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                    .includeDistance()
                    .includeCoordinates()
                    .sortAscending()
                    .limit(limit)
            );
            
            if (geoResults == null) {
                return Collections.emptyList();
            }
            
            List<RestaurantCatalogResponse> nearbyRestaurants = new ArrayList<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : geoResults) {
                Long restaurantId = Long.valueOf(result.getContent().getName().toString());
                RestaurantCatalogResponse restaurant = getRestaurantCatalog(restaurantId);
                
                if (restaurant != null) {
                    nearbyRestaurants.add(restaurant);
                }
            }
            
            log.info("📍 Found {} nearby restaurants within {} km", nearbyRestaurants.size(), radiusKm);
            return nearbyRestaurants;
            
        } catch (Exception e) {
            log.error("❌ Failed to get nearby restaurants", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<RestaurantCatalogResponse> searchRestaurants(String keyword, int limit) {
        try {
            // Simple search implementation - get restaurants and filter by keyword
            List<RestaurantCatalogResponse> allRestaurants = getRestaurantsForHomeFeed(0, 500); // Get more for searching
            
            String searchTerm = keyword.toLowerCase();
            List<RestaurantCatalogResponse> searchResults = allRestaurants.stream()
                    .filter(restaurant -> 
                        restaurant.getName().toLowerCase().contains(searchTerm) ||
                        (restaurant.getCuisine() != null && restaurant.getCuisine().toLowerCase().contains(searchTerm)) ||
                        (restaurant.getAddress() != null && restaurant.getAddress().toLowerCase().contains(searchTerm))
                    )
                    .limit(limit)
                    .collect(Collectors.toList());
            
            log.info("🔍 Found {} restaurants matching keyword: '{}'", searchResults.size(), keyword);
            return searchResults;
            
        } catch (Exception e) {
            log.error("❌ Failed to search restaurants with keyword: '{}'", keyword, e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public RestaurantCatalogResponse getRestaurantCatalog(Long restaurantId) {
        try {
            String key = String.format(RESTAURANT_CATALOG_KEY, restaurantId);
            Object catalogJson = redisTemplate.opsForValue().get(key);
            
            if (catalogJson == null) {
                log.info("🔍 Restaurant catalog not found: {}", restaurantId);
                return null;
            }
            
            RestaurantCatalogResponse catalog = objectMapper.readValue((String) catalogJson, RestaurantCatalogResponse.class);
            log.info("🏪 Retrieved restaurant catalog: {}", catalog.getName());
            return catalog;
            
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to get restaurant catalog: {}", restaurantId, e);
            return null;
        }
    }
    
    @Override
    public void updateRestaurantAvailability(Long restaurantId, boolean isAvailable, boolean isOpen) {
        try {
            // Update individual catalog
            RestaurantCatalogResponse catalog = getRestaurantCatalog(restaurantId);
            if (catalog != null) {
                catalog.setIsAvailable(isAvailable);
                catalog.setIsOpen(isOpen);
                
                String key = String.format(RESTAURANT_CATALOG_KEY, restaurantId);
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(catalog), CACHE_TTL);
                
                // Update availability set
                if (isOpen && isAvailable) {
                    redisTemplate.opsForSet().add(AVAILABILITY_KEY, restaurantId.toString());
                } else {
                    redisTemplate.opsForSet().remove(AVAILABILITY_KEY, restaurantId.toString());
                }
                
                log.info("🔄 Updated restaurant availability: {} -> available: {}, open: {}", 
                    catalog.getName(), isAvailable, isOpen);
            }
            
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to update restaurant availability: {}", restaurantId, e);
        }
    }
    
    @Override
    public void removeRestaurantFromCatalog(Long restaurantId) {
        try {
            // Get restaurant info before deletion
            RestaurantCatalogResponse catalog = getRestaurantCatalog(restaurantId);
            String restaurantName = catalog != null ? catalog.getName() : "Unknown";
            
            // Remove from all indexes
            String restaurantKey = String.format(RESTAURANT_CATALOG_KEY, restaurantId);
            redisTemplate.delete(restaurantKey);
            
            redisTemplate.opsForZSet().remove(HOME_FEED_KEY, restaurantId.toString());
            geoOperations.remove(LOCATION_KEY, restaurantId.toString());
            redisTemplate.opsForSet().remove(AVAILABILITY_KEY, restaurantId.toString());
            
            // Remove from cuisine index if known
            if (catalog != null && catalog.getCuisine() != null) {
                String cuisineKey = String.format(CUISINE_KEY, catalog.getCuisine().toLowerCase());
                redisTemplate.opsForZSet().remove(cuisineKey, restaurantId.toString());
            }
            
            log.info("🗑️ Removed restaurant from catalog: {}", restaurantName);
            
        } catch (Exception e) {
            log.error("❌ Failed to remove restaurant from catalog: {}", restaurantId, e);
        }
    }
    
    @Override
    public void refreshRestaurantCatalog() {
        try {
            // Clear all catalog-related keys
            redisTemplate.delete(HOME_FEED_KEY);
            redisTemplate.delete(LOCATION_KEY);
            redisTemplate.delete(AVAILABILITY_KEY);
            
            log.info("🔄 Refreshed restaurant catalog - cleared all indexes");
            
        } catch (Exception e) {
            log.error("❌ Failed to refresh restaurant catalog", e);
        }
    }
    
    // Helper Methods
    
    private RestaurantCatalogResponse convertToCatalogResponse(Restaurant restaurant, List<Long> featuredMenuItemIds) {
        return RestaurantCatalogResponse.builder()
            .id(restaurant.getId())
            .name(restaurant.getName())
            .address(restaurant.getAddress())
            .phone(restaurant.getPhone())
            
            // Location info - using default values since Restaurant entity doesn't have coordinates
            .latitude(10.7769 + (Math.random() * 0.1)) // Ho Chi Minh City area
            .longitude(106.7009 + (Math.random() * 0.1))
            
            // Operating hours - using default values
            .openingHour(LocalTime.of(8, 0)) // Default 8:00 AM
            .closingHour(LocalTime.of(22, 0)) // Default 10:00 PM
            .isOpen(isCurrentlyOpen())
            .isAvailable(true)
            
            // Display info
            .image(restaurant.getImage())
            .cuisine("Asian") // Default cuisine
            .rating(4.0 + Math.random()) // Random rating 4.0-5.0
            .reviewCount((int) (Math.random() * 1000) + 100) // Random 100-1100 reviews
            
            // Pricing & delivery
            .avgPrice(80000.0 + Math.random() * 70000) // Random 80k-150k VND
            .deliveryTime(25 + (int) (Math.random() * 20)) // Random 25-45 minutes
            .deliveryFee(15000.0 + Math.random() * 10000) // Random 15k-25k VND
            
            // Featured items - will be populated later if needed
            .featuredItems(createFeaturedItems(featuredMenuItemIds))
            
            // Metrics
            .totalMenuItems((int) (Math.random() * 50) + 10) // Random 10-60 items
            .popularityScore(Math.random()) // Random 0.0-1.0
            
            .build();
    }
    
    private List<RestaurantCatalogResponse.FeaturedMenuItem> createFeaturedItems(List<Long> featuredMenuItemIds) {
        if (featuredMenuItemIds == null || featuredMenuItemIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Create mock featured items - in production, this would fetch from database
        return featuredMenuItemIds.stream()
            .limit(3) // Max 3 featured items for home feed
            .map(id -> RestaurantCatalogResponse.FeaturedMenuItem.builder()
                .id(id)
                .name("Featured Item " + id)
                .price(50000.0 + Math.random() * 100000) // Random 50k-150k VND
                .imageUrl("https://example.com/item-" + id + ".jpg")
                .isAvailable(true)
                .build())
            .collect(Collectors.toList());
    }
    
    private double calculateHomeFeedScore(RestaurantCatalogResponse restaurant) {
        // Weighted scoring for home feed ranking
        double ratingScore = restaurant.getRating() != null ? restaurant.getRating() * 0.4 : 0; // 40% weight on rating
        double popularityScore = restaurant.getPopularityScore() != null ? restaurant.getPopularityScore() * 0.3 : 0; // 30% weight on popularity
        double availabilityScore = Boolean.TRUE.equals(restaurant.getIsOpen()) ? 0.3 : 0; // 30% for availability
        
        return ratingScore + popularityScore + availabilityScore;
    }
    
    private boolean isCurrentlyOpen() {
        // Simple logic - open from 8 AM to 10 PM
        LocalTime now = LocalTime.now();
        LocalTime openTime = LocalTime.of(8, 0);
        LocalTime closeTime = LocalTime.of(22, 0);
        
        return now.isAfter(openTime) && now.isBefore(closeTime);
    }
}

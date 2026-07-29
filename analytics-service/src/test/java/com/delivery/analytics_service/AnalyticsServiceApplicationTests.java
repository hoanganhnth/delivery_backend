package com.delivery.analytics_service;

import com.delivery.analytics_service.controller.DashboardController;
import com.delivery.analytics_service.listener.OrderEventListener;
import com.delivery.analytics_service.listener.PaymentEventListener;
import com.delivery.analytics_service.scheduler.StatsReconciliationJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import com.delivery.analytics_service.service.EventProcessingService;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:analytics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"spring.kafka.listener.auto-startup=false"
})
class AnalyticsServiceApplicationTests {

	@Autowired
	private ApplicationContext context;
	@Autowired
	private EventProcessingService eventProcessingService;
	@Autowired
	private AnalyticsEventRepository eventRepository;
	@Autowired
	private DailyOrderStatsRepository orderStatsRepository;
	@Autowired
	private DailyRevenueStatsRepository revenueStatsRepository;

	@Test
	void contextLoads() {
		assertThat(context.getBeansOfType(DashboardController.class)).isEmpty();
		assertThat(context.getBeansOfType(OrderEventListener.class)).isEmpty();
		assertThat(context.getBeansOfType(PaymentEventListener.class)).isEmpty();
		assertThat(context.getBeansOfType(StatsReconciliationJob.class)).isEmpty();
	}

	@Test
	void duplicateOrderEventIsPersistedAndAggregatedOnce() {
		revenueStatsRepository.deleteAll();
		orderStatsRepository.deleteAll();
		eventRepository.deleteAll();
		String payload = "{\"eventId\":\"evt-integration-1\",\"orderId\":901}";

		eventProcessingService.processOrderCreated(
				901L, 11L, 21L, "Restaurant", new BigDecimal("125000"), "COD", payload);
		eventProcessingService.processOrderCreated(
				901L, 11L, 21L, "Restaurant", new BigDecimal("125000"), "COD", payload);

		assertThat(eventRepository.count()).isEqualTo(1);
		var platform = orderStatsRepository.findByStatDateAndRestaurantIdIsNull(LocalDate.now())
				.orElseThrow();
		var restaurant = orderStatsRepository.findByStatDateAndRestaurantId(LocalDate.now(), 21L)
				.orElseThrow();
		assertThat(platform.getTotalOrders()).isEqualTo(1);
		assertThat(platform.getPendingOrders()).isEqualTo(1);
		assertThat(restaurant.getTotalOrders()).isEqualTo(1);
		assertThat(restaurant.getPendingOrders()).isEqualTo(1);
	}

}

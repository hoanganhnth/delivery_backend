package com.delivery.livestream_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import com.delivery.livestream_service.controller.LivestreamController;
import com.delivery.livestream_service.controller.LivestreamProductController;
import com.delivery.livestream_service.controller.StreamTokenController;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:livestream;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false"
})
class LivestreamServiceApplicationTests {
	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getBeansOfType(LivestreamController.class)).isEmpty();
		assertThat(context.getBeansOfType(LivestreamProductController.class)).isEmpty();
		assertThat(context.getBeansOfType(StreamTokenController.class)).isEmpty();
	}

}

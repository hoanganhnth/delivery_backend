package com.delivery.user_service.config;

import com.delivery.user.application.DefaultUserAddressUseCase;
import com.delivery.user.application.DefaultUserBlockStatusUseCase;
import com.delivery.user.application.DefaultUserProfileReadUseCase;
import com.delivery.user.application.DefaultUserProfileUseCase;
import com.delivery.user.application.api.UserAddressPort;
import com.delivery.user.application.api.UserAddressUseCase;
import com.delivery.user.application.api.UserBlockStatusPort;
import com.delivery.user.application.api.UserBlockStatusUseCase;
import com.delivery.user.application.api.UserProfilePort;
import com.delivery.user.application.api.UserProfileReadPort;
import com.delivery.user.application.api.UserProfileReadUseCase;
import com.delivery.user.application.api.UserProfileUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires framework-free user application use cases to infrastructure ports. */
@Configuration
public class UserApplicationConfiguration {

    @Bean
    UserProfileUseCase userProfileUseCase(UserProfilePort profilePort) {
        return new DefaultUserProfileUseCase(profilePort);
    }

    @Bean
    UserProfileReadUseCase userProfileReadUseCase(UserProfileReadPort profileReadPort) {
        return new DefaultUserProfileReadUseCase(profileReadPort);
    }

    @Bean
    UserBlockStatusUseCase userBlockStatusUseCase(UserBlockStatusPort blockStatusPort) {
        return new DefaultUserBlockStatusUseCase(blockStatusPort);
    }

    @Bean
    UserAddressUseCase userAddressUseCase(UserAddressPort addressPort) {
        return new DefaultUserAddressUseCase(addressPort);
    }
}

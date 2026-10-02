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
    com.delivery.user.application.api.UserRegistrationUseCase userRegistrationUseCase(
            com.delivery.user.application.api.ProvisioningIdentityPort identities, UserProfileUseCase profiles) {
        return new com.delivery.user.application.DefaultUserRegistrationUseCase(identities, profiles);
    }


    @Bean
    UserProfileUseCase userProfileUseCase(UserProfilePort profilePort) {
        return new DefaultUserProfileUseCase(profilePort);
    }

    @Bean
    UserProfileReadUseCase userProfileReadUseCase(UserProfileReadPort profileReadPort) {
        return new DefaultUserProfileReadUseCase(profileReadPort);
    }

    @Bean
    com.delivery.user.application.api.UserIdentityStatusUseCase userIdentityStatusUseCase(
            com.delivery.user.application.api.UserIdentityStatusPort statuses) {
        return new com.delivery.user.application.DefaultUserIdentityStatusUseCase(statuses);
    }

    @Bean
    UserBlockStatusUseCase userBlockStatusUseCase(UserBlockStatusPort blockStatusPort) {
        return new DefaultUserBlockStatusUseCase(blockStatusPort);
    }

    @Bean
    com.delivery.user.application.api.UserAddressAccessUseCase userAddressAccessUseCase(UserProfileReadUseCase profiles) {
        return new com.delivery.user.application.DefaultUserAddressAccessUseCase(profiles);
    }

    @Bean
    UserAddressUseCase userAddressUseCase(UserAddressPort addressPort) {
        return new DefaultUserAddressUseCase(addressPort);
    }
}

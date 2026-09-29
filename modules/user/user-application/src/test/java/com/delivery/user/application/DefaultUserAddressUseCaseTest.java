package com.delivery.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.user.application.api.CreateUserAddressCommand;
import com.delivery.user.application.api.UpdateUserAddressCommand;
import com.delivery.user.application.api.UserAddressPort;
import com.delivery.user.application.api.UserAddressResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultUserAddressUseCaseTest {

    private final RecordingAddressPort port = new RecordingAddressPort();
    private final DefaultUserAddressUseCase useCase = new DefaultUserAddressUseCase(port);

    @Test
    void delegatesAddressOperationsToThePersistenceBoundary() {
        UserAddressResult result = new UserAddressResult(
                5L, 7L, "Home", "Customer", "0900000000", "Address", "Ward",
                "District", "City", null, null, null, true, null, null);
        CreateUserAddressCommand create = new CreateUserAddressCommand(
                7L, "Home", "Customer", "0900000000", "Address", "Ward",
                "District", "City", null, null, null, true);
        UpdateUserAddressCommand update = new UpdateUserAddressCommand(
                5L, "Home", "Customer", "0900000000", "Address", "Ward",
                "District", "City", null, null, null, true);
        port.result = result;
        port.results = List.of(result);

        assertThat(useCase.byUserId(7L)).containsExactly(result);
        assertThat(useCase.byId(5L)).isSameAs(result);
        assertThat(useCase.create(create)).isSameAs(result);
        assertThat(useCase.update(update)).isSameAs(result);
        assertThat(useCase.setDefault(5L)).isSameAs(result);
        useCase.delete(5L);
        assertThat(port.userId).isEqualTo(7L);
        assertThat(port.id).isEqualTo(5L);
        assertThat(port.created).isSameAs(create);
        assertThat(port.updated).isSameAs(update);
        assertThat(port.deleted).isTrue();
    }

    @Test
    void rejectsMissingInputsBeforeCallingThePort() {
        assertThatThrownBy(() -> useCase.byUserId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userId");
        assertThatThrownBy(() -> useCase.byId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
        assertThatThrownBy(() -> useCase.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
        assertThatThrownBy(() -> useCase.update(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
        assertThatThrownBy(() -> useCase.delete(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
        assertThatThrownBy(() -> useCase.setDefault(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
    }

    private static final class RecordingAddressPort implements UserAddressPort {
        private Long userId;
        private Long id;
        private CreateUserAddressCommand created;
        private UpdateUserAddressCommand updated;
        private UserAddressResult result;
        private List<UserAddressResult> results;
        private boolean deleted;

        @Override
        public List<UserAddressResult> byUserId(Long userId) {
            this.userId = userId;
            return results;
        }

        @Override
        public UserAddressResult byId(Long id) {
            this.id = id;
            return result;
        }

        @Override
        public UserAddressResult create(CreateUserAddressCommand command) {
            this.created = command;
            return result;
        }

        @Override
        public UserAddressResult update(UpdateUserAddressCommand command) {
            this.updated = command;
            return result;
        }

        @Override
        public void delete(Long id) {
            this.id = id;
            this.deleted = true;
        }

        @Override
        public UserAddressResult setDefault(Long id) {
            this.id = id;
            return result;
        }
    }
}

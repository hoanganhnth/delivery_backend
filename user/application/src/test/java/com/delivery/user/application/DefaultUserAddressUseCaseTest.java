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
    void choosingDefaultClearsOtherAddressesInsideTheOwnerLock() {
        port.result = new UserAddressResult(5L, 7L, null, null, null, null, null,
                null, null, null, null, null, false, null, null);
        useCase.setDefault(5L);
        assertThat(port.defaultsCleared).isEqualTo(1);
        assertThat(port.lockedOwner).isEqualTo(7L);
    }

    @Test
    void createDefaultSelectionAndUpdateNullSemanticsArePreserved() {
        port.result = result(true);
        useCase.create(create(null));
        assertThat(port.savedDefault).isFalse();
        assertThat(port.defaultsCleared).isZero();
        useCase.create(create(false));
        assertThat(port.defaultsCleared).isZero();
        useCase.create(create(true));
        assertThat(port.savedDefault).isTrue();
        assertThat(port.defaultsCleared).isEqualTo(1);
        useCase.update(update(null));
        assertThat(port.savedDefault).isTrue();
        assertThat(port.defaultsCleared).isEqualTo(1);
        useCase.update(update(false));
        assertThat(port.savedDefault).isFalse();
        assertThat(port.defaultsCleared).isEqualTo(1);
        useCase.update(update(true));
        assertThat(port.defaultsCleared).isEqualTo(2);
        port.result = result(null);
        useCase.update(update(null));
        assertThat(port.savedDefault).isNull();
    }

    @Test
    void deletingDefaultPromotesLatestRemainingAddressAndOtherDeletesDoNot() {
        port.result = result(true);
        port.latest = java.util.Optional.of(new UserAddressResult(6L, 7L, null, null, null,
                null, null, null, null, null, null, null, false, null, null));
        useCase.delete(5L);
        assertThat(port.id).isEqualTo(6L);
        assertThat(port.defaultWrites).isEqualTo(1);
        port.result = result(false);
        useCase.delete(5L);
        assertThat(port.defaultWrites).isEqualTo(1);
        port.result = result(true);
        port.latest = java.util.Optional.empty();
        useCase.delete(5L);
        assertThat(port.defaultWrites).isEqualTo(1);
    }

    private UserAddressResult result(Boolean isDefault) {
        return new UserAddressResult(5L, 7L, null, null, null, null, null,
                null, null, null, null, null, isDefault, null, null);
    }

    private CreateUserAddressCommand create(Boolean isDefault) {
        return new CreateUserAddressCommand(7L, null, null, null, null, null,
                null, null, null, null, null, isDefault);
    }

    private UpdateUserAddressCommand update(Boolean isDefault) {
        return new UpdateUserAddressCommand(5L, null, null, null, null, null,
                null, null, null, null, null, isDefault);
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
        private int defaultsCleared;
        private Long lockedOwner;
        private Boolean savedDefault;
        private int defaultWrites;
        private boolean locked;
        private java.util.Optional<UserAddressResult> latest = java.util.Optional.empty();

        @Override
        public <T> T withOwnerLock(Long userId, java.util.function.Supplier<T> operation) {
            lockedOwner = userId;
            locked = true;
            try { return operation.get(); } finally { locked = false; }
        }

        @Override
        public void clearDefaults(Long userId, Long exceptId) {
            assertThat(locked).isTrue();
            defaultsCleared++;
        }

        @Override
        public java.util.Optional<UserAddressResult> latestByUserId(Long userId) {
            assertThat(locked).isTrue();
            return latest;
        }

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
        public UserAddressResult create(CreateUserAddressCommand command, boolean isDefault) {
            assertThat(locked).isTrue();
            savedDefault = isDefault;
            this.created = command;
            return result;
        }

        @Override
        public UserAddressResult update(UpdateUserAddressCommand command, Boolean isDefault) {
            assertThat(locked).isTrue();
            savedDefault = isDefault;
            this.updated = command;
            return result;
        }

        @Override
        public void delete(Long id) {
            this.id = id;
            assertThat(locked).isTrue();
            this.deleted = true;
        }

        @Override
        public UserAddressResult setDefault(Long id) {
            assertThat(locked).isTrue();
            defaultWrites++;
            this.id = id;
            return result;
        }
    }
}

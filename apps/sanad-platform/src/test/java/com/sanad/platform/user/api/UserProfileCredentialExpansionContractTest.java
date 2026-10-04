package com.sanad.platform.user.api;

import com.sanad.platform.user.dto.CreateUserRequest;
import com.sanad.platform.user.dto.UpdateUserRequest;
import com.sanad.platform.user.dto.UserResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileCredentialExpansionContractTest {

    @Test
    void userResponseExposesOnlySafeProfileAndCredentialMetadata() {
        var methods = Arrays.stream(UserResponse.class.getMethods())
                .map(method -> method.getName())
                .toList();

        assertThat(methods).contains(
                "getMobileNumber",
                "getMobileRegion",
                "getLastLoginAt",
                "isCredentialInitialized",
                "isCredentialRotationRequired"
        );

        assertThat(methods).doesNotContain(
                "getPasswordHash",
                "getResetToken",
                "getRefreshToken"
        );
    }

    @Test
    void createAndUpdateContractsAcceptMobileProfileFields() {
        var createMethods = Arrays.stream(CreateUserRequest.class.getMethods())
                .map(method -> method.getName())
                .toList();
        var updateMethods = Arrays.stream(UpdateUserRequest.class.getMethods())
                .map(method -> method.getName())
                .toList();

        assertThat(createMethods).contains(
                "getMobileNumber", "setMobileNumber",
                "getMobileRegion", "setMobileRegion"
        );
        assertThat(updateMethods).contains(
                "getMobileNumber", "setMobileNumber",
                "getMobileRegion", "setMobileRegion"
        );
    }

    @Test
    void responseMetadataTypesRemainNonSecretAndDeterministic() throws Exception {
        assertThat(UserResponse.class.getMethod("getLastLoginAt").getReturnType()).isEqualTo(Instant.class);
        assertThat(UserResponse.class.getMethod("isCredentialInitialized").getReturnType()).isEqualTo(boolean.class);
        assertThat(UserResponse.class.getMethod("isCredentialRotationRequired").getReturnType()).isEqualTo(boolean.class);
    }
}

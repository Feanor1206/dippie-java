package com.dippie.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dippie.common.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsValidRegistrationPasswordsIncludingBoundaryAndWhitespace() {
        assertThatCode(() -> PasswordPolicy.DEFAULT.validateForRegistration("abcdef")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.DEFAULT.validateForRegistration(" abcdef ")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.DEFAULT.validateForRegistration("a".repeat(72))).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.DEFAULT.validateForRegistration("ế".repeat(24))).doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShortRegistrationPassword() {
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateForRegistration("12345"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Mật khẩu phải có ít nhất 6 ký tự");
    }

    @Test
    void rejectsRegistrationPasswordExceeding72Utf8Bytes() {
        assertThat(PasswordPolicy.DEFAULT.exceedsMaxBytes("a".repeat(73))).isTrue();
        assertThat(PasswordPolicy.DEFAULT.exceedsMaxBytes("ế".repeat(25))).isTrue();
        assertThat(PasswordPolicy.DEFAULT.exceedsMaxBytes("ế".repeat(24))).isFalse();

        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateForRegistration("ế".repeat(25)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Mật khẩu được dùng tối đa 72 byte UTF-8.");
    }

    @Test
    void enforcesAdminBootstrapPasswordBounds() {
        assertThatCode(() -> PasswordPolicy.DEFAULT.validateForAdminBootstrap("A-strong-private-password-123"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateForAdminBootstrap("too-short-12345"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mật khẩu quản trị phải dài từ 16 ký tự đến 72 byte.");
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateForAdminBootstrap("x".repeat(73)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mật khẩu quản trị phải dài từ 16 ký tự đến 72 byte.");
    }
}

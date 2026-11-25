package com.dippie.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dippie.common.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

// Test đơn vị cho PasswordPolicy: cấm khoảng trắng, kiểm tra độ dài tối thiểu, trần 72 byte UTF-8 và chuẩn mật khẩu admin.
class PasswordPolicyTest {

    @Test
    void acceptsValidRegistrationPasswordsIncludingBoundaryAndSurroundingWhitespace() {
        assertThatCode(() -> PasswordPolicy.DEFAULT.validateUserPassword("abcdef")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.DEFAULT.validateUserPassword(" abcdef ")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.DEFAULT.validateUserPassword("a".repeat(72))).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.DEFAULT.validateUserPassword("ế".repeat(24))).doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankWhitespaceOnlyAndTooShortRegistrationPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateUserPassword("      "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(PasswordPolicy.BLANK_PASSWORD_MESSAGE);
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateUserPassword("\t\n   \r "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(PasswordPolicy.BLANK_PASSWORD_MESSAGE);
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateUserPassword("  123  "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Mật khẩu cần ít nhất 6 ký tự.");
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateUserPassword("12345"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Mật khẩu cần ít nhất 6 ký tự.");
    }

    @Test
    void rejectsRegistrationPasswordExceeding72Utf8Bytes() {
        assertThat(PasswordPolicy.DEFAULT.exceedsMaxBytes("a".repeat(73))).isTrue();
        assertThat(PasswordPolicy.DEFAULT.exceedsMaxBytes("ế".repeat(25))).isTrue();
        assertThat(PasswordPolicy.DEFAULT.exceedsMaxBytes("ế".repeat(24))).isFalse();

        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateUserPassword("ế".repeat(25)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Mật khẩu không được dài quá 72 byte UTF-8.");
    }

    @Test
    void enforcesAdminBootstrapAndAdminPasswordChangeBounds() {
        assertThatCode(() -> PasswordPolicy.DEFAULT.validateBootstrapPassword("A-strong-private-password-123"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateBootstrapPassword("too-short-12345"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mật khẩu quản trị cần từ 16 ký tự đến tối đa 72 byte.");
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateBootstrapPassword("                "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateBootstrapPassword("x".repeat(73)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mật khẩu quản trị cần từ 16 ký tự đến tối đa 72 byte.");

        assertThatCode(() -> PasswordPolicy.DEFAULT.validateAdminPassword("A-strong-private-password-123"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.DEFAULT.validateAdminPassword("12345678"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Mật khẩu quản trị cần từ 16 ký tự đến tối đa 72 byte.");
    }
}

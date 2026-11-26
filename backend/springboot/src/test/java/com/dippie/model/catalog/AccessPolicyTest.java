package com.dippie.model.catalog;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// Test parse chuỗi AccessPolicy (rỗng về PUBLIC, chuỗi lạ về UNKNOWN) và ma trận phân quyền cho từng nhóm người xem.
class AccessPolicyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void defaultsBlankPolicyToPublic(String raw) {
        assertThat(AccessPolicy.of(raw)).isEqualTo(AccessPolicy.PUBLIC);
    }

    @ParameterizedTest
    @CsvSource({"PuBliC, PUBLIC", "AuThEnTiCaTeD, AUTHENTICATED", "PRIVATE, PRIVATE",
            "restricted, UNKNOWN", "' private ', UNKNOWN"})
    void interpretsPolicy(String raw, AccessPolicy expected) {
        assertThat(AccessPolicy.of(raw)).isEqualTo(expected);
    }

    // Kiểm tra đủ 4 tổ hợp: khách, user thường, admin đã đăng nhập và cờ admin nhưng chưa xác thực.
    @ParameterizedTest
    @CsvSource({
            "PUBLIC, true, true, true, true",
            "AUTHENTICATED, false, true, true, false",
            "PRIVATE, false, false, true, false",
            "UNKNOWN, false, false, false, false"
    })
    void appliesPolicyToEachViewer(AccessPolicy policy, boolean anonymous, boolean user,
                                  boolean admin, boolean unauthenticatedAdmin) {
        assertThat(policy.allows(false, false)).isEqualTo(anonymous);
        assertThat(policy.allows(true, false)).isEqualTo(user);
        assertThat(policy.allows(true, true)).isEqualTo(admin);
        assertThat(policy.allows(false, true)).isEqualTo(unauthenticatedAdmin);
    }
}

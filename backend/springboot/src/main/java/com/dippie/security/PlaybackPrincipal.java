package com.dippie.security;

import com.dippie.model.user.Role;

// sealed interface Java 21 chốt đúng 2 trạng thái người xem khi phát video là đã đăng nhập hoặc khách ẩn danh
public sealed interface PlaybackPrincipal permits PlaybackPrincipal.Authenticated, PlaybackPrincipal.Anonymous {

    // người xem đã đăng nhập kèm vai trò
    record Authenticated(Role role) implements PlaybackPrincipal {
    }

    // khách ẩn danh dùng chung 1 instance INSTANCE cho khỏi cấp phát thừa
    record Anonymous() implements PlaybackPrincipal {
        public static final Anonymous INSTANCE = new Anonymous();
    }
}

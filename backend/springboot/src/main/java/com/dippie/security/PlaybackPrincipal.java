package com.dippie.security;

import com.dippie.model.user.Role;

// Dùng sealed interface của Java 21 để chốt đúng hai trạng thái người xem khi phát video: đã đăng nhập hoặc khách ẩn danh.
public sealed interface PlaybackPrincipal permits PlaybackPrincipal.Authenticated, PlaybackPrincipal.Anonymous {

    // Người xem đã đăng nhập kèm vai trò tương ứng.
    record Authenticated(Role role) implements PlaybackPrincipal {
    }

    // Khách ẩn danh dùng chung một thể hiện INSTANCE để khỏi cấp phát thừa.
    record Anonymous() implements PlaybackPrincipal {
        public static final Anonymous INSTANCE = new Anonymous();
    }
}

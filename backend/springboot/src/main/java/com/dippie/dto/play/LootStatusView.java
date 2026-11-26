package com.dippie.dto.play;

// trạng thái sẵn sàng và chuỗi combo rương báu bay ngang của bé
public record LootStatusView(boolean enabled, boolean ready, int combo) {

    public static LootStatusView disabled() {
        return new LootStatusView(false, false, 0);
    }
}

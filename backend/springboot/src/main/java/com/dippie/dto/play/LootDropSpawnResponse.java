package com.dippie.dto.play;

import java.time.LocalDateTime;

// rương báu bay ngang vừa được server phát sau khi bé xem đủ 5 đến 7 phút
public record LootDropSpawnResponse(
        String dropToken,
        int visibleSeconds,
        LocalDateTime expiresAt,
        int combo
) {
}

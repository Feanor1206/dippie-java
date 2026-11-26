package com.dippie.model.play;

// các trạng thái của thú ảo canh giờ nghỉ mắt theo thời lượng xem liên tục của bé
public enum WatchPetStage {
    HAPPY,
    SWEATY,
    DIZZY,
    FAINTED,
    RESTING;

    // mốc đổ mồ hôi ở 75% ngưỡng ngất (mặc định 20 phút thì đúng 15 phút)
    public static int sweatyThresholdSeconds(int faintMinutes) {
        return (faintMinutes * 60 * 3) / 4;
    }

    // mốc chóng mặt ở 90% ngưỡng ngất (mặc định 20 phút thì đúng 18 phút)
    public static int dizzyThresholdSeconds(int faintMinutes) {
        return (faintMinutes * 60 * 9) / 10;
    }

    public static int faintThresholdSeconds(int faintMinutes) {
        return faintMinutes * 60;
    }

    // tính trạng thái thú ảo từ cờ nghỉ, cờ ngất và số giây xem liên tục hiện tại
    public static WatchPetStage resolve(
            boolean resting,
            boolean fainted,
            int continuousWatchSeconds,
            int faintMinutes
    ) {
        if (resting) {
            return RESTING;
        }
        int faintSeconds = faintThresholdSeconds(faintMinutes);
        if (fainted || continuousWatchSeconds >= faintSeconds) {
            return FAINTED;
        }
        if (continuousWatchSeconds >= dizzyThresholdSeconds(faintMinutes)) {
            return DIZZY;
        }
        if (continuousWatchSeconds >= sweatyThresholdSeconds(faintMinutes)) {
            return SWEATY;
        }
        return HAPPY;
    }
}

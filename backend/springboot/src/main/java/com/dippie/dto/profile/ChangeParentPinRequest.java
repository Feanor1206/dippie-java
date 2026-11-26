package com.dippie.dto.profile;

// gói mã PIN cũ với mới lúc đổi PIN phụ huynh
public record ChangeParentPinRequest(String currentPin, String newPin) {
}

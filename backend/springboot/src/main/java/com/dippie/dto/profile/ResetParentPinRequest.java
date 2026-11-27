package com.dippie.dto.profile;

// phụ huynh quên PIN thì gửi mật khẩu tài khoản kèm PIN mới để đặt lại
public record ResetParentPinRequest(String password, String newPin) {
}

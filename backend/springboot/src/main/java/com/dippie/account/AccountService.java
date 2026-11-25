package com.dippie.account;

import com.dippie.common.error.InvalidRequestException;
import com.dippie.common.error.UnauthorizedException;
import com.dippie.security.CurrentViewer;
import com.dippie.security.JwtService;
import com.dippie.security.PasswordPolicy;
import com.dippie.security.Viewer;
import com.dippie.user.AuthResponse;
import com.dippie.user.User;
import com.dippie.user.UserProfileResponse;
import com.dippie.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_FULL_NAME_LENGTH = 100;

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AccountService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        Viewer viewer = currentViewer.require();
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException("Chưa xác thực người dùng"));

        UpdateProfileRequest payload = request == null ? new UpdateProfileRequest(null, null) : request;
        String email = payload.rawEmail().trim();
        String rawFullName = payload.fullName();

        if (!email.contains("@") || email.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidRequestException("Định dạng email không hợp lệ");
        }
        if (rawFullName != null && rawFullName.length() > MAX_FULL_NAME_LENGTH) {
            throw new InvalidRequestException("Họ tên tối đa 100 ký tự");
        }
        if (userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new InvalidRequestException("Email đã được sử dụng!");
        }

        String resolvedFullName = (rawFullName == null || rawFullName.trim().isEmpty())
                ? user.getUsername()
                : rawFullName.trim();
        user.updateProfile(resolvedFullName, email, LocalDateTime.now(clock));

        try {
            User saved = userRepository.saveAndFlush(user);
            return UserProfileResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new InvalidRequestException("Email đã được sử dụng!");
        }
    }

    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request) {
        Viewer viewer = currentViewer.require();
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException("Chưa xác thực người dùng"));

        ChangePasswordRequest payload = request == null ? new ChangePasswordRequest(null, null) : request;
        String currentPassword = payload.rawCurrentPassword();
        String newPassword = payload.rawNewPassword();

        if (currentPassword.isEmpty()
                || PasswordPolicy.DEFAULT.exceedsMaxBytes(currentPassword)
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidRequestException("Mật khẩu hiện tại không đúng.");
        }
        PasswordPolicy.DEFAULT.validateForRegistration(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new InvalidRequestException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        user.changePassword(passwordEncoder.encode(newPassword), LocalDateTime.now(clock));
        User saved = userRepository.saveAndFlush(user);
        String token = jwtService.issueLoginToken(saved.getUsername(), saved.getTokenVersion());
        return AuthResponse.of(saved, token);
    }
}

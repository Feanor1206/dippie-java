package com.dippie.service.profile;

import com.dippie.dto.profile.ChangeParentPinRequest;
import com.dippie.dto.profile.ParentModeResponse;
import com.dippie.dto.profile.PinRequest;
import com.dippie.dto.profile.ResetParentPinRequest;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.user.User;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.PasswordPolicy;
import com.dippie.security.Viewer;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// lo bật tắt chế độ hồ sơ trẻ em, đổi mã PIN BCrypt và kiểm tra PIN có khóa khi nhập sai nhiều lần
@Service
public class ParentModeService {

    private static final String CHILD_FORBIDDEN_MESSAGE = "Hồ sơ trẻ em không thể chỉnh cài đặt phụ huynh.";
    private static final String NOT_ENABLED_MESSAGE = "Chế độ hồ sơ trẻ em chưa được bật.";
    private static final String LOGIN_REQUIRED_MESSAGE = "Bạn cần đăng nhập để tiếp tục.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final ParentPinRateLimiter pinRateLimiter;
    private final Clock clock;

    public ParentModeService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            PasswordEncoder passwordEncoder,
            ParentPinRateLimiter pinRateLimiter,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.pinRateLimiter = pinRateLimiter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ParentModeResponse status() {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_FORBIDDEN_MESSAGE);
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED_MESSAGE));
        long count = childProfileRepository.countByUserId(user.getId());
        return new ParentModeResponse(user.isParentModeEnabled(), count);
    }

    // bật chế độ phụ huynh với mã PIN 4 đến 6 số đã băm BCrypt
    @Transactional
    public ParentModeResponse enable(PinRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_FORBIDDEN_MESSAGE);
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED_MESSAGE));
        if (user.isParentModeEnabled()) {
            throw new ConflictException("Chế độ hồ sơ trẻ em đã được bật rồi.");
        }
        String pin = User.validateParentPin(request == null ? null : request.pin());
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        user.enableParentMode(passwordEncoder.encode(pin), now);
        userRepository.saveAndFlush(user);
        pinRateLimiter.clear(user.getId());
        return new ParentModeResponse(true, childProfileRepository.countByUserId(user.getId()));
    }

    // đổi mã PIN phụ huynh, bắt buộc nhập đúng PIN hiện tại và PIN mới phải khác PIN cũ
    @Transactional
    public ParentModeResponse changePin(ChangeParentPinRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_FORBIDDEN_MESSAGE);
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED_MESSAGE));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(NOT_ENABLED_MESSAGE);
        }
        String currentPin = request == null ? null : request.currentPin();
        String newPin = request == null ? null : request.newPin();
        verifyParentPin(user, currentPin);
        String validatedNewPin = User.validateParentPin(newPin);
        if (validatedNewPin.equals(currentPin)) {
            throw new InvalidRequestException("Mã PIN mới cần khác mã PIN hiện tại.");
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        user.changeParentPin(passwordEncoder.encode(validatedNewPin), now);
        userRepository.saveAndFlush(user);
        return new ParentModeResponse(true, childProfileRepository.countByUserId(user.getId()));
    }

    // tắt chế độ phụ huynh sau khi nhập đúng PIN, giữ lại hồ sơ trẻ em để bật lại không mất dữ liệu
    @Transactional
    public ParentModeResponse disable(PinRequest request) {
        Viewer viewer = currentViewer.requireMainProfile(CHILD_FORBIDDEN_MESSAGE);
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED_MESSAGE));
        if (!user.isParentModeEnabled()) {
            throw new InvalidRequestException(NOT_ENABLED_MESSAGE);
        }
        verifyParentPin(user, request == null ? null : request.pin());
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        user.disableParentMode(now);
        userRepository.saveAndFlush(user);
        return new ParentModeResponse(false, childProfileRepository.countByUserId(user.getId()));
    }

    // quên PIN thì xác minh bằng mật khẩu tài khoản rồi đặt PIN mới, sai mật khẩu vẫn tính vào bộ đếm khóa như PIN
    public void resetPinWithPassword(User user, ResetParentPinRequest request) {
        pinRateLimiter.ensureNotLocked(user.getId());
        String password = request == null ? null : request.password();
        if (password == null || password.isEmpty()) {
            throw new InvalidRequestException("Hãy nhập mật khẩu tài khoản.");
        }
        boolean matched = !PasswordPolicy.DEFAULT.exceedsMaxBytes(password)
                && passwordEncoder.matches(password, user.getPassword());
        if (!matched) {
            pinRateLimiter.recordFailure(user.getId());
            throw new InvalidRequestException("Mật khẩu tài khoản chưa đúng.");
        }
        String newPin = User.validateParentPin(request.newPin());
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        user.changeParentPin(passwordEncoder.encode(newPin), now);
        userRepository.saveAndFlush(user);
        pinRateLimiter.clear(user.getId());
    }

    // check khóa rate limit trước rồi mới so BCrypt, sai thì cộng bộ đếm còn đúng thì xóa bộ đếm sai
    public void verifyParentPin(User user, String rawPin) {
        pinRateLimiter.ensureNotLocked(user.getId());
        if (rawPin == null || rawPin.isEmpty()) {
            throw new InvalidRequestException("Hãy nhập mã PIN phụ huynh.");
        }
        String storedHash = user.getParentPinHash();
        boolean matched = storedHash != null
                && !PasswordPolicy.DEFAULT.exceedsMaxBytes(rawPin)
                && passwordEncoder.matches(rawPin, storedHash);
        if (!matched) {
            pinRateLimiter.recordFailure(user.getId());
            throw new InvalidRequestException("Mã PIN phụ huynh chưa đúng.");
        }
        pinRateLimiter.clear(user.getId());
    }
}

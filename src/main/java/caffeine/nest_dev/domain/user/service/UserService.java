package caffeine.nest_dev.domain.user.service;

import caffeine.nest_dev.common.config.JwtUtil;
import caffeine.nest_dev.common.config.PasswordEncoder;
import caffeine.nest_dev.common.enums.ErrorCode;
import caffeine.nest_dev.common.exception.BaseException;
import caffeine.nest_dev.domain.auth.dto.request.DeleteRequestDto;
import caffeine.nest_dev.domain.auth.dto.response.LoginResponseDto;
import caffeine.nest_dev.domain.auth.repository.TokenRepository;
import caffeine.nest_dev.domain.user.dto.request.ExtraInfoRequestDto;
import caffeine.nest_dev.domain.user.dto.request.UpdatePasswordRequestDto;
import caffeine.nest_dev.domain.user.dto.request.UserRequestDto;
import caffeine.nest_dev.domain.user.dto.response.UserResponseDto;
import caffeine.nest_dev.domain.user.entity.User;
import caffeine.nest_dev.domain.user.enums.SocialType;
import caffeine.nest_dev.domain.user.enums.UserGrade;
import caffeine.nest_dev.domain.user.enums.UserRole;
import caffeine.nest_dev.domain.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenRepository tokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional(readOnly = true)
    public UserResponseDto findUser(Long userId) {

        // 유저 조회
        User user = findByIdAndIsDeletedFalseOrElseThrow(userId);

        return UserResponseDto.of(user);
    }

    @Transactional
    public void updateUser(Long userId, UserRequestDto dto) {

        // dto 가 null 일 때
        if (dto == null) {
            throw new BaseException(ErrorCode.EMPTY_UPDATE_REQUEST);
        }

        // 이메일 중복 검증
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new BaseException(ErrorCode.ALREADY_EXIST_EMAIL);
        }

        // 유저 조회
        User user = findByIdAndIsDeletedFalseOrElseThrow(userId);

        // 수정 메서드 호출
        user.updateUser(dto, user);
    }

    @Transactional
    public void updatePassword(Long userId, UpdatePasswordRequestDto dto) {

        // 유저 조회
        User user = findByIdAndIsDeletedFalseOrElseThrow(userId);

        // 소셜 로그인 회원은 예외 발생
        if (!SocialType.LOCAL.equals(user.getSocialType())) {
            throw new BaseException(ErrorCode.NOT_LOCAL_USER);
        }

        // 비밀 번호 검증
        if (!passwordEncoder.matches(dto.getRawPassword(), user.getPassword())) {
            throw new BaseException(ErrorCode.INVALID_PASSWORD);
        }

        // 새로운 비밀번호가 현재 비밀번호와 같은 경우
        if (dto.getNewPassword().equals(dto.getRawPassword())) {
            throw new BaseException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
        }

        // 새 비밀번호 인코딩
        String encodedPassword = passwordEncoder.encode(dto.getNewPassword());

        // 비밀번호 변경
        user.updatePassword(encodedPassword);
    }

    @Transactional
    public LoginResponseDto updateExtraInfo(Long userId, ExtraInfoRequestDto dto) {

        // 유저 조회
        User user = findByIdAndIsDeletedFalseOrElseThrow(userId);

        if (dto.getUserRole() == null || dto.getPhoneNumber() == null || dto.getName() == null) {
            throw new BaseException(ErrorCode.EXTRA_INFO_REQUIRED);
        }

        // MENTEE 일때
        if (dto.getUserRole().equals(UserRole.MENTEE)) {
            user.updateUserGrade(UserGrade.SEED);
            user.updateExtraInfo(dto);
            user.updateTotalPrice(0);
        }

        // MENTOR 일때
        if (dto.getUserRole().equals(UserRole.MENTOR)) {
            user.updateExtraInfo(dto);
        }

        // accessToken 발급
        String accessToken = jwtUtil.createAccessToken(user);
        // refreshToken 발급
        String refreshToken = jwtUtil.createRefreshToken(user);

        // redis 에 refreshToken 저장
        tokenRepository.save(user.getId(), refreshToken, refreshTokenExpiration);

        return LoginResponseDto.of(user, accessToken, refreshToken);
    }

    @Transactional
    public void deleteUser(Long userId, String accessToken, DeleteRequestDto dto) {

        // 토큰 무효화
        if (dto.getRefreshToken() == null) {
            throw new BaseException(ErrorCode.TOKEN_MISSING);
        }

        // refresh 토큰 유효성 검사
        log.info("토큰 유효성 검사 시작");
        if (jwtUtil.validateToken(dto.getRefreshToken())) {
            throw new BaseException(ErrorCode.INVALID_TOKEN);
        }

        // access 토큰에서 가져온 userId 와 refresh 토큰에서 가져온 userId 가 일치하는지 검증
        Long userIdFromAccessToken = jwtUtil.getUserIdFromToken(accessToken);
        Long userIdFromRefreshToken = jwtUtil.getUserIdFromToken(dto.getRefreshToken());
        if (!userIdFromAccessToken.equals(userIdFromRefreshToken)) {
            throw new BaseException(ErrorCode.TOKEN_USER_MISMATCH);
        }

        // refreshToken 일치 여부 검증
        String refreshTokenByUserId = tokenRepository.findByUserId(userIdFromRefreshToken);
        if (!dto.getRefreshToken().equals(refreshTokenByUserId)) {
            throw new BaseException(ErrorCode.INVALID_TOKEN);
        }

        // 유저 조회
        User user = findByIdAndIsDeletedFalseOrElseThrow(userId);

        // 비밀번호 일치 검증
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BaseException(ErrorCode.INVALID_PASSWORD);
        }

        // access 토큰 redis에 블랙리스트 추가
        jwtUtil.addToBlacklistAccessToken(accessToken);
        log.info("access 토큰을 블랙리스트에 추가");

        // refresh 토큰 redis에 블랙리스트 추가
        jwtUtil.addToBlacklistRefreshToken(dto.getRefreshToken());
        log.info("refresh 토큰을 블랙리스트에 추가");

        // 유저 상태 변경
        user.deleteUser(true);
    }

    // user 가 없으면 예외 던지기
    public User findByIdAndIsDeletedFalseOrElseThrow(Long userId) {
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    }

    // 총 결제 금액으로 등급 산정
    @Scheduled(cron = "59 59 23 L * ?")
    @Transactional
    public void runOnLastDayOfMonth() {
        List<User> users = userRepository.findAll();

        for (User user : users) {

            Integer totalPrice = user.getTotalPrice();

            if (totalPrice < 20000) {
                // 20,000원 미만은 SEED
                user.updateUserGrade(UserGrade.SEED);
            } else if (totalPrice <= 39999) {
                // 20,000원 ~ 39,999원 -> SPROUT
                user.updateUserGrade(UserGrade.SPROUT);
            } else if (totalPrice <= 59999) {
                // 40,000원 ~ 59,999원 -> BRANCH
                user.updateUserGrade(UserGrade.BRANCH);
            } else if (totalPrice <= 79999) {
                // 60,000원 ~ 79,999원 -> BLOOM
                user.updateUserGrade(UserGrade.BLOOM);
            } else {
                // 80,000원 이상 -> NEST
                user.updateUserGrade(UserGrade.NEST);
            }
        }
    }
}

package caffeine.nest_dev.domain.auth.service;

import caffeine.nest_dev.common.config.JwtUtil;
import caffeine.nest_dev.common.config.PasswordEncoder;
import caffeine.nest_dev.common.enums.ErrorCode;
import caffeine.nest_dev.common.exception.BaseException;
import caffeine.nest_dev.domain.auth.dto.request.AuthRequestDto;
import caffeine.nest_dev.domain.auth.dto.request.LoginRequestDto;
import caffeine.nest_dev.domain.auth.dto.request.RefreshTokenRequestDto;
import caffeine.nest_dev.domain.auth.dto.response.AuthResponseDto;
import caffeine.nest_dev.domain.auth.dto.response.LoginResponseDto;
import caffeine.nest_dev.domain.auth.dto.response.TokenResponseDto;
import caffeine.nest_dev.domain.auth.repository.TokenRepository;
import caffeine.nest_dev.domain.user.entity.User;
import caffeine.nest_dev.domain.user.enums.SocialType;
import caffeine.nest_dev.domain.user.repository.UserRepository;
import caffeine.nest_dev.domain.user.service.UserService;
import caffeine.nest_dev.oauth2.userinfo.OAuth2UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public AuthResponseDto signup(AuthRequestDto dto) {

        // 이메일 중복 검증
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new BaseException(ErrorCode.ALREADY_EXIST_EMAIL);
        }

        // 비밀번호 인코딩
        String encoded = passwordEncoder.encode(dto.getPassword());

        User user = switch (dto.getUserRole()) {
            case MENTEE -> User.createMentee(dto, encoded);
            case MENTOR -> User.createMentor(dto, encoded);
            case ADMIN -> User.createAdmin(dto, encoded);

            // 다른 값이 들어오면 예외 발생
            default -> throw new BaseException(ErrorCode.INVALID_ROLE_FOR_SIGNUP);
        };

        User savedUser = userRepository.save(user);

        return AuthResponseDto.of(savedUser);
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto dto) {
        // 유저 조회
        User user = userRepository.findByEmailAndIsDeletedFalse(dto.getEmail())
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        // 비밀번호 일치 여부 검증
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BaseException(ErrorCode.INVALID_PASSWORD);
        }

        // Local 로그인 으로 변경
        if (user.getSocialType() != SocialType.LOCAL) {
            user.updateSocialType(SocialType.LOCAL, null);
        }

        // 토큰 생성
        String accessToken = jwtUtil.createAccessToken(user);
        String refreshToken = jwtUtil.createRefreshToken(user);

        // redis에 저장
        tokenRepository.save(user.getId(), refreshToken, refreshTokenExpiration);

        return LoginResponseDto.of(user, accessToken, refreshToken);
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {

        if (refreshToken == null) {
            throw new BaseException(ErrorCode.TOKEN_MISSING);
        }

        // refresh 토큰 유효성 검사
        log.info("토큰 유효성 검사 시작");
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BaseException(ErrorCode.INVALID_TOKEN);
        }

        // 토큰 파싱해서 접두사 빼기
        String resolvedAccessToken = jwtUtil.resolveToken(accessToken);

        // access 토큰에서 가져온 userId 와 refresh 토큰에서 가져온 userId 가 일치하는지 검증
        Long userIdFromAccessToken = jwtUtil.getUserIdFromToken(resolvedAccessToken);
        Long userIdFromRefreshToken = jwtUtil.getUserIdFromToken(refreshToken);
        log.info("동일한 유저인지 검증 시작");
        if (!userIdFromAccessToken.equals(userIdFromRefreshToken)) {
            throw new BaseException(ErrorCode.TOKEN_USER_MISMATCH);
        }

        // refreshToken 일치 여부 검증
        String refreshTokenByUserId = tokenRepository.findByUserId(userIdFromRefreshToken);
        if (!refreshToken.equals(refreshTokenByUserId)) {
            throw new BaseException(ErrorCode.INVALID_TOKEN);
        }

        // access 토큰 redis에 블랙리스트 추가
        jwtUtil.addToBlacklistAccessToken(resolvedAccessToken);
        log.info("access 토큰을 블랙리스트에 추가");

        // refresh 토큰 redis에 블랙리스트 추가
        jwtUtil.addToBlacklistRefreshToken(refreshToken);
        log.info("refresh 토큰을 블랙리스트에 추가");
    }

    @Transactional
    public TokenResponseDto reissue(RefreshTokenRequestDto dto, Long userId) {

        String refreshToken = dto.getRefreshToken();

        // refreshToken 일치 여부 검증
        String refreshTokenByUserId = tokenRepository.findByUserId(userId);
        if (!refreshToken.equals(refreshTokenByUserId)) {
            throw new BaseException(ErrorCode.INVALID_TOKEN);
        }

        // 블랙리스트 확인
        String blacklistToken = stringRedisTemplate.opsForValue().get("blacklist:" + refreshToken);
        if (blacklistToken != null) {
            throw new BaseException(ErrorCode.IS_BLACKLISTED);
        }

        // 토큰 유효성 검사
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BaseException(ErrorCode.INVALID_TOKEN);
        }

        // 새로운 access 토큰 발급
        Long userIdFromToken = jwtUtil.getUserIdFromToken(refreshToken);
        User user = userService.findByIdAndIsDeletedFalseOrElseThrow(userIdFromToken);
        String newAccessToken = jwtUtil.createAccessToken(user);

        return TokenResponseDto.of(newAccessToken);
    }

    // DB에서 유저 조회
    @Transactional
    public User findUserByEmail(OAuth2UserInfo userInfo, SocialType provider) {
        return userRepository.findByEmailAndIsDeletedFalse(userInfo.getEmail())
                .orElseGet(() -> registerIfAbsent(userInfo, provider));
    }

    @Transactional
    // OAuth2UserInfo 정보로 user 객체 만들기
    public User registerIfAbsent(OAuth2UserInfo userInfo, SocialType provider) {
        return userRepository.save(User.createSocialUser(userInfo, provider));
    }
}

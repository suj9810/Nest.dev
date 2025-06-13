package caffeine.nest_dev.domain.user.entity;

import caffeine.nest_dev.common.entity.BaseEntity;
import caffeine.nest_dev.domain.auth.dto.request.AuthRequestDto;
import caffeine.nest_dev.domain.user.dto.request.ExtraInfoRequestDto;
import caffeine.nest_dev.domain.user.dto.request.UserRequestDto;
import caffeine.nest_dev.domain.user.enums.SocialType;
import caffeine.nest_dev.domain.user.enums.UserGrade;
import caffeine.nest_dev.domain.user.enums.UserRole;
import caffeine.nest_dev.oauth2.userinfo.OAuth2UserInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String email;

    @Column(nullable = false)
    private String nickName;

    @Column(nullable = false)
    private String password;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private SocialType socialType;  // 소셜 타입

    private String socialId; // 소셜 로그인 식별자

    @Enumerated(EnumType.STRING)
    private UserGrade userGrade; // 회원 등급

    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    private Integer totalPrice;

    private String bank;

    private String accountNumber;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isDeleted;

    @Builder
    private User(String name, String email, String nickName, String password, String phoneNumber,
            SocialType socialType, String socialId, UserRole userRole, UserGrade userGrade,
            Integer totalPrice) {
        this.name = name;
        this.email = email;
        this.nickName = nickName;
        this.password = password;
        this.phoneNumber = phoneNumber;
        this.socialType = socialType;
        this.socialId = socialId;
        this.userRole = userRole;
        this.userGrade = userGrade; // null이 될 수도 있음
        this.totalPrice = totalPrice;
    }

    public static User createMentee(AuthRequestDto dto, String encodedPassword) {
        return User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .nickName(dto.getNickName())
                .password(encodedPassword)
                .phoneNumber(dto.getPhoneNumber())
                .userRole(UserRole.MENTEE)
                .userGrade(UserGrade.SEED)
                .socialType(SocialType.LOCAL)
                .totalPrice(0)
                .build();
    }

    public static User createMentor(AuthRequestDto dto, String encodedPassword) {
        return User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .nickName(dto.getNickName())
                .password(encodedPassword)
                .phoneNumber(dto.getPhoneNumber())
                .userRole(UserRole.MENTOR)
                .socialType(SocialType.LOCAL)
                .build();
    }

    public static User createAdmin(AuthRequestDto dto, String encodedPassword) {
        return User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .nickName(dto.getNickName())
                .password(encodedPassword)
                .phoneNumber(dto.getPhoneNumber())
                .userRole(UserRole.ADMIN)
                .build();
    }

    public static User createSocialUser(OAuth2UserInfo userInfo, SocialType socialType) {
        return User.builder()
                .email(userInfo.getEmail())
                .nickName(userInfo.getNickName())
                .password(UUID.randomUUID().toString()) // 임의의 비밀번호 사용
                .userRole(UserRole.GUEST)
                .socialType(socialType)
                .socialId(userInfo.getId())
                .build();
    }

    // -------------- 수정 메서드 --------------

    public void updateUser(UserRequestDto dto, User user) {
        if (dto.getEmail() != null) {
            this.email = dto.getEmail();
        }

        if (dto.getNickName() != null) {
            this.nickName = dto.getNickName();
        }

        if (dto.getPhoneNumber() != null) {
            this.phoneNumber = dto.getPhoneNumber();
        }

        // 멘토일 경우 추가 수정
        if (user.getUserRole() == UserRole.MENTOR) {
            if (dto.getBank() != null) {
                this.bank = dto.getBank();
            }

            if (dto.getAccountNumber() != null) {
                this.accountNumber = dto.getAccountNumber();
            }
        }
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void deleteUser(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public void updateUserGrade(UserGrade userGrade) {
        this.userGrade = userGrade;
    }

    public void updateExtraInfo(ExtraInfoRequestDto dto) {
        this.name = dto.getName();
        this.userRole = dto.getUserRole();
        this.phoneNumber = dto.getPhoneNumber();
    }

    public void updateSocialType(SocialType socialType, String socialId) {
        this.socialType = socialType;
        this.socialId = socialId;
    }

    public void updateTotalPrice(Integer totalPrice) {
        this.totalPrice = totalPrice;
    }
}

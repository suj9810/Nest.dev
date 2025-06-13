package caffeine.nest_dev.domain.user.dto.response;

import caffeine.nest_dev.domain.user.entity.User;
import caffeine.nest_dev.domain.user.enums.SocialType;
import caffeine.nest_dev.domain.user.enums.UserGrade;
import caffeine.nest_dev.domain.user.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // null 값은 JSON 에 포함 X
public class UserResponseDto {

    private Long id;
    private String name;
    private String email;
    private String nickName;
    private String phoneNumber;
    private UserRole userRole;
    private UserGrade userGrade;
    private SocialType socialType;
    private Integer totalPrice;
    private String bank;
    private String accountNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserResponseDto of(User user) {
        if (user.getUserRole() == UserRole.MENTEE) {

            return UserResponseDto.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .nickName(user.getNickName())
                    .phoneNumber(user.getPhoneNumber())
                    .userRole(user.getUserRole())
                    .userGrade(user.getUserGrade())
                    .socialType(user.getSocialType())
                    .totalPrice(user.getTotalPrice())
                    .createdAt(user.getCreatedAt())
                    .updatedAt(user.getUpdatedAt())
                    .build();
        }

        // MENTOR 일 때
        return UserResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .nickName(user.getNickName())
                .phoneNumber(user.getPhoneNumber())
                .userRole(user.getUserRole())
                .socialType(user.getSocialType())
                .bank(user.getBank())
                .accountNumber(user.getAccountNumber())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

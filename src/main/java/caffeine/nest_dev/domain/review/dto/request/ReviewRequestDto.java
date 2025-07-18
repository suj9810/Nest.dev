package caffeine.nest_dev.domain.review.dto.request;

import caffeine.nest_dev.domain.reservation.entity.Reservation;
import caffeine.nest_dev.domain.review.entity.Review;
import caffeine.nest_dev.domain.user.entity.User;
import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReviewRequestDto {

    @NotBlank(message="내용은 필수로 입력해야 합니다.")
    private String content;
    private Long mentor;
    private Long mentee;
    private Long reservationId;

    public Review toEntity(User mentor, User mentee, Reservation reservation){
        return Review.builder()
                .mentor(mentor)
                .mentee(mentee)
                .reservation(reservation)
                .content(content)
                .build();
    }

}

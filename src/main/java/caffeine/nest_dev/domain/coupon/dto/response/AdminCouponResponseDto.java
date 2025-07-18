package caffeine.nest_dev.domain.coupon.dto.response;

import caffeine.nest_dev.domain.coupon.entity.Coupon;
import caffeine.nest_dev.domain.coupon.enums.CouponDiscountType;
import caffeine.nest_dev.domain.user.enums.UserGrade;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AdminCouponResponseDto {

    private final Long id;
    private final String name;
    private final Integer discountAmount;
    private final Integer totalQuantity;
    private final Integer issuedQuantity;
    private final LocalDateTime validFrom;
    private final LocalDateTime validTo;
    private final UserGrade minGrade;
    private final Integer minOrderAmount;
    private final CouponDiscountType discountType;
    private final boolean canIssue;

    public static AdminCouponResponseDto of(Coupon coupon) {
        return AdminCouponResponseDto.builder()
                .id(coupon.getId())
                .name(coupon.getName())
                .discountAmount(coupon.getDiscountAmount())
                .totalQuantity(coupon.getTotalQuantity())
                .issuedQuantity(coupon.getIssuedQuantity())
                .validFrom(coupon.getValidFrom())
                .validTo(coupon.getValidTo())
                .minGrade(coupon.getMinGrade())
                .minOrderAmount(coupon.getMinOrderAmount())
                .discountType(coupon.getDiscountType())
                .canIssue(coupon.canIssue())
                .build();
    }
}

package caffeine.nest_dev.domain.notification.dto.response;

import caffeine.nest_dev.domain.notification.entity.Notification;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NotificationResponse {

    private String content;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .content(notification.getContent())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}

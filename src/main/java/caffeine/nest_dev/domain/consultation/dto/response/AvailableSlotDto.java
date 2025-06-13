package caffeine.nest_dev.domain.consultation.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AvailableSlotDto {

    private LocalDateTime availableStartAt;
    private LocalDateTime availableEndAt;

}

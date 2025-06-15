package caffeine.nest_dev.domain.chatroom.scheduler.repository;

import caffeine.nest_dev.domain.chatroom.scheduler.entity.NotificationSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationScheduleRepository extends JpaRepository<NotificationSchedule, Long> {

    List<NotificationSchedule> findAllByIsSent(boolean b);
}

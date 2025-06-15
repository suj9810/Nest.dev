package caffeine.nest_dev.domain.chatroom.scheduler.service;

import caffeine.nest_dev.domain.chatroom.scheduler.entity.NotificationSchedule;
import caffeine.nest_dev.domain.chatroom.scheduler.repository.NotificationScheduleRepository;
import caffeine.nest_dev.domain.notification.service.NotificationService;
import caffeine.nest_dev.domain.user.entity.User;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomTerminationNotifier {

    private final TaskScheduler taskScheduler;
    private final NotificationService notificationService;
    private final NotificationScheduleRepository scheduleRepository;

    public void init() {
        List<NotificationSchedule> findScheduleList = scheduleRepository.findAllByIsSent(false);

        if (findScheduleList.isEmpty()) {
            log.info("저장된 작업이 없습니다.");
            return;
        }
        log.info("불러온 알림 예약 작업 개수: {}", findScheduleList.size());

        for (NotificationSchedule schedule : findScheduleList) {

            if (schedule.getScheduledAt().isAfter(LocalDateTime.now())) {
                taskScheduler.schedule(createNotification(schedule.getId()),
                        Date.from(schedule.getScheduledAt().atZone(ZoneId.systemDefault()).toInstant()));
                log.info("서버시작 후 실행되지 않은 작업이 등록되었습니다.");
            }
        }
    }


    public void registerNotificationSchedule(Long chatRoomId, LocalDateTime endTime, User user) {
        NotificationSchedule schedule = NotificationSchedule.builder()
                .chatRoomId(chatRoomId)
                .scheduledAt(endTime.minusMinutes(5))
                .receiver(user)
                .build();

        NotificationSchedule savedSchedule = scheduleRepository.save(schedule);

        taskScheduler.schedule(
                createNotification(savedSchedule.getId()),
                Date.from(savedSchedule.getScheduledAt().atZone(ZoneId.systemDefault()).toInstant())
        );
    }


    private Runnable createNotification(Long scheduleId) {
        return () -> {
            NotificationSchedule schedule = scheduleRepository.findById(scheduleId).orElseThrow(
                    () -> new IllegalArgumentException("저장된 작업이 없습니다.")
            );

            if (schedule.isSent()) {
                log.info("이미 완료된 스케줄입니다. ID : {}", scheduleId);
                return;
            }

            notificationService.send(schedule.getReceiver(), "채팅 종료까지 5분 남았습니다.");

            // isSent : false -> true, 전송시간 기록
            schedule.update();

            scheduleRepository.save(schedule);

            log.info("알림 전송 완료. 채팅방 ID : {}, 알림 전송 시간: {}", schedule.getChatRoomId(), schedule.getSentAt());
        };
    }
}

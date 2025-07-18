package caffeine.nest_dev.domain.chatroom.scheduler.service;

import caffeine.nest_dev.common.enums.ErrorCode;
import caffeine.nest_dev.common.exception.BaseException;
import caffeine.nest_dev.domain.chatroom.scheduler.entity.NotificationSchedule;
import caffeine.nest_dev.domain.chatroom.scheduler.enums.ChatRoomType;
import caffeine.nest_dev.domain.chatroom.scheduler.repository.NotificationScheduleRepository;
import caffeine.nest_dev.domain.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
        try {

            List<NotificationSchedule> findScheduleList = scheduleRepository.findAllByIsSent(false);

            if (findScheduleList.isEmpty()) {
                log.info("저장된 작업이 없습니다.");
                return;
            }
            log.info("불러온 알림 예약 작업 개수: {}", findScheduleList.size());

            List<NotificationSchedule> futureSchedules = new ArrayList<>();
            List<NotificationSchedule> expiredSchedules = new ArrayList<>();

            // 만료된 스케줄 분류
            for (NotificationSchedule schedule : findScheduleList) {
                if (schedule.getScheduledAt().isAfter(LocalDateTime.now())) {
                    futureSchedules.add(schedule);
                } else {
                    expiredSchedules.add(schedule);
                }
            }
            log.info("재등록 : {}개, 삭제 : {}개", futureSchedules.size(), expiredSchedules.size());

            // 만료된 스케줄 제거
            if (!expiredSchedules.isEmpty()) {
                scheduleRepository.deleteAll(expiredSchedules);
            }

            for (NotificationSchedule schedule : futureSchedules) {
                startSchedule(schedule);
            }
            log.info("채팅 알림 초기화 등록 완료");

        } catch (Exception e) {
            log.error("초기화 등록 오류", e);
        }
    }


    public void registerNotificationSchedule(Long chatRoomId, Long reservationId, LocalDateTime endTime,
            Long receiverId) {
        try {
            NotificationSchedule schedule = NotificationSchedule.builder()
                    .chatRoomId(chatRoomId)
                    .reservationId(reservationId)
                    .scheduledAt(endTime.minusMinutes(5))
                    .receiverId(receiverId)
                    .build();

            NotificationSchedule savedSchedule = scheduleRepository.save(schedule);

            startSchedule(savedSchedule);
        } catch (Exception e) {
            log.error("채팅 종료 알림 등록 실패");
        }
    }

    private void startSchedule(NotificationSchedule schedule) {
        taskScheduler.schedule(
                createNotification(schedule.getId()),
                Date.from(schedule.getScheduledAt().atZone(ZoneId.systemDefault()).toInstant())
        );
    }

    private Runnable createNotification(Long scheduleId) {
        return () -> {
            NotificationSchedule schedule = scheduleRepository.findById(scheduleId).orElseThrow(
                    () -> new BaseException(ErrorCode.NOTIFICATION_SCHEDULE_NOT_FOUND)
            );

            if (schedule.isSent()) {
                log.info("이미 완료된 스케줄입니다. ID : {}", scheduleId);
                return;
            }
            Long chatRoomId = schedule.getChatRoomId();
            Long reservationId = schedule.getReservationId();
            Long receiverId = schedule.getReceiverId();
            notificationService.send(receiverId, "채팅 종료까지 5분 남았습니다.", ChatRoomType.CLOSE, chatRoomId, reservationId);

            // isSent : false -> true, 전송시간 기록
            schedule.markAsSent();

            scheduleRepository.save(schedule);

            log.info("알림 전송 완료. 채팅방 ID : {}, 알림 전송 시간: {}", schedule.getChatRoomId(), schedule.getSentAt());
        };
    }
}

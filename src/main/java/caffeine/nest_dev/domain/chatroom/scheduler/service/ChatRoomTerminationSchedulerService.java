package caffeine.nest_dev.domain.chatroom.scheduler.service;

import caffeine.nest_dev.common.enums.ErrorCode;
import caffeine.nest_dev.common.exception.BaseException;
import caffeine.nest_dev.common.websocket.util.WebSocketSessionRegistry;
import caffeine.nest_dev.domain.chatroom.entity.ChatRoom;
import caffeine.nest_dev.domain.chatroom.repository.ChatRoomRepository;
import caffeine.nest_dev.domain.chatroom.scheduler.entity.ChatRoomSchedule;
import caffeine.nest_dev.domain.chatroom.scheduler.enums.ChatRoomType;
import caffeine.nest_dev.domain.chatroom.scheduler.enums.ScheduleStatus;
import caffeine.nest_dev.domain.chatroom.scheduler.repository.ChatRoomScheduleRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomTerminationSchedulerService {

    // 스케줄링을 위한 빈
    private final TaskScheduler taskScheduler;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomScheduleRepository chatRoomScheduleRepository;

    // 사용자 Id , Session 매핑한 저장소
    private final WebSocketSessionRegistry sessionRegistry;

    // 서버 재시작 시 초기화 작업
    public void init() {
        List<ChatRoomSchedule> scheduleList = chatRoomScheduleRepository.findAllByChatRoomTypeAndScheduleStatus(
                ChatRoomType.CLOSE, ScheduleStatus.PENDING);

        if (scheduleList.isEmpty()) {
            log.info("저장된 종료 예약 작업이 없습니다.");
            return;
        }
        log.info("예약 종료 스케줄 {}건", scheduleList.size());

        // 예약시간이 지나지 않았다면 작업을 다시 등록함
        for (ChatRoomSchedule roomSchedule : scheduleList) {
            if (roomSchedule.getScheduledTime().isAfter(LocalDateTime.now())) {
                taskScheduler.schedule(disconnectUsersAndCloseRoom(roomSchedule.getId()),
                        java.sql.Date.from(roomSchedule.getScheduledTime().atZone(ZoneId.systemDefault()).toInstant()));
                log.info("서버시작 후 실행되지 않은 작업이 등록되었습니다.");
            }
        }

    }

    /**
     * 채팅방 종료 스케줄 등록 메서드
     *
     * @param reservationId 예약 ID
     * @param endTime       예약 종료 시간 (채팅 종료 시간)
     */
    public void registerChatRoomCloseSchedule(Long reservationId, LocalDateTime endTime) {
        ChatRoomSchedule closeSchedule = ChatRoomSchedule.builder()
                .reservationId(reservationId)
                .scheduledTime(endTime)
                .scheduleStatus(ScheduleStatus.PENDING)
                .chatRoomType(ChatRoomType.CLOSE)
                .build();

        ChatRoomSchedule saved = chatRoomScheduleRepository.save(closeSchedule);

        // 종료 시각에 해당 채팅방을 닫는 작업 예약
        taskScheduler.schedule(
                disconnectUsersAndCloseRoom(saved.getId()),
                Date.from(endTime.atZone(ZoneId.systemDefault()).toInstant())
        );
    }

    /**
     * 실제 채팅방을 종료하고 참여자의 세션을 닫는 작업을 Runnable 변환
     *
     * @param scheduleId 예약된 채팅 종료 작업 ID
     * @return Runnable
     */
    private Runnable disconnectUsersAndCloseRoom(Long scheduleId) {
        return () -> {
            ChatRoomSchedule schedule = chatRoomScheduleRepository.findById(scheduleId).orElseThrow(
                    () -> new BaseException(ErrorCode.CHATROOM_SCHEDULE_NOT_FOUND)
            );

            if (ScheduleStatus.COMPLETE.equals(schedule.getScheduleStatus())) {
                log.info("이미 완료된 스케줄입니다. ID: {}", scheduleId);
                return;
            }

            // 종료 후 상태 업데이트
            schedule.updateStatus();
            chatRoomScheduleRepository.save(schedule);

            Long reservationId = schedule.getReservationId();
            ChatRoom chatRoom = chatRoomRepository.findByReservationId(reservationId).orElseThrow(
                    () -> new BaseException(ErrorCode.CHATROOM_NOT_FOUND)
            );

            // isClosed == true 설정
            chatRoom.close();
            chatRoomRepository.save(chatRoom);

            // 사용자별 세션 종료
            String mentorId = chatRoom.getMentor().getId().toString();
            String menteeId = chatRoom.getMentee().getId().toString();
            disconnectUser(mentorId);
            disconnectUser(menteeId);

            log.info("종료 예약 완료 : ScheduleId = {}", scheduleId);
            log.info("채팅방 종료 완료 : ChatRoomId = {}, mentor = {}, mentee = {}", chatRoom.getId(), mentorId, menteeId);
        };
    }

    /**
     * 특정 사용자 ID의 WebSocket 세션 종료
     *
     * @param userId 종료할 사용자 ID
     */
    private void disconnectUser(String userId) {
        WebSocketSession session = sessionRegistry.getSession(userId);
        if (session != null && session.isOpen()) {
            try {
                session.close(new CloseStatus(4000, "채팅 종료"));
            } catch (Exception e) {
                log.warn("세션 종료 실패 : {}", userId, e);
            }
        }
    }
}

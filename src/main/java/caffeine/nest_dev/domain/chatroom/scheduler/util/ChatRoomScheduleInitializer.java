package caffeine.nest_dev.domain.chatroom.scheduler.util;

import caffeine.nest_dev.domain.chatroom.scheduler.service.ChatRoomSchedulerService;
import caffeine.nest_dev.domain.chatroom.scheduler.service.ChatRoomTerminationNotifier;
import caffeine.nest_dev.domain.chatroom.scheduler.service.ChatRoomTerminationSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@code ChatRoomScheduleInitializer}는 애플리케이션 시작 시 저장된 채팅방 관련 예약 작업을 복구, 재등록 하는 초기화 클래스입니다.
 *
 * <p>각 작업은 {@link ApplicationReadyEvent} 시점에 순차적으로 복구됩니다.
 * <ul>
 *      <li> 채팅방 생성 예약 작업</li>
 *      <li> 채팅 종료 5분 전 알림 예약 작업</li>
 *      <li> 채팅방 자동 종료 예약 작업</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomScheduleInitializer {

    private final ChatRoomSchedulerService chatRoomSchedulerService;
    private final ChatRoomTerminationNotifier chatRoomTerminationNotifier;
    private final ChatRoomTerminationSchedulerService chatRoomTerminationSchedulerService;

    @EventListener(ApplicationReadyEvent.class)
    public void initAllSchedules() {
        log.info("[초기화] 채팅방 생성 예약 복구 시작");
        chatRoomSchedulerService.init();

        log.info("[초기화] 알림 예약 복구 시작");
        chatRoomTerminationNotifier.init();

        log.info("[초기화] 채팅방 종료 예약 복구 시작");
        chatRoomTerminationSchedulerService.init();

        log.info("[초기화] 모든 스케줄러 초기화 완료");
    }
}

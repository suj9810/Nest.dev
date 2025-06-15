package caffeine.nest_dev.domain.notification.service;

import caffeine.nest_dev.domain.notification.dto.response.NotificationResponse;
import caffeine.nest_dev.domain.notification.entity.Notification;
import caffeine.nest_dev.domain.notification.repository.EmitterRepository;
import caffeine.nest_dev.domain.user.entity.User;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class NotificationService {

    // sse timeout 시간
    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60;

    private final EmitterRepository emitterRepository;

    /**
     * 새로운 SseEmitter 생성
     *
     * @param userId      로그인한 userId
     * @param lastEventId 마지막으로 발생한 eventId
     * @return 서버에서 클라이언트와 매핑되는 Sse 통신 객체
     */
    public SseEmitter subscribe(Long userId, String lastEventId) {

        // 데이터의 유실 시점을 파악하기 위해 시간을 함께 저장함
        String id = userId + "_" + System.currentTimeMillis();

        SseEmitter emitter = emitterRepository.save(id, new SseEmitter(DEFAULT_TIMEOUT));

        // SseEmitter 에러가 발생했을 경우, emitter 삭제
        emitter.onCompletion(() -> emitterRepository.deleteById(id));
        emitter.onTimeout(() -> emitterRepository.deleteById(id));

        // 유실된 데이터가 있다면 데이터를 찾아 다시 클라이언트에게 전송
        if (!lastEventId.isEmpty()) {
            Map<String, Object> events = emitterRepository.findAllEventCacheStartWithId(
                    String.valueOf(userId));
            events.entrySet().stream()
                    .filter(entry -> lastEventId.compareTo(entry.getKey()) < 0)
                    .forEach(entry -> sendToClient(emitter, entry.getKey(), entry.getValue()));
        }
        return emitter;
    }

    // 알림을 만들어 로그인한 사용자에게 데이터 전송
    public void send(User receiver, String content) {
        Notification notification = createNotification(receiver, content);
        String userId = String.valueOf(receiver.getId());

        // 로그인한 유저의 SseEmitter 가져오기
        Map<String, SseEmitter> sseEmitter = emitterRepository.findAllStartWithId(userId);
        sseEmitter.forEach(
                (key, emitter) -> {
                    // 유실된 데이터를 처리하기 위해 데이터 캐시 저장
                    emitterRepository.saveEventCache(key, notification);
                    // 데이터 전송
                    sendToClient(emitter, key, NotificationResponse.from(notification));
                }
        );
    }

    // Notification 객체 생성
    private Notification createNotification(User receiver, String content) {
        return Notification.builder()
                .receiver(receiver)
                .content(content)
                .build();
    }

    // 클라이언트에게 데이터 전송
    private void sendToClient(SseEmitter emitter, String id, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(id)
                    .name("chat-termination")
                    .data(data));
        } catch (IOException e) {
            emitterRepository.deleteById(id);
            throw new RuntimeException("연결 오류");
        }
    }
}

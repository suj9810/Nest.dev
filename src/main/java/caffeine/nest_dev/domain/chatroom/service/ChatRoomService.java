package caffeine.nest_dev.domain.chatroom.service;

import caffeine.nest_dev.common.enums.ErrorCode;
import caffeine.nest_dev.common.exception.BaseException;
import caffeine.nest_dev.domain.chatroom.dto.request.CreateChatRoomRequestDto;
import caffeine.nest_dev.domain.chatroom.dto.response.ChatRoomResponseDto;
import caffeine.nest_dev.domain.chatroom.dto.response.MessageDto;
import caffeine.nest_dev.domain.chatroom.entity.ChatRoom;
import caffeine.nest_dev.domain.chatroom.repository.ChatRoomRepository;
import caffeine.nest_dev.domain.chatroom.scheduler.service.ChatRoomTerminationSchedulerService;
import caffeine.nest_dev.domain.reservation.entity.Reservation;
import caffeine.nest_dev.domain.reservation.repository.ReservationRepository;
import caffeine.nest_dev.domain.user.entity.User;
import caffeine.nest_dev.domain.user.service.UserService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ReservationRepository reservationRepository;
    private final UserService userService;

    private final ChatRoomTerminationSchedulerService schedulerService;

    // 채팅방 생성
    @Transactional
    public ChatRoomResponseDto createChatRooms(CreateChatRoomRequestDto requestDto) {

        // 예약이 유효한지 확인
        Reservation reservation = reservationRepository.findById(requestDto.getReservationId()).orElseThrow(
                () -> new BaseException(ErrorCode.RESERVATION_NOT_FOUND)
        );

        // 예약이 결제된 상태에만 채팅방 생성 가능
//        if (!ReservationStatus.PAID.equals(reservation.getReservationStatus())) {
//            throw new BaseException(ErrorCode.CHATROOM_NOT_CREATED);
//        }

//        if (!reservation.getMentor().getId().equals(userId) && !reservation.getMentee().getId().equals(userId)) {
//            throw new IllegalArgumentException("접근 권한이 없습니다.");
//        }

        // 채팅방이 이미 존재하는 경우 기존의 채팅방을 반환
        Optional<ChatRoom> existChatRoom = chatRoomRepository.findByReservationId(reservation.getId());
        if (existChatRoom.isPresent()) {
            return ChatRoomResponseDto.of(existChatRoom.get());
        }

        // 멘토, 멘티 정보 추출
        User mentor = reservation.getMentor();
        User mentee = reservation.getMentee();
        log.info("mentorID = {}", mentor.getId());
        log.info("menteeID = {}", mentee.getId());

        ChatRoom chatRoom = ChatRoom.builder()
                .mentor(mentor)
                .mentee(mentee)
                .reservation(reservation)
                .isClosed(false)
                .build();

        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
        log.info("채팅방 : chatRoomId = {}", chatRoom.getId());

        // 채팅방 자동 종료 작업 등록
        schedulerService.registerChatRoomCloseSchedule(reservation.getId(), reservation.getReservationEndAt());
        return ChatRoomResponseDto.of(savedChatRoom);
    }

    // 채팅방 목록 조회
    @Transactional(readOnly = true)
    public List<ChatRoomResponseDto> findAllChatRooms(Long userId) {

        List<ChatRoom> findChatRoomList = chatRoomRepository.findAllByMentorIdOrMenteeId(userId, userId);

        return findChatRoomList.stream().map(ChatRoomResponseDto::of)
                .toList();
    }

    // 채팅 내역 조회
    public Slice<MessageDto> findAllMessage(Long id, Long chatRoomId, Long lastMessageId, Pageable pageable) {
        Long userId = userService.findByIdAndIsDeletedFalseOrElseThrow(id).getId();

        Slice<MessageDto> messageDtos = chatRoomRepository.findAllMessagesByChatRoomId(chatRoomId, lastMessageId,
                pageable);

        for (MessageDto messageDto : messageDtos.getContent()) {
            if (messageDto.getSenderId().equals(userId)) {
                messageDto.setMine(true);
            }
        }

        return messageDtos;
    }


}

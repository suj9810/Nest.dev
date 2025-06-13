package caffeine.nest_dev.domain.reservation.service;

import caffeine.nest_dev.common.dto.PagingResponse;
import caffeine.nest_dev.common.enums.ErrorCode;
import caffeine.nest_dev.common.exception.BaseException;
import caffeine.nest_dev.domain.chatroom.scheduler.service.ChatRoomSchedulerService;
import caffeine.nest_dev.domain.reservation.dto.request.ReservationCancelRequestDto;
import caffeine.nest_dev.domain.reservation.dto.request.ReservationRequestDto;
import caffeine.nest_dev.domain.reservation.dto.response.ReservationResponseDto;
import caffeine.nest_dev.domain.reservation.entity.Reservation;
import caffeine.nest_dev.domain.reservation.repository.ReservationRepository;
import caffeine.nest_dev.domain.user.entity.User;
import caffeine.nest_dev.domain.user.enums.UserRole;
import caffeine.nest_dev.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserService userService;

    private final ChatRoomSchedulerService chatRoomSchedulerService;

    @Transactional
    public ReservationResponseDto save(Long userId, ReservationRequestDto requestDto) {

        boolean exists = reservationRepository.existsByMentorOrMenteeAndTime(
                requestDto.getMentor(), userId, requestDto.getReservationStartAt(),
                requestDto.getReservationEndAt());

        if (exists) {
            throw new BaseException(ErrorCode.DUPLICATED_RESERVATION);
        }

        User mentor = userService.findByIdAndIsDeletedFalseOrElseThrow(requestDto.getMentor());

        // mentorId 로 찾은 mentor 가 mentor 인지 검증
        if (!mentor.getUserRole().equals(UserRole.MENTOR)) {
            throw new BaseException(ErrorCode.INVALID_ROLE);
        }

        User mentee = userService.findByIdAndIsDeletedFalseOrElseThrow(userId);

        Reservation reservation = reservationRepository.save(requestDto.toEntity(mentor, mentee));

//        chatRoomSchedulerService.registerChatRoomSchedule(
//                reservation.getId(),
//                reservation.getReservationStartAt()
//        );

        return ReservationResponseDto.of(reservation);

    }

    @Transactional(readOnly = true)
    public PagingResponse<ReservationResponseDto> getReservationList(Long userId,
            Pageable pageable) {

        Page<Reservation> reservationPage = reservationRepository.findByMentorIdOrMenteeId(userId,
                userId, pageable);
        Page<ReservationResponseDto> responseDtos = reservationPage.map(ReservationResponseDto::of);

        return PagingResponse.from(responseDtos);
    }

    @Transactional(readOnly = true)
    public ReservationResponseDto getReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getMentee().getId().equals(userId)) {
            throw new BaseException(ErrorCode.NO_PERMISSION);
        }

        return ReservationResponseDto.of(reservation);
    }

    @Transactional
    public void update(Long userId, Long reservationId,
            ReservationCancelRequestDto cancelRequestDto) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getMentee().getId().equals(userId)) {
            throw new BaseException(ErrorCode.NO_PERMISSION);
        }

        reservation.update(cancelRequestDto);
    }


}

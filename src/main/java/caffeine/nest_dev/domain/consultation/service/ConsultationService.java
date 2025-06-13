package caffeine.nest_dev.domain.consultation.service;

import caffeine.nest_dev.common.enums.ErrorCode;
import caffeine.nest_dev.common.exception.BaseException;
import caffeine.nest_dev.domain.consultation.dto.request.ConsultationRequestDto;
import caffeine.nest_dev.domain.consultation.dto.response.AvailableSlotDto;
import caffeine.nest_dev.domain.consultation.dto.response.ConsultationResponseDto;
import caffeine.nest_dev.domain.consultation.entity.Consultation;
import caffeine.nest_dev.domain.consultation.repository.ConsultationRepository;
import caffeine.nest_dev.domain.reservation.entity.Reservation;
import caffeine.nest_dev.domain.reservation.enums.ReservationStatus;
import caffeine.nest_dev.domain.reservation.repository.ReservationRepository;
import caffeine.nest_dev.domain.user.entity.User;
import caffeine.nest_dev.domain.user.service.UserService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final UserService userService;
    private final ReservationRepository reservationRepository;

    @Transactional
    public ConsultationResponseDto createConsultation(Long userId,
            ConsultationRequestDto requestDto) {

        if (consultationRepository.existsConsultation(userId, requestDto.getStartAt(),
                requestDto.getEndAt())) {
            throw new BaseException(ErrorCode.DUPLICATE_CONSULTATION_TIME);
        }

        User user = userService.findByIdAndIsDeletedFalseOrElseThrow(userId);

        Consultation consultation = consultationRepository.save(
                requestDto.toEntity(user));

        return ConsultationResponseDto.of(consultation);

    }

    public List<ConsultationResponseDto> getMyConsultations(Long userId) {
        List<Consultation> list = consultationRepository.findByMentorId(userId);
        return list.stream().map(ConsultationResponseDto::of).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotDto> getAvailableConsultationSlots(Long mentorId) {
        // 멘토가 등록한 전체 시간 범위 조회
        List<Consultation> availableTime = consultationRepository.findByMentorId(mentorId);

        if (availableTime.isEmpty()) {
            throw new BaseException(ErrorCode.CONSULTATION_NOT_FOUND);
        }

        // 예약이 취소된 것들을 제외한 모든 예약 조회
        List<Reservation> bookedReservations = reservationRepository.findByMentorIdAndReservationStatusNot(
                mentorId, ReservationStatus.CANCELED);

        // 10분 단위 시작 시간
        List<LocalDateTime> availableSlots = new ArrayList<>();

        // 전체 상담 시간을 순회
        for (Consultation time : availableTime) {
            LocalDateTime start = time.getStartAt();
            LocalDateTime end = time.getEndAt();

            // 시작 시간부터 10분 단위로 이동
            while (start.isBefore(end)) {
                LocalDateTime plussed = start.plusMinutes(10);

                // 종단 포인트
                if (plussed.isAfter(end)) {
                    break;
                }

                // 예약된 시간과 겹치는지 확인
                if (!isBooked(start, plussed, bookedReservations)) {
                    availableSlots.add(start);
                }

                // 그 다음 10분으로 이동
                start = start.plusMinutes(10);
            }
        }
        return availableSlots.stream()
                .map(startTime -> new AvailableSlotDto(startTime, startTime.plusMinutes(10)))
                .collect(Collectors.toList());
    }


    @Transactional
    public ConsultationResponseDto updateConsultation(Long userId, Long consultationId,
            ConsultationRequestDto requestDto) {

        if (consultationRepository.existsConsultation(userId, requestDto.getStartAt(),
                requestDto.getEndAt())) {
            throw new BaseException(ErrorCode.DUPLICATE_CONSULTATION_TIME);
        }

        Consultation consultation = consultationRepository.findByIdAndMentorId(consultationId,
                        userId)
                .orElseThrow(() -> new BaseException(ErrorCode.CONSULTATION_NOT_FOUND));

        consultation.update(requestDto.getStartAt(), requestDto.getEndAt());

        return ConsultationResponseDto.of(consultation);
    }

    @Transactional
    public void deleteConsultation(Long userId, Long consultationId) {

        Consultation consultation = consultationRepository.findByIdAndMentorId(consultationId,
                        userId)
                .orElseThrow(() -> new BaseException(ErrorCode.CONSULTATION_NOT_FOUND));

        consultationRepository.delete(consultation);
    }

    private boolean isBooked(LocalDateTime start, LocalDateTime plussed,
            List<Reservation> bookedReservations) {
        for (Reservation reservation : bookedReservations) {
            if (start.isBefore(reservation.getReservationEndAt())
                    && reservation.getReservationStartAt().isBefore(plussed)) {
                return true;
            }
        }
        return false;
    }
}

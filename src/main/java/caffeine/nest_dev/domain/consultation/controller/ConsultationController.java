package caffeine.nest_dev.domain.consultation.controller;

import caffeine.nest_dev.common.dto.CommonResponse;
import caffeine.nest_dev.common.enums.SuccessCode;
import caffeine.nest_dev.domain.consultation.dto.request.ConsultationRequestDto;
import caffeine.nest_dev.domain.consultation.dto.response.AvailableSlotDto;
import caffeine.nest_dev.domain.consultation.dto.response.ConsultationResponseDto;
import caffeine.nest_dev.domain.consultation.service.ConsultationService;
import caffeine.nest_dev.domain.user.entity.UserDetailsImpl;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ConsultationController {

    private final ConsultationService consultationService;

    /**
     * 상감 가능 시간 등록
     * */
    @PostMapping("/mentor/consultations")
    public ResponseEntity<CommonResponse<ConsultationResponseDto>> createConsultation(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestBody ConsultationRequestDto consultationRequestDto
    ) {
        ConsultationResponseDto consultation = consultationService.createConsultation(
                userDetails.getId(), consultationRequestDto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.of(SuccessCode.SUCCESS_CONSULTATION_CREATED, consultation));

    }

    /**
     * 내가 등록한 상담 시간 조회
     * */
    @GetMapping("/mentor/consultations")
    public ResponseEntity<CommonResponse<List<ConsultationResponseDto>>> getMyConsultations(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<ConsultationResponseDto> myConsultations = consultationService.getMyConsultations(
                userDetails.getId());

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.of(SuccessCode.SUCCESS_CONSULTATION_READ, myConsultations));
    }

    /**
     * MENTEE 가 보는 예약된 시간을 제외한 상담 가능한 시간 조회
     * */
    @GetMapping("/mentor/{mentorId}/availableConsultations")
    public ResponseEntity<CommonResponse<List<AvailableSlotDto>>> getSlots(
            @PathVariable Long mentorId // 멘토의 ID
    ) {
        List<AvailableSlotDto> responseDto = consultationService.getAvailableConsultationSlots(mentorId);
        return ResponseEntity.ok(CommonResponse.of(SuccessCode.SUCCESS_SLOTS_READ, responseDto));
    }

    /**
     * 상담 시간 수정
     * */
    @PatchMapping("/mentor/consultations/{consultationId}")
    public ResponseEntity<CommonResponse<ConsultationResponseDto>> updateConsultation(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long consultationId,
            @RequestBody ConsultationRequestDto requestDto) {
        ConsultationResponseDto updated = consultationService.updateConsultation(userDetails.getId(), consultationId, requestDto);
        return ResponseEntity.ok(CommonResponse.of(SuccessCode.SUCCESS_CONSULTATION_UPDATED, updated));
    }

    @DeleteMapping("/mentor/consultations/{consultationId}")
    public ResponseEntity<CommonResponse<Void>> deleteConsultation(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long consultationId) {
        consultationService.deleteConsultation(userDetails.getId(), consultationId);
        return ResponseEntity.ok(CommonResponse.of(SuccessCode.SUCCESS_CONSULTATION_DELETED));
    }
}

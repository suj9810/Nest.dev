package caffeine.nest_dev.domain.reservation.repository;

import caffeine.nest_dev.domain.reservation.entity.Reservation;
import caffeine.nest_dev.domain.reservation.enums.ReservationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Page<Reservation> findByMentorIdOrMenteeId(Long mentorId, Long menteeId, Pageable pageable);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Reservation r
            WHERE (r.mentor.id = :mentorId OR r.mentee.id = :menteeId)
            AND (r.reservationStartAt < :endAt AND r.reservationEndAt > :startAt)""")
    boolean existsByMentorOrMenteeAndTime(@Param("mentorId") Long mentorId,
            @Param("menteeId") Long menteeId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    List<Reservation> findByMentorIdAndReservationStatusNot(Long mentorId,
            ReservationStatus status);

}

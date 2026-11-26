package com.dippie.repository.profile;

import com.dippie.model.profile.ScreenTimeLedgerEntry;
import com.dippie.model.profile.ScreenTimeLedgerKind;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// repo sổ cái thời gian xem của hồ sơ trẻ em, gom tổng theo ngày giờ Việt Nam
public interface ScreenTimeLedgerRepository extends JpaRepository<ScreenTimeLedgerEntry, Long> {

    @Query("""
            select e.kind as kind, sum(e.seconds) as totalSeconds
            from ScreenTimeLedgerEntry e
            where e.profileId = :profileId and e.usageDay = :usageDay
            group by e.kind
            """)
    List<KindTotal> sumByProfileIdAndUsageDay(@Param("profileId") Long profileId, @Param("usageDay") LocalDate usageDay);

    @Query("""
            select e.profileId as profileId, e.kind as kind, sum(e.seconds) as totalSeconds
            from ScreenTimeLedgerEntry e
            where e.profileId in :profileIds and e.usageDay = :usageDay
            group by e.profileId, e.kind
            """)
    List<ProfileKindTotal> sumByProfileIdsAndUsageDay(
            @Param("profileIds") Collection<Long> profileIds,
            @Param("usageDay") LocalDate usageDay
    );

    Optional<ScreenTimeLedgerEntry> findTopByProfileIdAndKindOrderByCreatedAtDescIdDesc(
            Long profileId,
            ScreenTimeLedgerKind kind
    );

    void deleteByProfileId(Long profileId);

    @Modifying
    @Query("""
            delete from ScreenTimeLedgerEntry e
            where e.profileId in (select c.id from ChildProfile c where c.userId = :userId)
            """)
    void deleteByUserId(@Param("userId") Long userId);

    @Query("""
            select e.usageDay as usageDay, e.kind as kind, sum(e.seconds) as totalSeconds, count(e.id) as entryCount
            from ScreenTimeLedgerEntry e
            where e.profileId = :profileId and e.usageDay >= :startDay and e.usageDay <= :endDay
            group by e.usageDay, e.kind
            """)
    List<DayKindStats> summarizeByProfileIdAndUsageDayBetween(
            @Param("profileId") Long profileId,
            @Param("startDay") LocalDate startDay,
            @Param("endDay") LocalDate endDay
    );

    @Query("""
            select e.profileId as profileId, e.usageDay as usageDay, e.kind as kind,
                   sum(e.seconds) as totalSeconds, count(e.id) as entryCount
            from ScreenTimeLedgerEntry e
            where e.profileId in :profileIds and e.usageDay >= :startDay and e.usageDay <= :endDay
            group by e.profileId, e.usageDay, e.kind
            """)
    List<ProfileDayKindStats> summarizeByProfileIdsAndUsageDayBetween(
            @Param("profileIds") Collection<Long> profileIds,
            @Param("startDay") LocalDate startDay,
            @Param("endDay") LocalDate endDay
    );

    interface KindTotal {
        ScreenTimeLedgerKind getKind();
        Long getTotalSeconds();
    }

    interface ProfileKindTotal {
        Long getProfileId();
        ScreenTimeLedgerKind getKind();
        Long getTotalSeconds();
    }

    interface DayKindStats {
        LocalDate getUsageDay();
        ScreenTimeLedgerKind getKind();
        Long getTotalSeconds();
        Long getEntryCount();
    }

    interface ProfileDayKindStats {
        Long getProfileId();
        LocalDate getUsageDay();
        ScreenTimeLedgerKind getKind();
        Long getTotalSeconds();
        Long getEntryCount();
    }
}

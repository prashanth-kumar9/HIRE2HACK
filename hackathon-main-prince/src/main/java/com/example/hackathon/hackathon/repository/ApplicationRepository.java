package com.example.hackathon.hackathon.repository;

import com.example.hackathon.hackathon.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    @Query("SELECT a FROM Application a WHERE a.selected = true AND a.hrScheduled IS NOT NULL AND " +
           "(a.smsSent = false OR a.emailSent24Hrs = false OR a.emailSent48Hrs = false) ORDER BY a.createdAt ASC")
    List<Application> findSelectedApplicationsWithPendingNotifications();

    @Query("SELECT a FROM Application a WHERE a.id = :id AND a.selected = true AND a.hrScheduled IS NOT NULL")
    Optional<Application> findSelectedById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Application a SET a.smsSent = true, a.smsSentTime = :timestamp WHERE a.id = :id")
    int markSmsAsSent(@Param("id") Long id, @Param("timestamp") java.time.LocalDateTime timestamp);

    @Modifying
    @Query("UPDATE Application a SET a.emailSent24Hrs = true, a.emailSent24HrsTime = :timestamp WHERE a.id = :id")
    int markEmail24HrsAsSent(@Param("id") Long id, @Param("timestamp") java.time.LocalDateTime timestamp);

    @Modifying
    @Query("UPDATE Application a SET a.emailSent48Hrs = true, a.emailSent48HrsTime = :timestamp WHERE a.id = :id")
    int markEmail48HrsAsSent(@Param("id") Long id, @Param("timestamp") java.time.LocalDateTime timestamp);

    @Query("SELECT a FROM Application a WHERE a.selected = true AND a.hrScheduled IS NOT NULL AND a.smsSent = false ORDER BY a.createdAt ASC")
    List<Application> findPendingSmsNotifications();

    @Query("SELECT a FROM Application a WHERE a.selected = true AND a.hrScheduled IS NOT NULL AND a.smsSent = true AND a.emailSent24Hrs = false AND a.smsSentTime <= :cutoffTime ORDER BY a.createdAt ASC")
    List<Application> findPending24HrEmailNotifications(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("SELECT a FROM Application a WHERE a.selected = true AND a.hrScheduled IS NOT NULL AND a.emailSent24Hrs = true AND a.emailSent48Hrs = false AND a.emailSent24HrsTime <= :cutoffTime ORDER BY a.createdAt ASC")
    List<Application> findPending48HrEmailNotifications(@Param("cutoffTime") LocalDateTime cutoffTime);
}

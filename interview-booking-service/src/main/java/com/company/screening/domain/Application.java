package com.company.screening.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "applications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Application {

    @Id
    private String applicationId;

    @Indexed
    private String candidateId;
    private String candidateName;
    private String candidateEmail;
    private String candidatePhone;

    private String recruiterId;
    private String recruiterEmail;

    private ApplicationStatus status;

    @Indexed(unique = true)
    private String bookingToken;
    private Instant tokenExpiresAt;

    @Builder.Default
    private int missedAttemptsCount = 0;

    private Integer screeningScore;
    private String screeningFeedback;

    @Builder.Default
    private NudgeStage nudgeStage = NudgeStage.NONE;
    private Instant lastNudgeSentAt;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

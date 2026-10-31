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
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "bookings")
@CompoundIndex(name = "status_time_idx", def = "{'status': 1, 'bookingDateTime': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    private String bookingId;

    @Indexed
    private String candidateId;

    @Indexed
    private String applicationId;

    private BookingStatus status;

    @Indexed
    private Instant bookingDateTime;

    @Builder.Default
    private int durationMinutes = 30;

    @Builder.Default
    private int bufferTimeMinutes = 15;

    private Instant callInitiatedAt;
    private Instant callCompletedAt;

    @Builder.Default
    private boolean agentDelayNotified = false;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public Instant getEndTime() {
        if (bookingDateTime == null) {
            return null;
        }
        return bookingDateTime.plusSeconds(durationMinutes * 60L);
    }

    public Instant getEffectiveEndTime() {
        if (bookingDateTime == null) {
            return null;
        }
        return bookingDateTime.plusSeconds((durationMinutes + bufferTimeMinutes) * 60L);
    }
}

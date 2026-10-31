package com.company.screening.common;

import com.company.screening.application.dsa.SlotCapacityAlgorithm;
import com.company.screening.common.exception.InvalidBookingStateException;
import com.company.screening.common.exception.ResourceNotFoundException;
import com.company.screening.common.exception.SlotCapacityExceededException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://company.com/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(SlotCapacityExceededException.class)
    public ProblemDetail handleCapacityExceeded(SlotCapacityExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Slot Capacity Exceeded");
        problem.setType(URI.create("https://company.com/errors/capacity-exceeded"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("maxAllowedSimultaneousInterviews", SlotCapacityAlgorithm.MAX_CONCURRENT_INTERVIEWS);
        return problem;
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    public ProblemDetail handleInvalidState(InvalidBookingStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Booking State");
        problem.setType(URI.create("https://company.com/errors/invalid-state"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://company.com/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Concurrent modification conflict. Please retry your request.");
        problem.setTitle("Concurrent Modification Conflict");
        problem.setType(URI.create("https://company.com/errors/concurrent-modification"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}

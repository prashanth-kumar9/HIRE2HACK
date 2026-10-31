package com.company.screening.common.exception;

public class SlotCapacityExceededException extends RuntimeException {
    public SlotCapacityExceededException(String message) {
        super(message);
    }
}

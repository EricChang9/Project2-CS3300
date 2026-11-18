package edu.group17.roommates.household;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = HouseholdController.class)
public class HouseholdExceptionHandler {
    @ExceptionHandler(HouseholdStorageException.class)
    public ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Household storage is unavailable");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidName() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid household name");
    }
}

package edu.group17.roommates.household;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import edu.group17.roommates.expense.ExpenseController;

@RestControllerAdvice(assignableTypes = {HouseholdController.class, ExpenseController.class, edu.group17.roommates.invitation.InvitationController.class,
        edu.group17.roommates.account.CurrentUserController.class})
public class HouseholdExceptionHandler {
    // Do not let Spring's default validation log include submitted invitation codes.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request fields");
    }

    @ExceptionHandler(HouseholdStorageException.class)
    public ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Household storage is unavailable");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidName() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid household name");
    }
}

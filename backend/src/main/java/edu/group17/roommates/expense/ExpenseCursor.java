package edu.group17.roommates.expense;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/** Opaque keyset cursor for descending expense-date and document-ID order. */
record ExpenseCursor(String householdId, LocalDate expenseDate, String expenseId) {
    private static final int MAX_ENCODED_LENGTH = 512;

    static ExpenseCursor decode(String value, String householdId) {
        if (value == null) return null;
        if (value.isBlank() || value.length() > MAX_ENCODED_LENGTH) throw invalid();
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\n", -1);
            if (parts.length != 3 || !householdId.equals(parts[0])
                    || !parts[2].matches("[A-Za-z0-9_-]{1,128}")) throw invalid();
            return new ExpenseCursor(householdId, LocalDate.parse(parts[1]), parts[2]);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid();
        }
    }

    String encode() {
        String value = householdId + "\n" + expenseDate + "\n" + expenseId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid expense history cursor");
    }
}

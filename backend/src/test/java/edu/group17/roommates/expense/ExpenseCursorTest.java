package edu.group17.roommates.expense;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseCursorTest {
    @Test void roundTripsCursorAndScopesItToItsHousehold() {
        var cursor = new ExpenseCursor("home", LocalDate.parse("2026-11-18"), "expense-id_1");
        assertEquals(cursor, ExpenseCursor.decode(cursor.encode(), "home"));
        assertThrows(IllegalArgumentException.class, () -> ExpenseCursor.decode(cursor.encode(), "other-home"));
    }

    @Test void rejectsMalformedAndOversizedCursors() {
        assertThrows(IllegalArgumentException.class, () -> ExpenseCursor.decode("%%%", "home"));
        assertThrows(IllegalArgumentException.class, () -> ExpenseCursor.decode("", "home"));
        assertThrows(IllegalArgumentException.class, () -> ExpenseCursor.decode("x".repeat(513), "home"));
    }
}

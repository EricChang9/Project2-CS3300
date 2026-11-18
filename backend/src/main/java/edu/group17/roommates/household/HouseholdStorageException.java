package edu.group17.roommates.household;

/** The caller receives a generic 503, never database internals or credentials. */
public class HouseholdStorageException extends RuntimeException {
    public HouseholdStorageException(Throwable cause) { super("Household storage is unavailable", cause); }
}

package edu.group17.roommates.balance;

/** A successfully recorded payment from a debtor to a creditor. */
public record Settlement(String fromUid, String toUid, String currency, long amountCents) {}

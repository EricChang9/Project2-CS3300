package edu.group17.roommates.balance;

import java.util.List;

/** Read seam for successful payments; payments reduce balances without modifying original expenses. */
public interface SettlementStore {
    List<Settlement> listForHousehold(String householdId, String uid);
}

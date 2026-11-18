package edu.group17.roommates.household;

import java.util.List;

public interface HouseholdStore {
    Household create(String name, String creatorUid);
    List<Household> listForMember(String uid);
}

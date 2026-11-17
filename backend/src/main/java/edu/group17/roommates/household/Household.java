package edu.group17.roommates.household;

import java.time.Instant;
import java.util.List;

public record Household(String id, String name, String createdBy, Instant createdAt, List<Member> members) {
    public Household { members = List.copyOf(members); }
    public record Member(String uid, Instant joinedAt) {}
}

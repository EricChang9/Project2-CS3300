package edu.group17.roommates.expense.split;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Splits an expense evenly, assigning leftover cents in participant order. */
public final class EqualSplitStrategy implements ExpenseSplitStrategy<List<String>> {
    @Override
    public Map<String, Long> split(long totalCents, List<String> participants) {
        if (totalCents < 0) {
            throw new IllegalArgumentException("Expense total cannot be negative");
        }
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("Participants cannot be null or empty");
        }

        Set<String> uniqueParticipants = new HashSet<>();
        for (String participant : participants) {
            if (participant == null || participant.isBlank()) {
                throw new IllegalArgumentException("Participant IDs cannot be null or blank");
            }
            if (!uniqueParticipants.add(participant)) {
                throw new IllegalArgumentException("Participant IDs must be unique");
            }
        }

        long baseShare = totalCents / participants.size();
        long leftoverCents = totalCents % participants.size();
        Map<String, Long> shares = new LinkedHashMap<>();
        for (int i = 0; i < participants.size(); i++) {
            shares.put(participants.get(i), baseShare + (i < leftoverCents ? 1 : 0));
        }
        return Collections.unmodifiableMap(shares);
    }
}

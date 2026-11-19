package edu.group17.roommates.balance;

import com.google.cloud.firestore.DocumentSnapshot;
import edu.group17.roommates.expense.ExpenseStore;
import edu.group17.roommates.household.HouseholdAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {
    @Mock ExpenseStore expenses;
    @Mock SettlementStore settlements;
    @Mock HouseholdAccessGuard access;
    @Mock DocumentSnapshot household;

    @Test void loadsExpensesAndSettlementInputsOnlyAfterHouseholdMembershipIsConfirmed() {
        when(access.requireMember("home", "alice")).thenReturn(household);
        when(household.get("memberIds")).thenReturn(List.of("alice", "bob"));
        when(expenses.listAllForBalances("home", "alice")).thenReturn(List.of());
        when(settlements.listForHousehold("home", "alice")).thenReturn(List.of());

        var result = service().get("home", "alice");

        assertEquals("home", result.householdId());
        assertEquals(List.of("alice", "bob"), result.members().stream()
                .map(HouseholdBalance.MemberBalance::memberId).toList());
        verify(access).requireMember("home", "alice");
        verify(expenses).listAllForBalances("home", "alice");
        verify(settlements).listForHousehold("home", "alice");
    }

    @Test void rejectsNonMembersBeforeReadingAnyHouseholdBalanceData() {
        when(access.requireMember("other-home", "alice"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service().get("other-home", "alice")).getStatusCode().value());
        verifyNoInteractions(expenses, settlements);
    }

    @Test void mapsBalanceOverflowToAnUnprocessableResponse() {
        when(access.requireMember("home", "alice")).thenReturn(household);
        when(household.get("memberIds")).thenReturn(List.of("alice", "bob"));
        when(expenses.listAllForBalances("home", "alice")).thenThrow(new ArithmeticException("overflow"));

        var failure = assertThrows(ResponseStatusException.class, () -> service().get("home", "alice"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, failure.getStatusCode());
        verifyNoInteractions(settlements);
    }

    private BalanceService service() {
        return new BalanceService(expenses, settlements, access, new BalanceCalculator());
    }
}

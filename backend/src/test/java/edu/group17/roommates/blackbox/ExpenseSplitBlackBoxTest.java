package edu.group17.roommates.blackbox;

import edu.group17.roommates.expense.split.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Requirement-derived examples exercising only the documented strategy interface. */
class ExpenseSplitBlackBoxTest {
    private final ExpenseSplitStrategy<List<String>> equal = new EqualSplitStrategy();
    private final ExpenseSplitStrategy<Map<String, Long>> custom = new CustomSplitStrategy();
    private void observed(String id, Object value) { System.out.println(id+" actual="+value); }

    @Test void BBU01_divisibleExpense() {
        var result=equal.split(2400,List.of("a","b","c")); observed("BBU01",result);
        assertEquals(Map.of("a",800L,"b",800L,"c",800L),result);
    }
    @Test void BBU02_remainderFollowsParticipantOrder() {
        var result=equal.split(13,List.of("z","a","m","b","c")); observed("BBU02",result);
        assertEquals(Map.of("z",3L,"a",3L,"m",3L,"b",2L,"c",2L),result);
        assertEquals(13,result.values().stream().mapToLong(Long::longValue).sum());
    }
    @Test void BBU03_lessThanOneCentPerPerson() {
        var result=equal.split(2,List.of("a","b","c","d")); observed("BBU03",result);
        assertEquals(Map.of("a",1L,"b",1L,"c",0L,"d",0L),result);
    }
    @Test void BBU04_singleParticipantAndZero() {
        assertEquals(Map.of("a",123L),equal.split(123,List.of("a")));
        var result=equal.split(0,List.of("a","b")); observed("BBU04",result);
        assertEquals(Map.of("a",0L,"b",0L),result);
    }
    @Test void BBU05_customAmountsPreserveTheTotal() {
        var result=custom.split(1001,Map.of("a",0L,"b",1001L)); observed("BBU05",result);
        assertEquals(Map.of("a",0L,"b",1001L),result);
    }
    @Test void BBU06_customUnderAndOverAllocationAreRejected() {
        assertThrows(IllegalArgumentException.class,()->custom.split(100,Map.of("a",99L)));
        assertThrows(IllegalArgumentException.class,()->custom.split(100,Map.of("a",101L)));
        observed("BBU06","IllegalArgumentException for 99 and 101 against total 100");
    }
    @Test void BBU07_negativeAndNoParticipantsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->equal.split(-1,List.of("a")));
        assertThrows(IllegalArgumentException.class,()->equal.split(1,List.of()));
        assertThrows(IllegalArgumentException.class,()->custom.split(1,Map.of()));
        observed("BBU07","negative and empty inputs rejected");
    }
    @Test void BBU08_largeIntegerMoneyIsExact() {
        var result=equal.split(9_007_199_254_740_993L,List.of("a","b")); observed("BBU08",result);
        assertEquals(Map.of("a",4_503_599_627_370_497L,"b",4_503_599_627_370_496L),result);
    }
}

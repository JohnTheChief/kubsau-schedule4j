package me.heyjohn.kubsau;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграционные тесты, ходящие на настоящий сайт.
 * Включаются флагом {@code -Dkubsau.live=true}.
 */
@EnabledIfSystemProperty(named = "kubsau.live", matches = "true")
class KubSauScheduleLiveTest {

    private final KubSauSchedule client = new KubSauSchedule();

    @Test
    void queryGroups() {
        List<Group> groups = client.queryGroups("БИ");

        assertFalse(groups.isEmpty());
        assertTrue(groups.stream().allMatch(g -> g.name().startsWith("БИ")));
        assertTrue(groups.stream().allMatch(g -> g.id() != null && !g.id().isBlank()));
    }

    @Test
    void queryGroupsReturnsEmptyForUnknownPrefix() {
        assertEquals(List.of(), client.queryGroups("ZZZZZZ"));
    }

    @Test
    void getSchedule() {
        Schedule schedule = client.getSchedule("БИ2601");

        assertEquals("БИ2601", schedule.group());
        assertEquals(2, schedule.weeks().size());
        assertFalse(schedule.days().isEmpty());
        assertTrue(schedule.lessons().findAny().isPresent());
    }

    @Test
    void getScheduleThrowsForUnknownGroup() {
        assertThrows(GroupNotFoundException.class, () -> client.getSchedule("NOPE9999"));
    }
}

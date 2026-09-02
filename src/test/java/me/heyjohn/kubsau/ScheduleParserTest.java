package me.heyjohn.kubsau;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleParserTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = ScheduleParserTest.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IOException("Ресурс не найден: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesHeader() throws IOException {
        Schedule s = ScheduleParser.parse(resource("bi2601.html"), "БИ2601");

        assertEquals("БИ2601", s.group());
        assertEquals(LocalDate.of(2026, 9, 2), s.date());
        assertEquals(1, s.currentWeekNumber());
        assertEquals(LocalDateTime.of(2026, 9, 2, 16, 57, 5), s.updatedAt());
    }

    @Test
    void parsesTwoWeeksOfSixDays() throws IOException {
        Schedule s = ScheduleParser.parse(resource("bi2601.html"), "БИ2601");

        assertEquals(2, s.weeks().size());
        Week first = s.currentWeek();
        Week second = s.nextWeek().orElseThrow();

        assertEquals(1, first.number());
        assertEquals("Первая неделя", first.title());
        assertEquals(6, first.days().size());
        assertEquals(LocalDate.of(2026, 8, 31), first.firstDate());
        assertEquals(LocalDate.of(2026, 9, 5), first.lastDate());
        assertEquals(DayOfWeek.MONDAY, first.days().get(0).dayOfWeek());

        assertEquals(2, second.number());
        assertEquals("Вторая неделя", second.title());
        assertEquals(6, second.days().size());
        assertEquals(LocalDate.of(2026, 9, 7), second.firstDate());
        assertEquals(LocalDate.of(2026, 9, 12), second.lastDate());

        assertEquals(12, s.days().size());
    }

    @Test
    void marksTodayAndEmptyDays() throws IOException {
        Schedule s = ScheduleParser.parse(resource("bi2601.html"), "БИ2601");

        Day today = s.today().orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 2), today.date());
        assertEquals(1, s.days().stream().filter(Day::today).count());

        Day monday = s.day(LocalDate.of(2026, 8, 31)).orElseThrow();
        assertTrue(monday.isEmpty());
    }

    @Test
    void parsesLectureRow() throws IOException {
        Schedule s = ScheduleParser.parse(resource("bi2601.html"), "БИ2601");
        Day wednesday = s.day(LocalDate.of(2026, 9, 2)).orElseThrow();

        assertEquals(4, wednesday.lessons().size());
        Lesson first = wednesday.lessons().get(0);
        assertEquals(1, first.number());
        assertEquals(LocalTime.of(8, 0), first.start());
        assertEquals(LocalTime.of(9, 30), first.end());
        assertEquals("История России", first.subject());
        assertEquals(LessonType.LECTURE, first.type());
        assertTrue(first.isLecture());
        assertEquals(List.of(new Teacher("Малиш М. А.", null)), first.teachers());
        assertEquals(List.of("302эк"), first.rooms());

        Lesson fourth = wednesday.lessons().get(3);
        assertEquals(4, fourth.number());
        assertEquals(LocalTime.of(13, 50), fourth.start());
        assertEquals("Линейная алгебра и аналитическая геометрия", fourth.subject());
    }

    @Test
    void parsesPracticeWithSubgroups() throws IOException {
        Schedule s = ScheduleParser.parse(resource("bi2601.html"), "БИ2601");
        Day monday = s.day(LocalDate.of(2026, 9, 7)).orElseThrow();

        Lesson programming = monday.lessons().stream()
                .filter(l -> l.subject().equals("Программирование"))
                .findFirst()
                .orElseThrow();

        assertEquals(5, programming.number());
        assertEquals(LocalTime.of(15, 35), programming.start());
        assertEquals(LocalTime.of(17, 5), programming.end());
        assertEquals(LessonType.PRACTICE, programming.type());
        assertFalse(programming.isLecture());
        assertEquals(List.of(
                new Teacher("Самойленкова В. А.", "БИ2601/1"),
                new Teacher("Салий В. В.", "БИ2601/2")
        ), programming.teachers());
        assertEquals("БИ2601/1 Самойленкова В. А.", programming.teachers().get(0).toString());
        assertEquals(List.of("402эк"), programming.rooms());
    }

    @Test
    void lessonNumbersFollowTableSlots() throws IOException {
        Schedule s = ScheduleParser.parse(resource("bi2601.html"), "БИ2601");
        Day monday = s.day(LocalDate.of(2026, 9, 7)).orElseThrow();

        // на этот день первые две пары пустые, занятия начинаются с третьей
        assertEquals(List.of(3, 4, 5), monday.lessons().stream().map(Lesson::number).toList());
        assertEquals(LocalTime.of(11, 30), monday.lessons().get(0).start());
    }

    @Test
    void everyLessonHasSubjectTimeAndRoom() throws IOException {
        Schedule s = ScheduleParser.parse(resource("bi2601.html"), "БИ2601");

        assertTrue(s.lessons().count() > 20);
        s.lessons().forEach(l -> {
            assertFalse(l.subject().isBlank(), "пустая дисциплина: " + l);
            assertTrue(l.start().isBefore(l.end()), "время: " + l);
            assertFalse(l.teachers().isEmpty(), "нет преподавателя: " + l);
            assertFalse(l.rooms().isEmpty(), "нет аудитории: " + l);
            assertTrue(l.number() >= 1 && l.number() <= 6, "номер пары: " + l);
        });
    }

    @Test
    void throwsWhenGroupNotFound() throws IOException {
        String html = resource("not-found.html");
        GroupNotFoundException e = assertThrows(GroupNotFoundException.class,
                () -> ScheduleParser.parse(html, "NOPE9999"));
        assertEquals("NOPE9999", e.getGroup());
    }

    @Test
    void parsesSuggestionsJson() {
        String json = "{\"suggestions\":[{\"value\":\"\\u0411\\u04182301\",\"data\":\"000003858\"},"
                + "{\"value\":\"\\u0411\\u0418\\u041e2601\",\"data\":\"000004616\"}]}";

        List<Group> groups = KubSauSchedule.parseSuggestions(json);

        assertEquals(List.of(
                new Group("БИ2301", "000003858"),
                new Group("БИО2601", "000004616")
        ), groups);
    }

    @Test
    void parsesEmptySuggestions() {
        assertEquals(List.of(), KubSauSchedule.parseSuggestions("{\"suggestions\":[]}"));
        assertEquals(List.of(), KubSauSchedule.parseSuggestions("{}"));
        assertNull(KubSauSchedule.parseSuggestions("{\"suggestions\":[{\"value\":\"X\"}]}").get(0).id());
    }

    @Test
    void rejectsBrokenSuggestionsJson() {
        assertThrows(KubSauException.class, () -> KubSauSchedule.parseSuggestions("<html>"));
    }
}

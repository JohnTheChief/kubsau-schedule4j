package me.heyjohn.kubsau;

import java.time.LocalTime;
import java.util.List;

/**
 * Одно занятие (пара).
 *
 * @param number   номер пары в течение дня, начиная с 1
 * @param start    время начала
 * @param end      время окончания
 * @param subject  название дисциплины
 * @param type     тип занятия
 * @param teachers преподаватели (может быть несколько при делении на подгруппы)
 * @param rooms    аудитории, например {@code 302эк}
 */
public record Lesson(
        int number,
        LocalTime start,
        LocalTime end,
        String subject,
        LessonType type,
        List<Teacher> teachers,
        List<String> rooms
) {
    public Lesson {
        teachers = List.copyOf(teachers);
        rooms = List.copyOf(rooms);
    }

    public boolean isLecture() {
        return type == LessonType.LECTURE;
    }
}

package me.heyjohn.kubsau;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Один учебный день.
 *
 * @param date    дата
 * @param today   помечен ли день сайтом как сегодняшний
 * @param lessons занятия в этот день (пустые слоты не включаются), отсортированы по номеру пары
 */
public record Day(LocalDate date, boolean today, List<Lesson> lessons) {

    public Day {
        lessons = List.copyOf(lessons);
    }

    public DayOfWeek dayOfWeek() {
        return date.getDayOfWeek();
    }

    /** {@code true}, если в этот день нет ни одного занятия. */
    public boolean isEmpty() {
        return lessons.isEmpty();
    }
}

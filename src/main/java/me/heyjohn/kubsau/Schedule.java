package me.heyjohn.kubsau;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Расписание группы на две недели: текущую и следующую.
 *
 * @param group             название группы, как его отдал сайт
 * @param date              дата, на которую сайт построил расписание («сегодня» по мнению сайта)
 * @param currentWeekNumber номер текущей учебной недели по чётности (1 или 2), как указано на сайте
 * @param updatedAt         дата и время последнего обновления расписания на сайте
 * @param weeks             недели: первая — текущая, вторая — следующая
 */
public record Schedule(
        String group,
        LocalDate date,
        int currentWeekNumber,
        LocalDateTime updatedAt,
        List<Week> weeks
) {
    public Schedule {
        weeks = List.copyOf(weeks);
    }

    /** Текущая неделя. */
    public Week currentWeek() {
        return weeks.get(0);
    }

    /** Следующая неделя. */
    public Optional<Week> nextWeek() {
        return weeks.size() > 1 ? Optional.of(weeks.get(1)) : Optional.empty();
    }

    /** Все дни обеих недель подряд. */
    public List<Day> days() {
        return weeks.stream().flatMap(w -> w.days().stream()).toList();
    }

    public Optional<Day> day(LocalDate date) {
        return days().stream().filter(d -> d.date().equals(date)).findFirst();
    }

    /** День, который сайт пометил как сегодняшний. */
    public Optional<Day> today() {
        return days().stream().filter(Day::today).findFirst();
    }

    /** Все занятия обеих недель подряд. */
    public Stream<Lesson> lessons() {
        return days().stream().flatMap(d -> d.lessons().stream());
    }
}

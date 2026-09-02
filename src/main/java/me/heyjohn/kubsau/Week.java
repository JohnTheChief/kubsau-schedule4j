package me.heyjohn.kubsau;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Учебная неделя.
 *
 * @param number номер недели относительно текущей: 1 — текущая, 2 — следующая
 * @param title  заголовок недели с сайта, например {@code Первая неделя}
 * @param days   дни недели с понедельника по субботу (воскресенье сайт не отдаёт)
 */
public record Week(int number, String title, List<Day> days) {

    public Week {
        days = List.copyOf(days);
    }

    public Optional<Day> day(LocalDate date) {
        return days.stream().filter(d -> d.date().equals(date)).findFirst();
    }

    public LocalDate firstDate() {
        return days.get(0).date();
    }

    public LocalDate lastDate() {
        return days.get(days.size() - 1).date();
    }
}

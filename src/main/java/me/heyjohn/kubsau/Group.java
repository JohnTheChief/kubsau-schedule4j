package me.heyjohn.kubsau;

/**
 * Учебная группа из подсказок сайта.
 *
 * @param name название группы, например {@code БИ2601}
 * @param id   внутренний идентификатор группы на сайте, например {@code 000004507}
 */
public record Group(String name, String id) {
}

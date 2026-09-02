package me.heyjohn.kubsau;

import java.util.Optional;

/**
 * Преподаватель на занятии.
 *
 * @param name     ФИО, например {@code Малиш М. А.}
 * @param subgroup подгруппа, если занятие делится на подгруппы (например {@code БИ2601/1}), иначе {@code null}
 */
public record Teacher(String name, String subgroup) {

    /** Подгруппа, если занятие ведётся по подгруппам. */
    public Optional<String> subgroupOpt() {
        return Optional.ofNullable(subgroup);
    }

    @Override
    public String toString() {
        return subgroup == null ? name : subgroup + " " + name;
    }
}

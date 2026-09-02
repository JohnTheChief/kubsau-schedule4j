package me.heyjohn.kubsau;

/** Сайт не вернул расписание для запрошенной группы. */
public class GroupNotFoundException extends KubSauException {

    private final String group;

    public GroupNotFoundException(String group) {
        super("Расписание для группы \"" + group + "\" не найдено");
        this.group = group;
    }

    public String getGroup() {
        return group;
    }
}

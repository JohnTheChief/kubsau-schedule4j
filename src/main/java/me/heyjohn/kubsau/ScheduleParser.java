package me.heyjohn.kubsau;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор HTML-страницы расписания {@code https://s.kubsau.ru/?type_schedule=1&val=<группа>}.
 * <p>
 * Страница содержит два блока: {@code .schedule-first-week} (текущая неделя) и
 * {@code .schedule-second-week} (следующая). Внутри каждого блока дни оформлены как
 * {@code div.card-block.day-YYYY-MM-DD} с таблицей пар. Блок {@code .fast-schedule}
 * (три ближайших дня) дублирует данные недель и не разбирается.
 */
public final class ScheduleParser {

    private static final Pattern DAY_CLASS = Pattern.compile("\\bday-(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern WEEK_NUMBER = Pattern.compile("(\\d+)-я\\s+недел");
    private static final Pattern UPDATED_AT = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}:\\d{2})");
    /** {@code БИ2601/1 Самойленкова В. А.} → подгруппа и ФИО. */
    private static final Pattern SUBGROUP_TEACHER = Pattern.compile("^(\\S+/\\S+)\\s+(.+)$");
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ScheduleParser() {
    }

    /**
     * Разбирает HTML страницы расписания.
     *
     * @param html           содержимое страницы
     * @param requestedGroup группа, которую запрашивали (используется в сообщении об ошибке)
     * @throws GroupNotFoundException если на странице нет расписания
     * @throws KubSauException        если структура страницы не распознана
     */
    public static Schedule parse(String html, String requestedGroup) {
        Document doc = Jsoup.parse(html);

        Element first = doc.selectFirst(".schedule-first-week");
        if (first == null) {
            throw new GroupNotFoundException(requestedGroup);
        }
        Element second = doc.selectFirst(".schedule-second-week");

        Element header = doc.selectFirst("h2.h2-responsive");
        String group = header != null && header.selectFirst("strong") != null
                ? header.selectFirst("strong").text().trim()
                : requestedGroup;
        String headerText = header != null ? header.text() : "";

        List<Week> weeks = new ArrayList<>(2);
        weeks.add(parseWeek(first, 1));
        if (second != null) {
            weeks.add(parseWeek(second, 2));
        }

        LocalDate date = weeks.get(0).days().stream()
                .filter(Day::today)
                .map(Day::date)
                .findFirst()
                .orElseGet(() -> weeks.get(0).firstDate());

        return new Schedule(group, date, parseWeekNumber(headerText), parseUpdatedAt(headerText), weeks);
    }

    private static Week parseWeek(Element weekBlock, int number) {
        String title = "";
        Element h3 = weekBlock.previousElementSibling();
        if (h3 != null && h3.tagName().equalsIgnoreCase("h3")) {
            title = h3.text().trim();
        }

        List<Day> days = new ArrayList<>();
        for (Element dayBlock : weekBlock.select("div.card-block")) {
            Matcher m = DAY_CLASS.matcher(dayBlock.className());
            if (!m.find()) {
                continue;
            }
            LocalDate date = LocalDate.parse(m.group(1));
            Element title4 = dayBlock.selectFirst("h4.card-title");
            boolean today = title4 != null && title4.hasClass("today");
            days.add(new Day(date, today, parseLessons(dayBlock)));
        }
        if (days.isEmpty()) {
            throw new KubSauException("В блоке недели " + number + " не найдено ни одного дня");
        }
        return new Week(number, title, days);
    }

    private static List<Lesson> parseLessons(Element dayBlock) {
        List<Lesson> lessons = new ArrayList<>();
        int number = 0;
        for (Element row : dayBlock.select("table tr")) {
            Element timeCell = row.selectFirst("td.time");
            Element dissCell = row.selectFirst("td.diss");
            if (timeCell == null || dissCell == null) {
                continue;
            }
            number++;

            String subject = extractSubject(dissCell);
            if (subject.isEmpty()) {
                continue;
            }

            LocalTime[] times = parseTimes(timeCell.text());
            Element lectionCell = row.selectFirst("td.lection");
            LessonType type = lectionCell != null && lectionCell.hasClass("yes")
                    ? LessonType.LECTURE
                    : LessonType.PRACTICE;

            List<Teacher> teachers = new ArrayList<>();
            Element info = dissCell.selectFirst("span.diss-info");
            if (info != null) {
                for (String part : info.text().split(",")) {
                    String name = normalize(part);
                    if (!name.isEmpty()) {
                        teachers.add(parseTeacher(name));
                    }
                }
            }

            List<String> rooms = new ArrayList<>();
            for (Element link : row.select("td.who-where a.room-link")) {
                String room = link.text().trim();
                if (!room.isEmpty()) {
                    rooms.add(room);
                }
            }
            if (rooms.isEmpty()) {
                Element whoWhere = row.selectFirst("td.who-where");
                if (whoWhere != null) {
                    String room = normalize(whoWhere.text());
                    if (!room.isEmpty()) {
                        rooms.add(room);
                    }
                }
            }

            lessons.add(new Lesson(number, times[0], times[1], subject, type, teachers, rooms));
        }
        return lessons;
    }

    /** Название дисциплины: текст ячейки без блока с преподавателями. */
    private static String extractSubject(Element dissCell) {
        Element copy = dissCell.clone();
        copy.select("span.diss-info").remove();
        return normalize(copy.text());
    }

    private static Teacher parseTeacher(String text) {
        Matcher m = SUBGROUP_TEACHER.matcher(text);
        if (m.matches()) {
            return new Teacher(m.group(2).trim(), m.group(1));
        }
        return new Teacher(text, null);
    }

    private static LocalTime[] parseTimes(String text) {
        Matcher m = TIME.matcher(text);
        LocalTime start = null;
        LocalTime end = null;
        if (m.find()) {
            start = parseTime(m.group(1));
        }
        if (m.find()) {
            end = parseTime(m.group(1));
        }
        if (start == null || end == null) {
            throw new KubSauException("Не удалось разобрать время пары: \"" + text + "\"");
        }
        return new LocalTime[]{start, end};
    }

    private static LocalTime parseTime(String s) {
        String[] p = s.split(":");
        return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    private static int parseWeekNumber(String headerText) {
        Matcher m = WEEK_NUMBER.matcher(headerText);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    private static LocalDateTime parseUpdatedAt(String headerText) {
        Matcher m = UPDATED_AT.matcher(headerText);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDateTime.parse(m.group(1).replaceAll("\\s+", " "), UPDATED_AT_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Схлопывает пробелы (включая неразрывные) и обрезает края. */
    private static String normalize(String s) {
        return s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }
}

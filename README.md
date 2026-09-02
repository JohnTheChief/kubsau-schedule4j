# KubSAU Schedule Library

Java-библиотека для расписания занятий КубГАУ (<https://s.kubsau.ru>).
Два метода: поиск групп и получение расписания на две недели.

## Подключение

Библиотека распространяется через [JitPack](https://jitpack.io), токены не нужны.
Замените `OWNER` на владельца репозитория на GitHub, `REPO` на его имя.

Maven:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.JohnTheChief</groupId>
        <artifactId>kubsau-schedule4j</artifactId>
        <version>1.0.1</version>
    </dependency>
</dependencies>
```

Gradle (Kotlin DSL):

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.JohnTheChief:kubsau-schedule4j:1.0.1")
}
```

Вместо тега можно указать `main-SNAPSHOT` (последний коммит в ветке) или хэш коммита.

Требуется Java 17+. Зависимости: jsoup (разбор HTML), Gson (разбор JSON подсказок).

## Использование

```java
import me.heyjohn.kubsau.*;

KubSauSchedule client = new KubSauSchedule();

// подсказки при вводе названия группы
List<Group> groups = client.queryGroups("БИ");
// [Group[name=БИ2301, id=000003858], Group[name=БИ2302, id=000003859], ...]

// расписание на текущую и следующую неделю
Schedule schedule = client.getSchedule("БИ2601");

System.out.println(schedule.group());              // БИ2601
System.out.println(schedule.date());               // 2026-09-02 — «сегодня» по мнению сайта
System.out.println(schedule.currentWeekNumber());  // 1 — чётность текущей недели
System.out.println(schedule.updatedAt());          // 2026-09-02T16:57:05

for (Week week : schedule.weeks()) {
    System.out.println(week.title());              // Первая неделя / Вторая неделя
    for (Day day : week.days()) {
        System.out.println(day.date() + " " + day.dayOfWeek() + (day.today() ? " (сегодня)" : ""));
        for (Lesson lesson : day.lessons()) {
            System.out.printf("  %d. %s–%s %s [%s] %s %s%n",
                    lesson.number(), lesson.start(), lesson.end(),
                    lesson.subject(), lesson.type(),
                    lesson.teachers(), lesson.rooms());
        }
    }
}
```

Удобные выборки: `schedule.today()`, `schedule.day(LocalDate)`, `schedule.currentWeek()`,
`schedule.nextWeek()`, `schedule.days()`, `schedule.lessons()`.

## Модель данных

| Тип | Поля |
|-----|------|
| `Group` | `name` — название группы, `id` — идентификатор на сайте |
| `Schedule` | `group`, `date`, `currentWeekNumber`, `updatedAt`, `weeks` (2 недели) |
| `Week` | `number` (1 — текущая, 2 — следующая), `title`, `days` (пн–сб) |
| `Day` | `date`, `today`, `lessons` (только занятые слоты) |
| `Lesson` | `number` (номер пары 1–6), `start`, `end`, `subject`, `type`, `teachers`, `rooms` |
| `Teacher` | `name`, `subgroup` (например `БИ2601/1`, иначе `null`) |
| `LessonType` | `LECTURE` — сайт помечает как лекцию, `PRACTICE` — всё остальное |

## Ошибки

* `GroupNotFoundException` — сайт не знает такую группу (`getSchedule`).
* `KubSauException` — сетевая ошибка, не-2xx ответ или неожиданная структура страницы.

Оба исключения unchecked. `queryGroups` для неизвестного префикса возвращает пустой список.

## Настройка

```java
HttpClient http = HttpClient.newBuilder()
        .proxy(ProxySelector.of(new InetSocketAddress("proxy", 3128)))
        .build();
KubSauSchedule client = new KubSauSchedule(http, KubSauSchedule.DEFAULT_BASE_URL, Duration.ofSeconds(10));
```

## Как это работает

* `queryGroups` — GET `/bitrix/components/atom/atom.education.schedule.remote.data/get.php?query=<текст>&type_schedule=1`,
  ответ `{"suggestions":[{"value":"БИ2301","data":"000003858"}, ...]}`.
* `getSchedule` — GET `/?type_schedule=1&val=<группа>`, из HTML берутся блоки
  `.schedule-first-week` и `.schedule-second-week`, внутри них дни `div.card-block.day-YYYY-MM-DD`
  и таблица пар. Ячейка `td.lection.yes` означает лекцию, `span.diss-info` — преподаватели,
  `a.room-link` — аудитории.

Разбор страницы вынесен в `ScheduleParser.parse(html, group)`, его можно использовать отдельно.

## Сборка и тесты

```bash
mvn test
```

Интеграционные тесты, ходящие на сайт, включаются флагом:

```bash
mvn test -Dkubsau.live=true
```

Первый запрос зависимости `com.github.OWNER:REPO:v1.0.0` запустит сборку на JitPack
(команда и JDK заданы в `jitpack.yml`), дальше артефакт отдаётся из кеша.
Статус сборки и лог: `https://jitpack.io/#OWNER/REPO`.

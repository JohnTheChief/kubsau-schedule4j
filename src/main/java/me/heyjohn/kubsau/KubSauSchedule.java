package me.heyjohn.kubsau;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Клиент расписания занятий КубГАУ ({@code https://s.kubsau.ru}).
 * <p>
 * Два метода:
 * <ul>
 *     <li>{@link #queryGroups(String)} — подсказки по названию группы;</li>
 *     <li>{@link #getSchedule(String)} — расписание группы на текущую и следующую неделю.</li>
 * </ul>
 * Экземпляр потокобезопасен, его можно переиспользовать.
 */
public final class KubSauSchedule {

    /** Адрес сайта расписания по умолчанию. */
    public static final String DEFAULT_BASE_URL = "https://s.kubsau.ru";

    private static final String SUGGESTIONS_PATH = "/bitrix/components/atom/atom.education.schedule.remote.data/get.php";
    /** {@code type_schedule=1} — расписание по группе (3 — по аудитории). */
    private static final String TYPE_GROUP = "1";
    private static final String USER_AGENT = "KubSauScheduleLib/1.0 (+https://github.com/heyjohn)";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration requestTimeout;

    /** Клиент с настройками по умолчанию. */
    public KubSauSchedule() {
        this(defaultHttpClient(), DEFAULT_BASE_URL, DEFAULT_TIMEOUT);
    }

    /** Клиент с собственным {@link HttpClient} (прокси, таймауты, executor и т. п.). */
    public KubSauSchedule(HttpClient httpClient) {
        this(httpClient, DEFAULT_BASE_URL, DEFAULT_TIMEOUT);
    }

    /**
     * @param httpClient     HTTP-клиент
     * @param baseUrl        адрес сайта без завершающего слэша, например {@code https://s.kubsau.ru}
     * @param requestTimeout таймаут одного запроса
     */
    public KubSauSchedule(HttpClient httpClient, String baseUrl, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /**
     * Запрашивает у сайта список групп, подходящих под строку поиска (подсказки при вводе).
     *
     * @param query начало названия группы, например {@code БИ} или {@code БИ26}
     * @return список групп; пустой, если ничего не найдено
     * @throws KubSauException при сетевой ошибке или неожиданном ответе
     */
    public List<Group> queryGroups(String query) {
        Objects.requireNonNull(query, "query");
        String url = baseUrl + SUGGESTIONS_PATH
                + "?query=" + encode(query.trim())
                + "&type_schedule=" + TYPE_GROUP;

        String body = get(url, "application/json, text/plain, */*");
        return parseSuggestions(body);
    }

    /**
     * Возвращает расписание группы на две недели: текущую и следующую.
     *
     * @param group название группы, например {@code БИ2601}
     * @return структурированное расписание
     * @throws GroupNotFoundException если сайт не знает такой группы
     * @throws KubSauException        при сетевой ошибке или неожиданной структуре страницы
     */
    public Schedule getSchedule(String group) {
        Objects.requireNonNull(group, "group");
        String name = group.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Название группы пустое");
        }
        String url = baseUrl + "/?type_schedule=" + TYPE_GROUP + "&val=" + encode(name);
        String html = get(url, "text/html");
        return ScheduleParser.parse(html, name);
    }

    static List<Group> parseSuggestions(String json) {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new KubSauException("Неожиданный ответ сервера подсказок: " + abbreviate(json));
            }
            root = element.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new KubSauException("Не удалось разобрать ответ сервера подсказок: " + abbreviate(json), e);
        }

        JsonElement suggestions = root.get("suggestions");
        if (suggestions == null || !suggestions.isJsonArray()) {
            return List.of();
        }

        JsonArray array = suggestions.getAsJsonArray();
        List<Group> groups = new ArrayList<>(array.size());
        for (JsonElement item : array) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject obj = item.getAsJsonObject();
            String value = stringOrNull(obj.get("value"));
            String data = stringOrNull(obj.get("data"));
            if (value != null && !value.isBlank()) {
                groups.add(new Group(value.trim(), data));
            }
        }
        return List.copyOf(groups);
    }

    private String get(String url, String accept) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(requestTimeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new KubSauException("Ошибка сети при запросе " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KubSauException("Запрос прерван: " + url, e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new KubSauException("Сервер вернул HTTP " + status + " на запрос " + url);
        }
        return response.body();
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String stringOrNull(JsonElement e) {
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}

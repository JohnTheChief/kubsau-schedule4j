package me.heyjohn.kubsau;

/** Ошибка при обращении к сайту расписания или при разборе его ответа. */
public class KubSauException extends RuntimeException {

    public KubSauException(String message) {
        super(message);
    }

    public KubSauException(String message, Throwable cause) {
        super(message, cause);
    }
}

package io.github.karunarathnad.immuto.example.advanced.sealed.model;

/**
 * Data Transfer Object corresponding to {@link
 * io.github.karunarathnad.immuto.example.advanced.sealed.model.SmsEvent}.
 * <p>
 * Used at the API boundary (e.g. in REST responses) instead of exposing
 * the domain model directly. Mirrors the shape of its domain counterpart
 * and is intended to be produced by a generated or hand-written mapper,
 * such as one implementing
 * {@link io.github.karunarathnad.immuto.example.advanced.sealed.mapper.NotificationEventMapper}.
 *
 * @param id        the unique identifier of the event
 * @param toNumber  the recipient's phone number
 * @param message   the text content of the SMS
 */
public record SmsEventDTO(Long id, String toNumber, String message) implements NotificationEventDTO {}
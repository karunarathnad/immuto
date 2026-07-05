package io.github.karunarathnad.immuto.example.advanced.sealed.model;

/**
 * Data Transfer Object corresponding to {@link
 * io.github.karunarathnad.immuto.example.advanced.sealed.model.EmailEvent}.
 * <p>
 * Used at the API boundary (e.g. in REST responses) instead of exposing
 * the domain model directly. Mirrors the shape of its domain counterpart
 * and is intended to be produced by a generated or hand-written mapper,
 * such as one implementing
 * {@link io.github.karunarathnad.immuto.example.advanced.sealed.mapper.NotificationEventMapper}.
 *
 * @param id         the unique identifier of the event
 * @param toAddress  the recipient's email address
 * @param subject    the subject line of the email
 */
public record EmailEventDTO(Long id, String toAddress, String subject) implements NotificationEventDTO {}
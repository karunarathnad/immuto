package io.github.karunarathnad.immuto.example.advanced.sealed.model;

/**
 * Represents an email notification event.
 * <p>
 * This is one of the two permitted implementations of the sealed
 * {@link NotificationEvent} interface, the other being {@link SmsEvent}.
 * As a {@code record}, instances are immutable and value-based, meaning
 * two {@code EmailEvent} instances with identical field values are
 * considered equal.
 * <p>
 * Being part of a sealed hierarchy, this type enables exhaustive
 * pattern matching (e.g. in {@code switch} expressions) without
 * requiring a {@code default} branch, which is particularly useful
 * when generating mapping code (such as via Immuto) between this
 * domain model and its corresponding DTO representation.
 *
 * @param id         the unique identifier of the event
 * @param toAddress  the recipient's email address
 * @param subject    the subject line of the email
 */
public record EmailEvent(Long id, String toAddress, String subject) implements NotificationEvent {}

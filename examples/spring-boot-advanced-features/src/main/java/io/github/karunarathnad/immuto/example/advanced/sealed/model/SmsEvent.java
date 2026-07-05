package io.github.karunarathnad.immuto.example.advanced.sealed.model;

/**
 * Represents an SMS notification event.
 * <p>
 * This is one of the two permitted implementations of the sealed
 * {@link NotificationEvent} interface, the other being {@link EmailEvent}.
 * As a {@code record}, instances are immutable and value-based, meaning
 * two {@code SmsEvent} instances with identical field values are
 * considered equal.
 * <p>
 * Being part of a sealed hierarchy, this type enables exhaustive
 * pattern matching (e.g. in {@code switch} expressions) without
 * requiring a {@code default} branch, which is particularly useful
 * when generating mapping code (such as via Immuto) between this
 * domain model and its corresponding DTO representation.
 *
 * @param id         the unique identifier of the event
 * @param toNumber   the recipient's phone number
 * @param message    the text content of the SMS
 */
public record SmsEvent(Long id, String toNumber, String message) implements NotificationEvent {}

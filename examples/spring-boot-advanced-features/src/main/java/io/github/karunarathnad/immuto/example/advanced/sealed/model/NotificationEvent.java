package io.github.karunarathnad.immuto.example.advanced.sealed.model;

/**
 * A sealed interface representing a notification event that can be
 * dispatched through one of a fixed, closed set of channels.
 * <p>
 * The permitted implementations are:
 * <ul>
 *     <li>{@link EmailEvent} — a notification delivered via email</li>
 *     <li>{@link SmsEvent} — a notification delivered via SMS</li>
 * </ul>
 * <p>
 * Because this interface is {@code sealed} and its permitted subtypes
 * are records, the compiler can guarantee exhaustiveness when this
 * hierarchy is consumed via pattern matching (e.g. in a {@code switch}
 * expression). No other class or record, in this module or any other,
 * may implement this interface. This closed hierarchy is used to
 * demonstrate and verify how Immuto generates exhaustive object
 * mappers for sealed types.
 */
public sealed interface NotificationEvent permits EmailEvent, SmsEvent {}

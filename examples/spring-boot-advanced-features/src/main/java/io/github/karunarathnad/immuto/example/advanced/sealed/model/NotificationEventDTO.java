package io.github.karunarathnad.immuto.example.advanced.sealed.model;

/**
 * A sealed interface representing the API-facing (DTO) counterpart of
 * {@link io.github.karunarathnad.immuto.example.advanced.sealed.model.NotificationEvent}.
 * <p>
 * The permitted implementations mirror the domain model's permitted
 * subtypes one-to-one:
 * <ul>
 *     <li>{@link EmailEventDTO} — DTO for {@code EmailEvent}</li>
 *     <li>{@link SmsEventDTO} — DTO for {@code SmsEvent}</li>
 * </ul>
 * <p>
 * Keeping this hierarchy sealed, and structurally symmetric with its
 * domain counterpart, allows a generated mapper (see
 * {@link io.github.karunarathnad.immuto.example.advanced.sealed.mapper.NotificationEventMapper})
 * to exhaustively map every domain subtype to its corresponding DTO
 * subtype without a fallback {@code default} case.
 */
public sealed interface NotificationEventDTO permits EmailEventDTO, SmsEventDTO {}
package io.github.karunarathnad.immuto.example.advanced.sealed.controller;

import io.github.karunarathnad.immuto.example.advanced.sealed.model.NotificationEventDTO;
import io.github.karunarathnad.immuto.example.advanced.sealed.mapper.NotificationEventMapper;
import io.github.karunarathnad.immuto.example.advanced.sealed.model.EmailEvent;
import io.github.karunarathnad.immuto.example.advanced.sealed.model.NotificationEvent;
import io.github.karunarathnad.immuto.example.advanced.sealed.model.SmsEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller used to demonstrate and manually verify mapping of a
 * sealed interface hierarchy ({@link NotificationEvent}) to its DTO
 * counterpart ({@link NotificationEventDTO}) via {@link NotificationEventMapper}.
 * <p>
 * Exposes a single endpoint that returns a fixed, in-memory list of
 * notification events — mixing both permitted subtypes,
 * {@link EmailEvent} and {@link SmsEvent} — after mapping each to its
 * corresponding DTO. This serves as a simple end-to-end check that the
 * mapper correctly and exhaustively handles every branch of the sealed
 * hierarchy.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    /** Mapper used to convert domain {@link NotificationEvent}s to
     *  {@link NotificationEventDTO}s.
     */
    private final NotificationEventMapper mapper;

    /**
     * Creates a new {@code NotificationController}.
     *
     * @param mapper the mapper used to convert {@link NotificationEvent}
     *               instances into {@link NotificationEventDTO} instances;
     *               typically injected by the Spring container
     */
    public NotificationController(NotificationEventMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Returns a fixed sample list of notification events, mapped from
     * the domain model to their DTO representations.
     * <p>
     * The sample data intentionally includes both {@link EmailEvent}
     * and {@link SmsEvent} instances to exercise every permitted
     * subtype of the sealed {@link NotificationEvent} interface.
     *
     * @return a list of {@link NotificationEventDTO} instances mapped
     *         from the in-memory sample {@link NotificationEvent} data
     */
    @GetMapping
    public List<NotificationEventDTO> list() {
        List<NotificationEvent> events = List.of(
                new EmailEvent(1L, "alice@example.com", "Welcome aboard"),
                new SmsEvent(2L, "+15551234567", "Your OTP is 482913"),
                new EmailEvent(3L, "bob@example.com", "Invoice due")
        );
        return events.stream().map(mapper::toDto).toList();
    }
}
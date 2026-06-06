package io.github.karunarathnad.immuto.example.advanced.hooks.controller;

import io.github.karunarathnad.immuto.example.advanced.hooks.mapper.PaymentMapper;
import io.github.karunarathnad.immuto.example.advanced.hooks.model.PaymentDTO;
import io.github.karunarathnad.immuto.example.advanced.hooks.model.PaymentEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentMapper mapper;

    public PaymentController(PaymentMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public List<PaymentDTO> list() {
        List<PaymentEntity> entities = List.of(
            new PaymentEntity(1L, "USD", "49.99",  "TXN-001"),
            new PaymentEntity(2L, "EUR", "120.00", "TXN-002")
        );
        return entities.stream().map(mapper::toDto).toList();
    }

    // POST /payments — validation hook fires before mapping;
    // audit hook logs after. Try currency "JPY" to see the validation error.
    @PostMapping
    public ResponseEntity<?> create(@RequestBody PaymentEntity entity) {
        try {
            return ResponseEntity.ok(mapper.toDto(entity));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

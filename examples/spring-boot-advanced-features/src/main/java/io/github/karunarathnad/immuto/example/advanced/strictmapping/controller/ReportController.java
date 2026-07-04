package io.github.karunarathnad.immuto.example.advanced.strictmapping.controller;

import io.github.karunarathnad.immuto.example.advanced.strictmapping.mapper.ReportMapper;
import io.github.karunarathnad.immuto.example.advanced.strictmapping.model.ReportDTO;
import io.github.karunarathnad.immuto.example.advanced.strictmapping.model.ReportEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportMapper mapper;

    public ReportController(ReportMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public List<ReportDTO> list() {
        List<ReportEntity> entities = List.of(
            new ReportEntity(1L, "Q1 Summary",   "published"),
            new ReportEntity(2L, "Q2 Forecast",  "draft"),
            new ReportEntity(3L, "Annual Review", "published")
        );
        return entities.stream().map(mapper::toDto).toList();
    }
}

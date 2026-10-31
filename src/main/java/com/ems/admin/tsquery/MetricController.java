package com.ems.admin.tsquery;

import com.ems.admin.common.api.R;
import com.ems.admin.system.Perms;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices/{id}/metrics")
public class MetricController {
    private final MetricQueryService metricQueryService;

    public MetricController(MetricQueryService metricQueryService) {
        this.metricQueryService = metricQueryService;
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('" + Perms.METRIC_QUERY + "')")
    public R<List<MetricDtos.LatestItem>> latest(@PathVariable Long id,
                                                 @RequestParam(required = false) String identifiers) {
        return R.ok(metricQueryService.latest(id, identifiers));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('" + Perms.METRIC_QUERY + "')")
    public R<MetricDtos.History> history(@PathVariable Long id,
                                         @RequestParam String identifier,
                                         @RequestParam long start,
                                         @RequestParam long end) {
        return R.ok(metricQueryService.history(id, identifier, start, end));
    }
}

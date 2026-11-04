package com.ems.admin.energy;

import com.ems.admin.common.api.R;
import com.ems.admin.system.Perms;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/stations")
public class StationEnergyController {
    private final StationEnergyService stationEnergyService;
    private final EnergySourceService energySourceService;

    public StationEnergyController(StationEnergyService stationEnergyService, EnergySourceService energySourceService) {
        this.stationEnergyService = stationEnergyService;
        this.energySourceService = energySourceService;
    }

    @GetMapping("/{id}/energy")
    @PreAuthorize("hasAuthority('" + Perms.STATION_LIST + "') and hasAuthority('" + Perms.METRIC_QUERY + "')")
    public R<EnergyDtos.Chart> energy(@PathVariable Long id,
                                      @RequestParam String period,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(stationEnergyService.chart(id, period, date));
    }

    @GetMapping("/{id}/energy-sources")
    @PreAuthorize("hasAuthority('" + Perms.STATION_LIST + "')")
    public R<EnergyDtos.SourceBoard> sources(@PathVariable Long id) {
        return R.ok(energySourceService.list(id));
    }

    @PutMapping("/{id}/energy-sources/{series}")
    @PreAuthorize("hasAuthority('" + Perms.STATION_EDIT + "')")
    public R<Void> replaceSources(@PathVariable Long id, @PathVariable String series,
                                  @Valid @RequestBody EnergyDtos.ReplaceSourcesRequest request) {
        energySourceService.replace(id, series, request.sources());
        return R.ok();
    }

    @DeleteMapping("/{id}/energy-sources/{sourceId}")
    @PreAuthorize("hasAuthority('" + Perms.STATION_EDIT + "')")
    public R<Void> removeSource(@PathVariable Long id, @PathVariable Long sourceId) {
        energySourceService.remove(id, sourceId);
        return R.ok();
    }
}

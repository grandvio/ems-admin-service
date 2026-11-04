package com.ems.admin.tariff;

import com.ems.admin.common.api.R;
import com.ems.admin.system.Perms;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class StationTariffController {
    private final TariffService tariffService;
    private final TariffCostService tariffCostService;

    public StationTariffController(TariffService tariffService, TariffCostService tariffCostService) {
        this.tariffService = tariffService;
        this.tariffCostService = tariffCostService;
    }

    @GetMapping("/{id}/tariff")
    @PreAuthorize("hasAuthority('" + Perms.STATION_LIST + "')")
    public R<TariffDtos.TariffVO> get(@PathVariable Long id) {
        return R.ok(tariffService.get(id));
    }

    @PutMapping("/{id}/tariff")
    @PreAuthorize("hasAuthority('" + Perms.STATION_EDIT + "')")
    public R<Void> save(@PathVariable Long id, @Valid @RequestBody TariffDtos.TariffRequest request) {
        tariffService.save(id, request);
        return R.ok();
    }

    @GetMapping("/{id}/prices")
    @PreAuthorize("hasAuthority('" + Perms.STATION_LIST + "')")
    public R<TariffDtos.PriceChart> prices(@PathVariable Long id,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(required = false) String product) {
        return R.ok(tariffService.prices(id, date, product));
    }

    @GetMapping("/{id}/energy-cost")
    @PreAuthorize("hasAuthority('" + Perms.STATION_LIST + "')")
    public R<TariffDtos.CostChart> cost(@PathVariable Long id,
                                        @RequestParam String period,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(tariffCostService.cost(id, period, date));
    }
}

package com.ems.admin.tariff;

import com.ems.admin.common.api.R;
import com.ems.admin.system.Perms;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/price-locations")
public class PriceLocationController {
    private final TariffService tariffService;

    public PriceLocationController(TariffService tariffService) {
        this.tariffService = tariffService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Perms.STATION_LIST + "')")
    public R<List<TariffDtos.LocationVO>> locations(@RequestParam(required = false) String market) {
        return R.ok(tariffService.locations(market));
    }
}

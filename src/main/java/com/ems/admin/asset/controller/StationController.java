package com.ems.admin.asset.controller;

import com.ems.admin.asset.dto.AssetDtos;
import com.ems.admin.asset.service.StationService;
import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.R;
import com.ems.admin.system.Perms;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {
    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Perms.STATION_LIST + "')")
    public R<PageResult<AssetDtos.StationVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) String name,
                                                   @RequestParam(required = false) String stationType,
                                                   @RequestParam(required = false) String status) {
        return R.ok(stationService.page(pageNum, pageSize, name, stationType, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.STATION_LIST + "')")
    public R<AssetDtos.StationVO> get(@PathVariable Long id) {
        return R.ok(stationService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Perms.STATION_ADD + "')")
    public R<Long> create(@Valid @RequestBody AssetDtos.StationRequest request) {
        return R.ok(stationService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.STATION_EDIT + "')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AssetDtos.StationRequest request) {
        stationService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.STATION_REMOVE + "')")
    public R<Void> delete(@PathVariable Long id) {
        stationService.delete(id);
        return R.ok();
    }
}

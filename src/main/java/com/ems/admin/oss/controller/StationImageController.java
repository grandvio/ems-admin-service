package com.ems.admin.oss.controller;

import com.ems.admin.common.api.R;
import com.ems.admin.oss.dto.OssDtos;
import com.ems.admin.oss.service.StationImageService;
import com.ems.admin.system.Perms;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stations/{stationId}/images")
public class StationImageController {
    private final StationImageService stationImageService;

    public StationImageController(StationImageService stationImageService) {
        this.stationImageService = stationImageService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Perms.STATION_LIST + "')")
    public R<List<OssDtos.StationImageVO>> list(@PathVariable Long stationId) {
        return R.ok(stationImageService.list(stationId));
    }

    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAuthority('" + Perms.STATION_EDIT + "')")
    public R<Void> delete(@PathVariable Long stationId, @PathVariable Long imageId) {
        stationImageService.delete(stationId, imageId);
        return R.ok();
    }

    @PutMapping("/{imageId}/cover")
    @PreAuthorize("hasAuthority('" + Perms.STATION_EDIT + "')")
    public R<Void> cover(@PathVariable Long stationId, @PathVariable Long imageId) {
        stationImageService.setCover(stationId, imageId);
        return R.ok();
    }
}

package com.ems.admin.energy;

import com.ems.admin.energy.entity.EmsEnergySource;
import com.ems.admin.energy.mapper.EmsEnergySourceMapper;
import com.ems.admin.framework.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class EnergyRollupJob {
    private final EmsEnergySourceMapper sourceMapper;
    private final EnergyRollupService energyRollupService;

    public EnergyRollupJob(EmsEnergySourceMapper sourceMapper, EnergyRollupService energyRollupService) {
        this.sourceMapper = sourceMapper;
        this.energyRollupService = energyRollupService;
    }

    @Scheduled(fixedDelayString = "${ems.energy.rollup-delay-ms:600000}",
            initialDelayString = "${ems.energy.rollup-initial-delay-ms:60000}")
    public void roll() {
        boolean previous = TenantContext.setIgnore(true);
        try {
            List<EmsEnergySource> sources = sourceMapper.selectList(null);
            Set<String> seen = new HashSet<>();
            for (EmsEnergySource source : sources) {
                if (seen.add(source.getDeviceId() + "\0" + source.getMetricName())) {
                    energyRollupService.advance(source.getDeviceId(), source.getMetricName());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("电量定时汇总失败", ex);
        } finally {
            TenantContext.setIgnore(previous);
        }
    }
}

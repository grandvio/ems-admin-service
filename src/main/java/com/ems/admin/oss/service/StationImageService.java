package com.ems.admin.oss.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ems.admin.asset.service.StationGuard;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.common.tx.AfterCommit;
import com.ems.admin.oss.ObjectStorage;
import com.ems.admin.oss.OssObjects;
import com.ems.admin.oss.OssUrls;
import com.ems.admin.oss.dto.OssDtos;
import com.ems.admin.oss.entity.EmsStationImage;
import com.ems.admin.oss.mapper.EmsStationImageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StationImageService {
    private final EmsStationImageMapper imageMapper;
    private final StationGuard stationGuard;
    private final OssUrls ossUrls;
    private final ObjectStorage objectStorage;
    private final AfterCommit afterCommit;

    public StationImageService(EmsStationImageMapper imageMapper, StationGuard stationGuard, OssUrls ossUrls,
                               ObjectStorage objectStorage, AfterCommit afterCommit) {
        this.imageMapper = imageMapper;
        this.stationGuard = stationGuard;
        this.ossUrls = ossUrls;
        this.objectStorage = objectStorage;
        this.afterCommit = afterCommit;
    }

    public List<OssDtos.StationImageVO> list(Long stationId) {
        stationGuard.check(stationId);
        return imageMapper.selectList(new LambdaQueryWrapper<EmsStationImage>()
                        .eq(EmsStationImage::getStationId, stationId)
                        .orderByDesc(EmsStationImage::getCover)
                        .orderByAsc(EmsStationImage::getSortNum)
                        .orderByAsc(EmsStationImage::getId))
                .stream().map(this::toVo).toList();
    }

    @Transactional
    public void delete(Long stationId, Long imageId) {
        stationGuard.check(stationId);
        EmsStationImage image = require(stationId, imageId);
        boolean wasCover = image.getCover() != null && image.getCover() == 1;
        String objectKey = image.getObjectKey();
        imageMapper.deleteById(image.getId());
        if (wasCover) {
            EmsStationImage next = imageMapper.selectOne(new LambdaQueryWrapper<EmsStationImage>()
                    .eq(EmsStationImage::getStationId, stationId)
                    .orderByAsc(EmsStationImage::getSortNum)
                    .orderByAsc(EmsStationImage::getId)
                    .last("LIMIT 1"));
            if (next != null) {
                next.setCover(1);
                imageMapper.updateById(next);
            }
        }
        afterCommit.run(() -> OssObjects.deleteQuietly(objectStorage, objectKey));
    }

    @Transactional
    public void setCover(Long stationId, Long imageId) {
        stationGuard.check(stationId);
        require(stationId, imageId);
        imageMapper.update(null, new LambdaUpdateWrapper<EmsStationImage>()
                .eq(EmsStationImage::getStationId, stationId)
                .set(EmsStationImage::getCover, 0));
        imageMapper.update(null, new LambdaUpdateWrapper<EmsStationImage>()
                .eq(EmsStationImage::getId, imageId)
                .eq(EmsStationImage::getStationId, stationId)
                .set(EmsStationImage::getCover, 1));
    }

    public void detach(Long stationId) {
        List<EmsStationImage> images = imageMapper.selectList(new LambdaQueryWrapper<EmsStationImage>()
                .eq(EmsStationImage::getStationId, stationId));
        List<String> keys = images.stream().map(EmsStationImage::getObjectKey).toList();
        for (EmsStationImage image : images) {
            imageMapper.deleteById(image.getId());
        }
        afterCommit.run(() -> keys.forEach(key -> OssObjects.deleteQuietly(objectStorage, key)));
    }

    public String coverUrl(Long stationId) {
        EmsStationImage image = imageMapper.selectOne(new LambdaQueryWrapper<EmsStationImage>()
                .eq(EmsStationImage::getStationId, stationId)
                .eq(EmsStationImage::getCover, 1)
                .last("LIMIT 1"));
        return image == null ? null : ossUrls.read(image.getObjectKey());
    }

    public Map<Long, String> coverUrls(List<Long> stationIds) {
        if (stationIds == null || stationIds.isEmpty()) {
            return Map.of();
        }
        List<EmsStationImage> images = imageMapper.selectList(new LambdaQueryWrapper<EmsStationImage>()
                .in(EmsStationImage::getStationId, stationIds)
                .eq(EmsStationImage::getCover, 1));
        Map<Long, String> urls = new HashMap<>();
        for (EmsStationImage image : images) {
            urls.putIfAbsent(image.getStationId(), ossUrls.read(image.getObjectKey()));
        }
        return urls;
    }

    private EmsStationImage require(Long stationId, Long imageId) {
        EmsStationImage image = imageMapper.selectById(imageId);
        if (image == null || !stationId.equals(image.getStationId())) {
            throw BizException.notFound("图片不存在");
        }
        return image;
    }

    private OssDtos.StationImageVO toVo(EmsStationImage image) {
        OssDtos.StationImageVO vo = new OssDtos.StationImageVO();
        vo.setId(image.getId());
        vo.setUrl(ossUrls.read(image.getObjectKey()));
        vo.setCover(image.getCover());
        vo.setSortNum(image.getSortNum());
        vo.setCreatedAt(image.getCreatedAt());
        return vo;
    }
}

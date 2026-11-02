package com.ems.admin.oss.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ems.admin.asset.service.StationGuard;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.common.tx.AfterCommit;
import com.ems.admin.framework.config.EmsProperties;
import com.ems.admin.framework.security.CurrentUser;
import com.ems.admin.framework.security.LoginUser;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.oss.ObjectKeys;
import com.ems.admin.oss.ObjectStorage;
import com.ems.admin.oss.OssObjects;
import com.ems.admin.oss.StoredObject;
import com.ems.admin.oss.dto.OssDtos;
import com.ems.admin.oss.entity.EmsStationImage;
import com.ems.admin.oss.entity.OssUploadTicket;
import com.ems.admin.oss.mapper.EmsStationImageMapper;
import com.ems.admin.oss.mapper.OssUploadTicketMapper;
import com.ems.admin.system.Perms;
import com.ems.admin.system.entity.SysUser;
import com.ems.admin.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class UploadTicketService {
    private static final String PENDING = "PENDING";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String REJECTED = "REJECTED";
    private static final String AVATAR = "AVATAR";
    private static final String STATION = "STATION";

    private final OssUploadTicketMapper ticketMapper;
    private final EmsStationImageMapper imageMapper;
    private final SysUserMapper userMapper;
    private final ObjectStorage objectStorage;
    private final StationGuard stationGuard;
    private final EmsProperties properties;
    private final AfterCommit afterCommit;
    private final TransactionTemplate transactionTemplate;

    public UploadTicketService(OssUploadTicketMapper ticketMapper, EmsStationImageMapper imageMapper, SysUserMapper userMapper,
                               ObjectStorage objectStorage, StationGuard stationGuard, EmsProperties properties,
                               AfterCommit afterCommit, PlatformTransactionManager transactionManager) {
        this.ticketMapper = ticketMapper;
        this.imageMapper = imageMapper;
        this.userMapper = userMapper;
        this.objectStorage = objectStorage;
        this.stationGuard = stationGuard;
        this.properties = properties;
        this.afterCommit = afterCommit;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public OssDtos.UploadTicketResponse issue(OssDtos.UploadTicketRequest request) {
        assertBiz(request.bizType(), request.bizId());
        EmsProperties.Oss oss = properties.getOss();
        long limit = AVATAR.equals(request.bizType()) ? oss.getAvatarMaxBytes() : oss.getStationImageMaxBytes();
        if (request.fileSize() > limit) {
            throw BizException.badRequest("文件不能超过" + (limit / 1024 / 1024) + "MB");
        }
        LoginUser loginUser = CurrentUser.require();
        String objectKey = AVATAR.equals(request.bizType())
                ? ObjectKeys.avatar(loginUser.getTenantId(), loginUser.getUserId(), request.contentType())
                : ObjectKeys.station(loginUser.getTenantId(), request.bizId(), request.contentType());
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(oss.getUploadExpireMinutes());
        OssUploadTicket ticket = new OssUploadTicket();
        ticket.setTenantId(TenantContext.get());
        ticket.setUserId(loginUser.getUserId());
        ticket.setBizType(request.bizType());
        ticket.setBizId(STATION.equals(request.bizType()) ? request.bizId() : null);
        ticket.setObjectKey(objectKey);
        ticket.setContentType(request.contentType());
        ticket.setMaxBytes(limit);
        ticket.setExpireAt(expireAt);
        ticket.setStatus(PENDING);
        ticketMapper.insert(ticket);
        String uploadUrl = objectStorage.presignPut(objectKey, request.contentType(), Duration.ofMinutes(oss.getUploadExpireMinutes()));
        return new OssDtos.UploadTicketResponse(ticket.getId(), objectKey, uploadUrl, "PUT", request.contentType(), expireAt);
    }

    public void confirm(Long ticketId) {
        OssUploadTicket ticket = requireUsable(ticketId);
        StoredObject stored = objectStorage.head(ticket.getObjectKey());
        if (stored == null) {
            throw BizException.badRequest("文件尚未上传");
        }
        if (stored.size() <= 0 || stored.size() > ticket.getMaxBytes()
                || !ObjectKeys.sameContentType(ticket.getContentType(), stored.contentType())) {
            objectStorage.delete(ticket.getObjectKey());
            transactionTemplate.executeWithoutResult(status -> markStatus(ticket.getId(), REJECTED));
            throw BizException.badRequest("文件类型或大小不符合要求");
        }
        transactionTemplate.executeWithoutResult(status -> bind(ticket));
    }

    @Transactional
    public void deleteAvatar() {
        LoginUser loginUser = CurrentUser.require();
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        String previous = user.getAvatarKey();
        if (!StringUtils.hasText(previous)) {
            return;
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getAvatarKey, null));
        afterCommit.run(() -> OssObjects.deleteQuietly(objectStorage, previous));
    }

    private void bind(OssUploadTicket ticket) {
        if (markStatus(ticket.getId(), CONFIRMED) != 1) {
            throw BizException.conflict("凭证已使用");
        }
        if (AVATAR.equals(ticket.getBizType())) {
            bindAvatar(ticket);
            return;
        }
        bindStation(ticket);
    }

    private void bindAvatar(OssUploadTicket ticket) {
        SysUser user = userMapper.selectById(ticket.getUserId());
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        String previous = user.getAvatarKey();
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getAvatarKey, ticket.getObjectKey()));
        if (StringUtils.hasText(previous) && !previous.equals(ticket.getObjectKey())) {
            afterCommit.run(() -> OssObjects.deleteQuietly(objectStorage, previous));
        }
    }

    private void bindStation(OssUploadTicket ticket) {
        Long countValue = imageMapper.selectCount(new LambdaQueryWrapper<EmsStationImage>()
                .eq(EmsStationImage::getStationId, ticket.getBizId()));
        long count = countValue == null ? 0 : countValue;
        int max = properties.getOss().getStationImageMaxCount();
        if (count >= max) {
            throw BizException.badRequest("电站图片最多" + max + "张");
        }
        Long covers = imageMapper.selectCount(new LambdaQueryWrapper<EmsStationImage>()
                .eq(EmsStationImage::getStationId, ticket.getBizId())
                .eq(EmsStationImage::getCover, 1));
        EmsStationImage image = new EmsStationImage();
        image.setTenantId(ticket.getTenantId());
        image.setStationId(ticket.getBizId());
        image.setObjectKey(ticket.getObjectKey());
        image.setCover(covers == null || covers == 0 ? 1 : 0);
        image.setSortNum((int) count);
        imageMapper.insert(image);
    }

    private int markStatus(Long ticketId, String status) {
        return ticketMapper.update(null, new LambdaUpdateWrapper<OssUploadTicket>()
                .eq(OssUploadTicket::getId, ticketId)
                .eq(OssUploadTicket::getStatus, PENDING)
                .set(OssUploadTicket::getStatus, status));
    }

    private OssUploadTicket requireUsable(Long ticketId) {
        OssUploadTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw BizException.notFound("上传凭证不存在");
        }
        LoginUser loginUser = CurrentUser.require();
        if (!loginUser.getUserId().equals(ticket.getUserId())) {
            throw BizException.forbidden("无权使用该凭证");
        }
        if (!PENDING.equals(ticket.getStatus())) {
            throw BizException.conflict("凭证已使用");
        }
        if (ticket.getExpireAt().isBefore(LocalDateTime.now())) {
            throw BizException.badRequest("上传凭证已过期");
        }
        assertBiz(ticket.getBizType(), ticket.getBizId());
        return ticket;
    }

    private void assertBiz(String bizType, Long bizId) {
        if (AVATAR.equals(bizType)) {
            return;
        }
        if (!STATION.equals(bizType)) {
            throw BizException.badRequest("业务类型不正确");
        }
        if (bizId == null) {
            throw BizException.badRequest("电站不能为空");
        }
        if (!hasAuthority(Perms.STATION_EDIT)) {
            throw BizException.forbidden("没有权限");
        }
        stationGuard.check(bizId);
    }

    private boolean hasAuthority(String permission) {
        return CurrentUser.require().getAuthorities().stream().anyMatch(authority -> permission.equals(authority.getAuthority()));
    }
}

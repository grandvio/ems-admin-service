package com.ems.admin.oss.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oss_upload_ticket")
public class OssUploadTicket extends BaseEntity {
    private Long userId;
    private String bizType;
    private Long bizId;
    private String objectKey;
    private String contentType;
    private Long maxBytes;
    private LocalDateTime expireAt;
    private String status;
}

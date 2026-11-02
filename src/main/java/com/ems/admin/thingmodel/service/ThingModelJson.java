package com.ems.admin.thingmodel.service;

import com.ems.admin.common.exception.BizException;
import com.ems.admin.thingmodel.dto.ThingModelFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class ThingModelJson {
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private ThingModelJson() {
    }

    public static byte[] write(ThingModelFile file) {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(file);
    }

    public static ThingModelFile read(byte[] bytes) {
        try {
            ThingModelFile file = JSON.readValue(bytes, ThingModelFile.class);
            if (file == null) {
                throw BizException.badRequest("物模型 JSON 不能为空");
            }
            return file;
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw BizException.badRequest("物模型 JSON 无法解析");
        }
    }
}

package com.ems.admin.framework.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class MybatisPlusConfig {

    /**
     * 菜单是平台目录；租户表本身不按租户列过滤。
     * 物模型需要同时看见平台标准产品（tenant_id=0）和本租户产品，等值插件做不到，改在服务层过滤。
     */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "sys_tenant", "sys_menu", "iot_product", "iot_property", "iot_event", "iot_service",
            "ems_price_location", "ems_market_price"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.get();
                if (tenantId == null) {
                    throw BizException.unauthorized("缺少租户上下文");
                }
                return new LongValue(tenantId);
            }

            @Override
            public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
                return columns.stream().anyMatch(column -> columnName(column).equalsIgnoreCase(tenantIdColumn));
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (TenantContext.isIgnore()) {
                    return true;
                }
                return IGNORE_TABLES.contains(plainName(tableName));
            }

            private String columnName(Column column) {
                return plainName(column.getColumnName());
            }

            private String plainName(String name) {
                int dot = name.lastIndexOf('.');
                if (dot >= 0) {
                    name = name.substring(dot + 1);
                }
                return name.replace("`", "").toLowerCase();
            }
        }));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.MYSQL));
        return interceptor;
    }
}

package com.ems.admin.thingmodel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.admin.asset.entity.EmsDevice;
import com.ems.admin.asset.mapper.EmsDeviceMapper;
import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.Pages;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.thingmodel.dto.ThingDtos;
import com.ems.admin.thingmodel.entity.IotEvent;
import com.ems.admin.thingmodel.entity.IotProduct;
import com.ems.admin.thingmodel.entity.IotProperty;
import com.ems.admin.thingmodel.entity.IotService;
import com.ems.admin.thingmodel.mapper.IotEventMapper;
import com.ems.admin.thingmodel.mapper.IotProductMapper;
import com.ems.admin.thingmodel.mapper.IotPropertyMapper;
import com.ems.admin.thingmodel.mapper.IotServiceMapper;
import com.ems.admin.thingmodel.dto.ThingModelFile;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ProductService {
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9_./\\-]{1,128}$");

    private final IotProductMapper productMapper;
    private final IotPropertyMapper propertyMapper;
    private final IotEventMapper eventMapper;
    private final IotServiceMapper serviceMapper;
    private final EmsDeviceMapper deviceMapper;
    private final Validator validator;

    public ProductService(IotProductMapper productMapper, IotPropertyMapper propertyMapper, IotEventMapper eventMapper,
                          IotServiceMapper serviceMapper, EmsDeviceMapper deviceMapper, Validator validator) {
        this.productMapper = productMapper;
        this.propertyMapper = propertyMapper;
        this.eventMapper = eventMapper;
        this.serviceMapper = serviceMapper;
        this.deviceMapper = deviceMapper;
        this.validator = validator;
    }

    public PageResult<ThingDtos.ProductVO> page(long pageNum, long pageSize, String name, String status) {
        LambdaQueryWrapper<IotProduct> wrapper = visibleWrapper();
        if (StringUtils.hasText(name)) {
            wrapper.like(IotProduct::getName, name);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(IotProduct::getStatus, status);
        }
        wrapper.orderByDesc(IotProduct::getId);
        Page<IotProduct> page = productMapper.selectPage(Pages.of(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toVo).toList());
    }

    public ThingDtos.ProductVO get(Long id) {
        return toVo(requireVisible(id));
    }

    @Transactional
    public Long create(ThingDtos.ProductRequest request) {
        assertCodeFree(request.code(), null);
        IotProduct product = new IotProduct();
        product.setTenantId(currentTenant());
        product.setCode(request.code());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setStatus("DRAFT");
        product.setModelVersion(0);
        productMapper.insert(product);
        return product.getId();
    }

    @Transactional
    public void update(Long id, ThingDtos.ProductRequest request) {
        IotProduct product = requireOwned(id);
        assertCodeFree(request.code(), id);
        product.setCode(request.code());
        product.setName(request.name());
        product.setDescription(request.description());
        productMapper.updateById(product);
    }

    @Transactional
    public Integer publish(Long id) {
        IotProduct product = requireOwned(id);
        int version = product.getModelVersion() == null ? 1 : product.getModelVersion() + 1;
        product.setModelVersion(version);
        product.setStatus("PUBLISHED");
        productMapper.updateById(product);
        return version;
    }

    public ExportedModel exportModel(Long id) {
        IotProduct product = requireVisible(id);
        ThingModelFile file = new ThingModelFile();
        file.setSpecVersion(ThingModelFile.SPEC_VERSION);
        file.setProduct(new ThingDtos.ProductRequest(product.getCode(), product.getName(), product.getDescription()));
        file.setProperties(listProperties(id).stream()
                .map(item -> new ThingDtos.PropertyRequest(item.getIdentifier(), item.getName(), item.getDataType(),
                        item.getUnit(), item.getAccessMode(), item.getMinValue(), item.getMaxValue(), item.getGroupCode()))
                .toList());
        file.setEvents(listEvents(id).stream()
                .map(item -> new ThingDtos.EventRequest(item.getIdentifier(), item.getName(), item.getLevel(), item.getDescription()))
                .toList());
        file.setServices(listServices(id).stream()
                .map(item -> new ThingDtos.ServiceRequest(item.getIdentifier(), item.getName(), item.getInputParams(), item.getDescription()))
                .toList());
        return new ExportedModel(product.getCode() + "-thing-model.json", ThingModelJson.write(file));
    }

    @Transactional
    public Long importNew(byte[] bytes) {
        ThingModelFile file = readModel(bytes);
        Long id = create(file.getProduct());
        insertChildren(productMapper.selectById(id), file);
        return id;
    }

    @Transactional
    public void importInto(Long id, byte[] bytes) {
        ThingModelFile file = readModel(bytes);
        IotProduct product = requireOwned(id);
        assertCodeFree(file.getProduct().code(), id);
        product.setCode(file.getProduct().code());
        product.setName(file.getProduct().name());
        product.setDescription(file.getProduct().description());
        productMapper.updateById(product);
        propertyMapper.delete(new LambdaQueryWrapper<IotProperty>().eq(IotProperty::getProductId, id));
        eventMapper.delete(new LambdaQueryWrapper<IotEvent>().eq(IotEvent::getProductId, id));
        serviceMapper.delete(new LambdaQueryWrapper<IotService>().eq(IotService::getProductId, id));
        insertChildren(product, file);
    }

    public record ExportedModel(String filename, byte[] body) {
    }

    @Transactional
    public void delete(Long id) {
        requireOwned(id);
        boolean previous = TenantContext.setIgnore(true);
        try {
            Long used = deviceMapper.selectCount(new LambdaQueryWrapper<EmsDevice>().eq(EmsDevice::getProductId, id));
            if (used != null && used > 0) {
                throw BizException.conflict("产品已被设备绑定");
            }
        } finally {
            TenantContext.setIgnore(previous);
        }
        propertyMapper.delete(new LambdaQueryWrapper<IotProperty>().eq(IotProperty::getProductId, id));
        eventMapper.delete(new LambdaQueryWrapper<IotEvent>().eq(IotEvent::getProductId, id));
        serviceMapper.delete(new LambdaQueryWrapper<IotService>().eq(IotService::getProductId, id));
        productMapper.deleteById(id);
    }

    public IotProduct requirePublishedVisible(Long id) {
        IotProduct product = requireVisible(id);
        if (!"PUBLISHED".equals(product.getStatus())) {
            throw BizException.badRequest("只能绑定已发布产品");
        }
        return product;
    }

    public List<IotProperty> propertiesOf(Long productId) {
        requireVisible(productId);
        return propertyMapper.selectList(new LambdaQueryWrapper<IotProperty>().eq(IotProperty::getProductId, productId).orderByAsc(IotProperty::getId));
    }

    public List<ThingDtos.PropertyVO> listProperties(Long productId) {
        return propertiesOf(productId).stream().map(this::toPropertyVo).toList();
    }

    @Transactional
    public Long addProperty(Long productId, ThingDtos.PropertyRequest request) {
        IotProduct product = requireOwned(productId);
        assertIdentifier(request.identifier());
        assertPropertyFree(productId, request.identifier(), null);
        IotProperty property = new IotProperty();
        property.setTenantId(product.getTenantId());
        property.setProductId(productId);
        fillProperty(property, request);
        propertyMapper.insert(property);
        return property.getId();
    }

    @Transactional
    public void updateProperty(Long productId, Long id, ThingDtos.PropertyRequest request) {
        requireOwned(productId);
        IotProperty property = requireProperty(productId, id);
        assertIdentifier(request.identifier());
        assertPropertyFree(productId, request.identifier(), id);
        fillProperty(property, request);
        propertyMapper.updateById(property);
    }

    @Transactional
    public void deleteProperty(Long productId, Long id) {
        requireOwned(productId);
        requireProperty(productId, id);
        propertyMapper.deleteById(id);
    }

    public List<ThingDtos.EventVO> listEvents(Long productId) {
        requireVisible(productId);
        return eventMapper.selectList(new LambdaQueryWrapper<IotEvent>().eq(IotEvent::getProductId, productId).orderByAsc(IotEvent::getId))
                .stream().map(this::toEventVo).toList();
    }

    @Transactional
    public Long addEvent(Long productId, ThingDtos.EventRequest request) {
        IotProduct product = requireOwned(productId);
        assertIdentifier(request.identifier());
        assertEventFree(productId, request.identifier(), null);
        IotEvent event = new IotEvent();
        event.setTenantId(product.getTenantId());
        event.setProductId(productId);
        fillEvent(event, request);
        eventMapper.insert(event);
        return event.getId();
    }

    @Transactional
    public void updateEvent(Long productId, Long id, ThingDtos.EventRequest request) {
        requireOwned(productId);
        IotEvent event = requireEvent(productId, id);
        assertIdentifier(request.identifier());
        assertEventFree(productId, request.identifier(), id);
        fillEvent(event, request);
        eventMapper.updateById(event);
    }

    @Transactional
    public void deleteEvent(Long productId, Long id) {
        requireOwned(productId);
        requireEvent(productId, id);
        eventMapper.deleteById(id);
    }

    public List<ThingDtos.ServiceVO> listServices(Long productId) {
        requireVisible(productId);
        return serviceMapper.selectList(new LambdaQueryWrapper<IotService>().eq(IotService::getProductId, productId).orderByAsc(IotService::getId))
                .stream().map(this::toServiceVo).toList();
    }

    @Transactional
    public Long addService(Long productId, ThingDtos.ServiceRequest request) {
        IotProduct product = requireOwned(productId);
        assertIdentifier(request.identifier());
        assertServiceFree(productId, request.identifier(), null);
        IotService service = new IotService();
        service.setTenantId(product.getTenantId());
        service.setProductId(productId);
        fillService(service, request);
        serviceMapper.insert(service);
        return service.getId();
    }

    @Transactional
    public void updateService(Long productId, Long id, ThingDtos.ServiceRequest request) {
        requireOwned(productId);
        IotService service = requireService(productId, id);
        assertIdentifier(request.identifier());
        assertServiceFree(productId, request.identifier(), id);
        fillService(service, request);
        serviceMapper.updateById(service);
    }

    @Transactional
    public void deleteService(Long productId, Long id) {
        requireOwned(productId);
        requireService(productId, id);
        serviceMapper.deleteById(id);
    }

    private LambdaQueryWrapper<IotProduct> visibleWrapper() {
        Long tenantId = currentTenant();
        return new LambdaQueryWrapper<IotProduct>().and(q -> q.eq(IotProduct::getTenantId, 0L).or().eq(IotProduct::getTenantId, tenantId));
    }

    private IotProduct requireVisible(Long id) {
        IotProduct product = productMapper.selectById(id);
        if (product == null || !canSee(product)) {
            throw BizException.notFound("产品不存在");
        }
        return product;
    }

    private IotProduct requireOwned(Long id) {
        IotProduct product = requireVisible(id);
        if (!product.getTenantId().equals(currentTenant())) {
            throw BizException.forbidden("只能维护本租户的产品");
        }
        return product;
    }

    private boolean canSee(IotProduct product) {
        Long tenantId = product.getTenantId();
        return tenantId != null && (tenantId == 0L || tenantId.equals(currentTenant()));
    }

    private Long currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw BizException.unauthorized("缺少租户上下文");
        }
        return tenantId;
    }

    private ThingModelFile readModel(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw BizException.badRequest("请上传物模型 JSON 文件");
        }
        ThingModelFile file = ThingModelJson.read(bytes);
        if (file.getSpecVersion() != null && file.getSpecVersion() != ThingModelFile.SPEC_VERSION) {
            throw BizException.badRequest("不支持的物模型文件版本");
        }
        if (file.getProduct() == null) {
            throw BizException.badRequest("缺少 product");
        }
        validateBean(file.getProduct());
        List<ThingDtos.PropertyRequest> properties = file.getProperties() == null ? List.of() : file.getProperties();
        List<ThingDtos.EventRequest> events = file.getEvents() == null ? List.of() : file.getEvents();
        List<ThingDtos.ServiceRequest> services = file.getServices() == null ? List.of() : file.getServices();
        properties.forEach(this::validateBean);
        events.forEach(this::validateBean);
        services.forEach(this::validateBean);
        assertUnique(properties.stream().map(ThingDtos.PropertyRequest::identifier).toList(), "属性");
        assertUnique(events.stream().map(ThingDtos.EventRequest::identifier).toList(), "事件");
        assertUnique(services.stream().map(ThingDtos.ServiceRequest::identifier).toList(), "服务");
        file.setProperties(properties);
        file.setEvents(events);
        file.setServices(services);
        return file;
    }

    private void insertChildren(IotProduct product, ThingModelFile file) {
        for (ThingDtos.PropertyRequest request : file.getProperties()) {
            assertIdentifier(request.identifier());
            IotProperty property = new IotProperty();
            property.setTenantId(product.getTenantId());
            property.setProductId(product.getId());
            fillProperty(property, request);
            propertyMapper.insert(property);
        }
        for (ThingDtos.EventRequest request : file.getEvents()) {
            assertIdentifier(request.identifier());
            IotEvent event = new IotEvent();
            event.setTenantId(product.getTenantId());
            event.setProductId(product.getId());
            fillEvent(event, request);
            eventMapper.insert(event);
        }
        for (ThingDtos.ServiceRequest request : file.getServices()) {
            assertIdentifier(request.identifier());
            IotService service = new IotService();
            service.setTenantId(product.getTenantId());
            service.setProductId(product.getId());
            fillService(service, request);
            serviceMapper.insert(service);
        }
    }

    private void validateBean(Object bean) {
        var violations = validator.validate(bean);
        if (!violations.isEmpty()) {
            var first = violations.iterator().next();
            throw BizException.badRequest(first.getPropertyPath() + "不合法");
        }
    }

    private void assertUnique(List<String> identifiers, String label) {
        Set<String> seen = new HashSet<>();
        for (String identifier : identifiers) {
            if (!seen.add(identifier)) {
                throw BizException.badRequest(label + "标识重复: " + identifier);
            }
        }
    }

    private void assertCodeFree(String code, Long excludeId) {
        LambdaQueryWrapper<IotProduct> wrapper = new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getTenantId, currentTenant())
                .eq(IotProduct::getCode, code);
        if (excludeId != null) {
            wrapper.ne(IotProduct::getId, excludeId);
        }
        Long count = productMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw BizException.conflict("产品编码已存在");
        }
    }

    private void assertIdentifier(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw BizException.badRequest("标识只允许字母、数字、下划线、点、斜线和中划线");
        }
    }

    private void assertPropertyFree(Long productId, String identifier, Long excludeId) {
        LambdaQueryWrapper<IotProperty> wrapper = new LambdaQueryWrapper<IotProperty>()
                .eq(IotProperty::getProductId, productId)
                .eq(IotProperty::getIdentifier, identifier);
        if (excludeId != null) {
            wrapper.ne(IotProperty::getId, excludeId);
        }
        if (propertyMapper.selectCount(wrapper) > 0) {
            throw BizException.conflict("属性标识已存在");
        }
    }

    private void assertEventFree(Long productId, String identifier, Long excludeId) {
        LambdaQueryWrapper<IotEvent> wrapper = new LambdaQueryWrapper<IotEvent>()
                .eq(IotEvent::getProductId, productId)
                .eq(IotEvent::getIdentifier, identifier);
        if (excludeId != null) {
            wrapper.ne(IotEvent::getId, excludeId);
        }
        if (eventMapper.selectCount(wrapper) > 0) {
            throw BizException.conflict("事件标识已存在");
        }
    }

    private void assertServiceFree(Long productId, String identifier, Long excludeId) {
        LambdaQueryWrapper<IotService> wrapper = new LambdaQueryWrapper<IotService>()
                .eq(IotService::getProductId, productId)
                .eq(IotService::getIdentifier, identifier);
        if (excludeId != null) {
            wrapper.ne(IotService::getId, excludeId);
        }
        if (serviceMapper.selectCount(wrapper) > 0) {
            throw BizException.conflict("服务标识已存在");
        }
    }

    private IotProperty requireProperty(Long productId, Long id) {
        IotProperty property = propertyMapper.selectById(id);
        if (property == null || !productId.equals(property.getProductId())) {
            throw BizException.notFound("属性不存在");
        }
        return property;
    }

    private IotEvent requireEvent(Long productId, Long id) {
        IotEvent event = eventMapper.selectById(id);
        if (event == null || !productId.equals(event.getProductId())) {
            throw BizException.notFound("事件不存在");
        }
        return event;
    }

    private IotService requireService(Long productId, Long id) {
        IotService service = serviceMapper.selectById(id);
        if (service == null || !productId.equals(service.getProductId())) {
            throw BizException.notFound("服务不存在");
        }
        return service;
    }

    private void fillProperty(IotProperty property, ThingDtos.PropertyRequest request) {
        property.setIdentifier(request.identifier());
        property.setName(request.name());
        property.setDataType(request.dataType());
        property.setUnit(request.unit());
        property.setAccessMode(request.accessMode());
        property.setMinValue(request.minValue());
        property.setMaxValue(request.maxValue());
        property.setGroupCode(request.groupCode());
    }

    private void fillEvent(IotEvent event, ThingDtos.EventRequest request) {
        event.setIdentifier(request.identifier());
        event.setName(request.name());
        event.setLevel(request.level());
        event.setDescription(request.description());
    }

    private void fillService(IotService service, ThingDtos.ServiceRequest request) {
        service.setIdentifier(request.identifier());
        service.setName(request.name());
        service.setInputParams(request.inputParams());
        service.setDescription(request.description());
    }

    private ThingDtos.ProductVO toVo(IotProduct product) {
        ThingDtos.ProductVO vo = new ThingDtos.ProductVO();
        vo.setId(product.getId());
        vo.setTenantId(product.getTenantId());
        vo.setCode(product.getCode());
        vo.setName(product.getName());
        vo.setStatus(product.getStatus());
        vo.setModelVersion(product.getModelVersion());
        vo.setDescription(product.getDescription());
        vo.setCreatedAt(product.getCreatedAt());
        return vo;
    }

    private ThingDtos.PropertyVO toPropertyVo(IotProperty property) {
        ThingDtos.PropertyVO vo = new ThingDtos.PropertyVO();
        vo.setId(property.getId());
        vo.setProductId(property.getProductId());
        vo.setIdentifier(property.getIdentifier());
        vo.setName(property.getName());
        vo.setDataType(property.getDataType());
        vo.setUnit(property.getUnit());
        vo.setAccessMode(property.getAccessMode());
        vo.setMinValue(property.getMinValue());
        vo.setMaxValue(property.getMaxValue());
        vo.setGroupCode(property.getGroupCode());
        return vo;
    }

    private ThingDtos.EventVO toEventVo(IotEvent event) {
        ThingDtos.EventVO vo = new ThingDtos.EventVO();
        vo.setId(event.getId());
        vo.setProductId(event.getProductId());
        vo.setIdentifier(event.getIdentifier());
        vo.setName(event.getName());
        vo.setLevel(event.getLevel());
        vo.setDescription(event.getDescription());
        return vo;
    }

    private ThingDtos.ServiceVO toServiceVo(IotService service) {
        ThingDtos.ServiceVO vo = new ThingDtos.ServiceVO();
        vo.setId(service.getId());
        vo.setProductId(service.getProductId());
        vo.setIdentifier(service.getIdentifier());
        vo.setName(service.getName());
        vo.setInputParams(service.getInputParams());
        vo.setDescription(service.getDescription());
        return vo;
    }
}

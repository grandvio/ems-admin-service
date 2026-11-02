package com.ems.admin.thingmodel.controller;

import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.R;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.system.Perms;
import com.ems.admin.thingmodel.dto.ThingDtos;
import com.ems.admin.thingmodel.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Perms.PRODUCT_LIST + "')")
    public R<PageResult<ThingDtos.ProductVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) String name,
                                                   @RequestParam(required = false) String status) {
        return R.ok(productService.page(pageNum, pageSize, name, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.PRODUCT_LIST + "')")
    public R<ThingDtos.ProductVO> get(@PathVariable Long id) {
        return R.ok(productService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Perms.PRODUCT_ADD + "')")
    public R<Long> create(@Valid @RequestBody ThingDtos.ProductRequest request) {
        return R.ok(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.PRODUCT_EDIT + "')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ThingDtos.ProductRequest request) {
        productService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.PRODUCT_REMOVE + "')")
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('" + Perms.PRODUCT_PUBLISH + "')")
    public R<Integer> publish(@PathVariable Long id) {
        return R.ok(productService.publish(id));
    }

    @GetMapping("/{id}/model/export")
    @PreAuthorize("hasAuthority('" + Perms.PRODUCT_LIST + "')")
    public ResponseEntity<byte[]> exportModel(@PathVariable Long id) {
        ProductService.ExportedModel exported = productService.exportModel(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exported.filename() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(exported.body());
    }

    @PostMapping(value = "/model/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Perms.PRODUCT_ADD + "')")
    public R<Long> importNew(@RequestParam("file") MultipartFile file) {
        return R.ok(productService.importNew(readFile(file)));
    }

    @PutMapping(value = "/{id}/model/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Perms.PRODUCT_EDIT + "')")
    public R<Void> importInto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        productService.importInto(id, readFile(file));
        return R.ok();
    }

    @GetMapping("/{productId}/properties")
    @PreAuthorize("hasAuthority('" + Perms.PROPERTY_LIST + "')")
    public R<List<ThingDtos.PropertyVO>> properties(@PathVariable Long productId) {
        return R.ok(productService.listProperties(productId));
    }

    @PostMapping("/{productId}/properties")
    @PreAuthorize("hasAuthority('" + Perms.PROPERTY_ADD + "')")
    public R<Long> addProperty(@PathVariable Long productId, @Valid @RequestBody ThingDtos.PropertyRequest request) {
        return R.ok(productService.addProperty(productId, request));
    }

    @PutMapping("/{productId}/properties/{id}")
    @PreAuthorize("hasAuthority('" + Perms.PROPERTY_EDIT + "')")
    public R<Void> updateProperty(@PathVariable Long productId, @PathVariable Long id,
                                  @Valid @RequestBody ThingDtos.PropertyRequest request) {
        productService.updateProperty(productId, id, request);
        return R.ok();
    }

    @DeleteMapping("/{productId}/properties/{id}")
    @PreAuthorize("hasAuthority('" + Perms.PROPERTY_REMOVE + "')")
    public R<Void> deleteProperty(@PathVariable Long productId, @PathVariable Long id) {
        productService.deleteProperty(productId, id);
        return R.ok();
    }

    @GetMapping("/{productId}/events")
    @PreAuthorize("hasAuthority('" + Perms.EVENT_LIST + "')")
    public R<List<ThingDtos.EventVO>> events(@PathVariable Long productId) {
        return R.ok(productService.listEvents(productId));
    }

    @PostMapping("/{productId}/events")
    @PreAuthorize("hasAuthority('" + Perms.EVENT_ADD + "')")
    public R<Long> addEvent(@PathVariable Long productId, @Valid @RequestBody ThingDtos.EventRequest request) {
        return R.ok(productService.addEvent(productId, request));
    }

    @PutMapping("/{productId}/events/{id}")
    @PreAuthorize("hasAuthority('" + Perms.EVENT_EDIT + "')")
    public R<Void> updateEvent(@PathVariable Long productId, @PathVariable Long id,
                               @Valid @RequestBody ThingDtos.EventRequest request) {
        productService.updateEvent(productId, id, request);
        return R.ok();
    }

    @DeleteMapping("/{productId}/events/{id}")
    @PreAuthorize("hasAuthority('" + Perms.EVENT_REMOVE + "')")
    public R<Void> deleteEvent(@PathVariable Long productId, @PathVariable Long id) {
        productService.deleteEvent(productId, id);
        return R.ok();
    }

    @GetMapping("/{productId}/services")
    @PreAuthorize("hasAuthority('" + Perms.SERVICE_LIST + "')")
    public R<List<ThingDtos.ServiceVO>> services(@PathVariable Long productId) {
        return R.ok(productService.listServices(productId));
    }

    @PostMapping("/{productId}/services")
    @PreAuthorize("hasAuthority('" + Perms.SERVICE_ADD + "')")
    public R<Long> addService(@PathVariable Long productId, @Valid @RequestBody ThingDtos.ServiceRequest request) {
        return R.ok(productService.addService(productId, request));
    }

    @PutMapping("/{productId}/services/{id}")
    @PreAuthorize("hasAuthority('" + Perms.SERVICE_EDIT + "')")
    public R<Void> updateService(@PathVariable Long productId, @PathVariable Long id,
                                 @Valid @RequestBody ThingDtos.ServiceRequest request) {
        productService.updateService(productId, id, request);
        return R.ok();
    }

    @DeleteMapping("/{productId}/services/{id}")
    @PreAuthorize("hasAuthority('" + Perms.SERVICE_REMOVE + "')")
    public R<Void> deleteService(@PathVariable Long productId, @PathVariable Long id) {
        productService.deleteService(productId, id);
        return R.ok();
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请上传物模型 JSON 文件");
        }
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase().endsWith(".json")) {
            throw BizException.badRequest("只支持 .json 文件");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw BizException.badRequest("读取文件失败");
        }
    }
}

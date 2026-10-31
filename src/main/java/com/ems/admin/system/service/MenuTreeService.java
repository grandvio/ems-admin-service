package com.ems.admin.system.service;

import com.ems.admin.system.entity.SysMenu;
import com.ems.admin.system.mapper.SysMenuMapper;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MenuTreeService {
    private final SysMenuMapper menuMapper;

    public MenuTreeService(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    public List<SysMenu> withAncestors(List<SysMenu> assigned) {
        List<SysMenu> all = menuMapper.selectList(null);
        Map<Long, SysMenu> byId = new LinkedHashMap<>();
        for (SysMenu menu : all) {
            byId.put(menu.getId(), menu);
        }
        Set<Long> ids = new HashSet<>();
        for (SysMenu menu : assigned) {
            ids.add(menu.getId());
        }
        boolean grow = true;
        while (grow) {
            grow = false;
            for (Long id : new ArrayList<>(ids)) {
                SysMenu menu = byId.get(id);
                if (menu == null || menu.getParentId() == null || menu.getParentId() == 0) {
                    continue;
                }
                if (ids.add(menu.getParentId())) {
                    grow = true;
                }
            }
        }
        return all.stream().filter(menu -> ids.contains(menu.getId())).toList();
    }

    public List<MenuNode> toTree(List<SysMenu> menus) {
        List<SysMenu> sorted = menus.stream()
                .sorted(Comparator.comparing(menu -> menu.getSortNum() == null ? 0 : menu.getSortNum()))
                .toList();
        Map<Long, MenuNode> nodes = new LinkedHashMap<>();
        for (SysMenu menu : sorted) {
            MenuNode node = new MenuNode();
            node.setId(menu.getId());
            node.setParentId(menu.getParentId());
            node.setName(menu.getName());
            node.setMenuType(menu.getMenuType());
            node.setPath(menu.getPath());
            node.setPerms(menu.getPerms());
            node.setSortNum(menu.getSortNum());
            nodes.put(menu.getId(), node);
        }
        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : nodes.values()) {
            if (node.getParentId() == null || node.getParentId() == 0 || !nodes.containsKey(node.getParentId())) {
                roots.add(node);
            } else {
                nodes.get(node.getParentId()).getChildren().add(node);
            }
        }
        return roots;
    }

    @Data
    public static class MenuNode {
        private Long id;
        private Long parentId;
        private String name;
        private String menuType;
        private String path;
        private String perms;
        private Integer sortNum;
        private List<MenuNode> children = new ArrayList<>();
    }
}

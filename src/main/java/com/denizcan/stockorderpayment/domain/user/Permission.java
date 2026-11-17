package com.denizcan.stockorderpayment.domain.user;

import java.util.EnumSet;
import java.util.Set;

public enum Permission {
    PRODUCTS_VIEW("Ürünleri görüntüle", Role.USER),
    STOCK_VIEW("Merkez stoğunu görüntüle", Role.USER),
    ORDERS_VIEW("Kendi siparişlerini görüntüle", Role.USER),
    ORDERS_CREATE("Kendi hesabına sipariş oluştur", Role.USER),
    BRANCH_STOCK_VIEW("Kendi şubesinin stoğunu görüntüle", Role.BRANCH_EMPLOYEE),
    BRANCH_SALE_CREATE("Kendi şubesinde satış kaydet", Role.BRANCH_EMPLOYEE);

    private final String label;
    private final Role scope;

    Permission(String label, Role scope) {
        this.label = label;
        this.scope = scope;
    }

    public String label() {
        return label;
    }

    public Role scope() {
        return scope;
    }

    public static Set<Permission> defaultsFor(Role role) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (Permission permission : values()) {
            if (role == Role.ADMIN || permission.scope == role) {
                result.add(permission);
            }
        }
        return result;
    }

    public static Set<Permission> forUser(User user) {
        if (user.getRole() == Role.ADMIN) {
            return defaultsFor(Role.ADMIN);
        }
        EnumSet<Permission> effective = EnumSet.noneOf(Permission.class);
        effective.addAll(user.getEmployeeRole() == null
                ? defaultsFor(user.getRole()) : user.getEmployeeRole().getPermissions());
        effective.addAll(user.getAddedPermissions());
        effective.removeAll(user.getExcludedPermissions());
        if (!effective.contains(BRANCH_STOCK_VIEW)) effective.remove(BRANCH_SALE_CREATE);
        if (!effective.contains(ORDERS_VIEW)) effective.remove(ORDERS_CREATE);
        return effective;
    }
}

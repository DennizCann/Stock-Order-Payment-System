package com.denizcan.stockorderpayment.domain.user;

import java.util.EnumSet;
import java.util.Set;

public enum Permission {
    PRODUCTS_VIEW("Ürünleri görüntüle", Role.USER),
    STOCK_VIEW("Merkez stoğunu görüntüle", Role.USER),
    ORDERS_VIEW("Kendi siparişlerini görüntüle", Role.USER),
    ORDERS_CREATE("Kendi hesabına sipariş oluştur", Role.USER),
    BRANCH_STOCK_VIEW("Kendi şubesinin stoğunu görüntüle", Role.BRANCH_EMPLOYEE),
    BRANCH_SALE_CREATE("Kendi şubesinde satış kaydet", Role.BRANCH_EMPLOYEE),
    PRODUCTS_CREATE("Ürün ekle", Role.USER),
    PRODUCTS_EDIT("Ürün ve etiketlerini düzenle", Role.USER),
    PRODUCTS_DELETE("Ürün kaldır", Role.USER),
    STOCK_MANAGE("Merkez ve şubelerde stok hareketi oluştur", Role.USER),
    BRANCH_STOCK_MANAGE("Kendi şubesinde stok giriş/çıkışı oluştur", Role.BRANCH_EMPLOYEE),
    BRANCHES_VIEW("Şubeleri ve depoları görüntüle", Role.USER),
    BRANCHES_MANAGE("Şube ve depo ekle, düzenle, arşivle", Role.USER),
    STOCK_TRANSFER("Merkez, şube ve depolar arasında stok aktar", Role.USER),
    STOCK_TYPES_MANAGE("Stok hareket adlarını tanımla ve düzenle", Role.USER);

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
        if (role == Role.ADMIN) return availableFor(role);
        return role == Role.USER ? EnumSet.of(PRODUCTS_VIEW, STOCK_VIEW, ORDERS_VIEW, ORDERS_CREATE)
                : EnumSet.of(BRANCH_STOCK_VIEW, BRANCH_SALE_CREATE);
    }

    public static Set<Permission> availableFor(Role role) {
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
        if (!effective.contains(PRODUCTS_VIEW)) effective.removeAll(EnumSet.of(PRODUCTS_CREATE, PRODUCTS_EDIT, PRODUCTS_DELETE));
        if (!effective.contains(STOCK_VIEW)) effective.remove(STOCK_MANAGE);
        if (!effective.contains(STOCK_VIEW)) effective.removeAll(EnumSet.of(STOCK_TRANSFER, STOCK_TYPES_MANAGE));
        if (!effective.contains(BRANCH_STOCK_VIEW)) effective.remove(BRANCH_STOCK_MANAGE);
        if (!effective.contains(BRANCHES_VIEW)) effective.remove(BRANCHES_MANAGE);
        return effective;
    }

    public static boolean validDependencies(Set<Permission> selected) {
        return (!selected.contains(BRANCH_SALE_CREATE) && !selected.contains(BRANCH_STOCK_MANAGE) || selected.contains(BRANCH_STOCK_VIEW))
                && (!selected.contains(ORDERS_CREATE) || selected.contains(ORDERS_VIEW))
                && (!selected.contains(STOCK_MANAGE) || selected.contains(STOCK_VIEW))
                && (!selected.contains(STOCK_TRANSFER) || selected.contains(STOCK_VIEW))
                && (!selected.contains(STOCK_TYPES_MANAGE) || selected.contains(STOCK_VIEW))
                && (!selected.contains(BRANCHES_MANAGE) || selected.contains(BRANCHES_VIEW))
                && (java.util.Collections.disjoint(selected, EnumSet.of(PRODUCTS_CREATE, PRODUCTS_EDIT, PRODUCTS_DELETE)) || selected.contains(PRODUCTS_VIEW));
    }
}

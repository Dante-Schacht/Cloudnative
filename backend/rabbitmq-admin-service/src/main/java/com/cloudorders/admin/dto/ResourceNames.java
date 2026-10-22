package com.cloudorders.admin.dto;

/** Patrones de validación compartidos por los DTO y el servicio. */
public final class ResourceNames {

    public static final String NAME_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$";
    public static final String ROUTING_KEY_PATTERN = "^[A-Za-z0-9._*#-]{0,255}$";

    private ResourceNames() {
    }
}

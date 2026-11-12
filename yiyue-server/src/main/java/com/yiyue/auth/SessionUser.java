package com.yiyue.auth;

import java.io.Serializable;

public record SessionUser(long id, long tenantId, String name, String role)
    implements Serializable {}

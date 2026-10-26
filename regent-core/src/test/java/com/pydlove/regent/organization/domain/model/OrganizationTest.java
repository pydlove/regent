package com.pydlove.regent.organization.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrganizationTest {

    @Test
    @DisplayName("组织创建后为启用状态并保留创建人")
    void createsActiveOrganization() {
        Organization organization = Organization.create(
                "hangzhou_rd", "杭州研发中心", "负责 Regent 平台研发", UserId.of(7));

        assertEquals("hangzhou_rd", organization.getCode());
        assertEquals("杭州研发中心", organization.getName());
        assertEquals(OrganizationStatus.ACTIVE, organization.getStatus());
        assertEquals(UserId.of(7), organization.getCreatedBy());
    }

    @Test
    @DisplayName("组织标识必须适合出现在 URL 中")
    void rejectsInvalidCode() {
        assertThrows(DomainException.class, () -> Organization.create(
                "杭州研发", "杭州研发中心", null, UserId.of(7)));
        assertThrows(DomainException.class, () -> Organization.create(
                "-bad", "错误组织", null, UserId.of(7)));
    }

    @Test
    @DisplayName("组织信息可编辑，标识保持不变")
    void updatesEditableDetails() {
        Organization organization = Organization.create(
                "hangzhou_rd", "杭州研发中心", null, UserId.of(7));

        organization.updateDetails("Regent 研发中心", "统一研发组织");

        assertEquals("hangzhou_rd", organization.getCode());
        assertEquals("Regent 研发中心", organization.getName());
        assertEquals("统一研发组织", organization.getDescription());
    }
}

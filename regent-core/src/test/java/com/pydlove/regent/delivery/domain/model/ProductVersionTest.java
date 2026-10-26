package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductVersionTest {

    private static final ProjectId PROJECT = ProjectId.of(7L);
    private static final UserId CREATOR = UserId.of(1L);

    @Test
    @DisplayName("产品版本创建后默认为规划中")
    void createsPlanningVersion() {
        ProductVersion version = ProductVersion.create(
                PROJECT, "v1.7.0", "组织与权限", "本期完成组织隔离", CREATOR);

        assertEquals(ProductVersionStatus.PLANNING, version.getStatus());
        assertEquals("v1.7.0", version.getVersionNo());
    }

    @Test
    @DisplayName("产品版本号不能为空或包含非法字符")
    void validatesVersionNo() {
        assertThrows(DomainException.class, () -> ProductVersion.create(
                PROJECT, " ", "版本", null, CREATOR));
        assertThrows(DomainException.class, () -> ProductVersion.create(
                PROJECT, "v1 7", "版本", null, CREATOR));
    }

    @Test
    @DisplayName("规划中的版本可以修改，关闭后不可再修改")
    void onlyPlanningVersionCanChange() {
        ProductVersion version = ProductVersion.create(
                PROJECT, "v1.0.0", "首个版本", null, CREATOR);

        version.update("首个正式版本", "补充范围");
        version.close();

        assertEquals(ProductVersionStatus.CLOSED, version.getStatus());
        assertThrows(DomainException.class,
                () -> version.update("再次修改", null));
    }

    @Test
    @DisplayName("版本关闭是终态")
    void closedVersionIsTerminal() {
        ProductVersion version = ProductVersion.create(
                PROJECT, "v1.0.0", "首个版本", null, CREATOR);
        version.close();
        version.close();

        assertEquals(ProductVersionStatus.CLOSED, version.getStatus());
    }
}

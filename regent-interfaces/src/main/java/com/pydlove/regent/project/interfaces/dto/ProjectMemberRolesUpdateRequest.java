package com.pydlove.regent.project.interfaces.dto;

import java.util.List;

/** 整体替换一个项目成员的非 OWNER 角色。 */
public record ProjectMemberRolesUpdateRequest(List<String> roles) {
}

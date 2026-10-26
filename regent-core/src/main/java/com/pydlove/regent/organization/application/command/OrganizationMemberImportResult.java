package com.pydlove.regent.organization.application.command;

import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.organization.domain.model.OrganizationRole;

/**
 * 单行导入结果。
 *
 * <p>只把一次性初始密码放在返回值里，绝不写审计或日志；调用方必须把它写进
 * 一次性下载的结果文件，并在界面明确提示管理员妥善转交。
 */
public record OrganizationMemberImportResult(
        User user,
        OrganizationRole role,
        boolean accountCreated,
        boolean roleAdded,
        String initialPassword) {
}

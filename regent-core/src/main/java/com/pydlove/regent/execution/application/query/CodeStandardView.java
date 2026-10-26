package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.CodeStandard;
import com.pydlove.regent.execution.domain.model.CodeStandardVersion;

/** 开发规范条目及其当前版本。 */
public record CodeStandardView(CodeStandard standard, CodeStandardVersion currentVersion) {
}

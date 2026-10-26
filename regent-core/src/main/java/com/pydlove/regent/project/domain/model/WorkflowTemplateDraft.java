package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 尚未落库的研发流程模板版本。 */
public record WorkflowTemplateDraft(
        String code,
        String name,
        int versionNo,
        String description,
        List<WorkflowTemplateStage> stages) {

    public WorkflowTemplateDraft {
        code = code == null ? null : code.trim();
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
        if (code == null || code.isBlank()) {
            throw new DomainException("流程模板编码不能为空");
        }
        if (code.length() > 64) {
            throw new DomainException("流程模板编码不能超过 64 个字符");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException("流程模板名称不能为空");
        }
        if (name.length() > 160) {
            throw new DomainException("流程模板名称不能超过 160 个字符");
        }
        if (versionNo <= 0) {
            throw new DomainException("流程模板版本号必须大于 0");
        }
        stages = stages == null ? List.of() : List.copyOf(stages);
        if (stages.isEmpty()) {
            throw new DomainException("流程模板至少需要一个阶段");
        }
        if (stages.size() > 50) {
            throw new DomainException("流程模板不能超过 50 个阶段");
        }
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (WorkflowTemplateStage stage : stages) {
            if (!codes.add(stage.code())) {
                throw new DomainException("流程阶段编码重复：" + stage.code());
            }
            if (!orders.add(stage.order())) {
                throw new DomainException("流程阶段顺序重复：" + stage.order());
            }
        }
    }
}

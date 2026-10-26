package com.pydlove.regent.settings.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitCommitSettingsTest {

    @Test
    void 默认规范生成约定式提交并通过校验() {
        GitCommitSettings settings = GitCommitSettings.defaults();

        String message = settings.render(
                "181", "102008", "完善变更审阅", "regent", "Regent",
                List.of("A.java", "B.vue"));

        assertEquals("feat(102008): 完善变更审阅", message);
        settings.validateMessage(message);
    }

    @Test
    void 未关联Bug时自动省略编号() {
        GitCommitSettings settings = GitCommitSettings.defaults();

        String message = settings.render(
                "181", "", "完善变更审阅", "regent", "Regent",
                List.of("A.java"));

        assertEquals("feat: 完善变更审阅", message);
        settings.validateMessage(message);
    }

    @Test
    void 支持企业自定义正文模板() {
        GitCommitSettings settings = new GitCommitSettings(
                true,
                "fix",
                "web",
                "[{taskId}] {title}",
                "Files:\n{files}",
                "",
                "");

        assertEquals("[181] 修复提交\n\nFiles:\nA.java\nB.vue",
                settings.render("181", "", "修复提交", "regent", "Regent",
                        List.of("A.java", "B.vue")));
    }

    @Test
    void 非法正则和不符合规范的提交都失败关闭() {
        assertThrows(DomainException.class, () -> new GitCommitSettings(
                true, "feat", "", "{title}", "", "[", "格式错误"));

        GitCommitSettings settings = GitCommitSettings.defaults();
        assertThrows(DomainException.class,
                () -> settings.validateMessage("随手写一句"));
        assertThrows(DomainException.class,
                () -> settings.validateMessage("feat(10200): 编号不足六位"));
        assertThrows(DomainException.class,
                () -> settings.validateMessage("feat(ABC123): 编号不是数字"));
    }
}

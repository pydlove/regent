package com.pydlove.regent.boot.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 把 ARCHITECTURE.md 的分包规矩变成可执行断言。
 *
 * <p>写在 regent-boot 里是刻意的：它是唯一能看见全部模块的模块，而架构违规往往正是
 * 「A 模块写了本该属于 B 模块的代码」，只有站在全局视角才看得见。
 *
 * <p>这些规则一旦被绕过，架构文档就会慢慢变成历史文件。宁可在这里失败一次，
 * 也不要三个月后在某个启动失败里发现领域层不知何时依赖了 Spring。
 */
@AnalyzeClasses(packages = "com.pydlove.regent", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** 铁律第一条：领域层必须是纯 Java，能脱离 Spring 和数据库跑单测。 */
    @ArchTest
    static final ArchRule 领域层不依赖任何框架 = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "org.apache.ibatis..",
                    "com.baomidou..",
                    "com.fasterxml.jackson..",
                    "jakarta.servlet..",
                    "javax.persistence..",
                    "org.slf4j..",
                    "ch.qos.logback..");

    /** 依赖方向单向：domain 不认识上面三层中的任何一层。 */
    @ArchTest
    static final ArchRule 领域层不被上层反向依赖 = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..infrastructure..", "..interfaces..");

    @ArchTest
    static final ArchRule 应用层不依赖基础设施与接口层 = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..interfaces..");

    /** 接口层只许调应用服务与领域模型，不许直接抓仓储实现或 SQL 映射。 */
    @ArchTest
    static final ArchRule 接口层不依赖基础设施 = noClasses()
            .that().resideInAPackage("..interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");

    /** 仓储实现只允许出现在基础设施层：领域层定义接口，实现永远是适配器。 */
    @ArchTest
    static final ArchRule 仓储实现在基础设施层 = classes()
            .that().haveSimpleNameStartingWith("MyBatis")
            .should().resideInAPackage("..infrastructure..");

    /** 领域对象禁止 Lombok：@Data 会生成 setter，那是贫血模型与绕过状态机的起点。 */
    @ArchTest
    static final ArchRule 领域对象不使用Lombok = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("lombok..");

    /** System.out 不给出处、不分级别，线上等于没有日志。 */
    @ArchTest
    static final ArchRule 禁止SystemOut = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
}

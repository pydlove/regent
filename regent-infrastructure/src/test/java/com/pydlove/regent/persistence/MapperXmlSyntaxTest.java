package com.pydlove.regent.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Mapper XML 语法守卫。
 *
 * <p>这些 XML 不参与编译，写坏的后果要到应用启动才炸，而且报的是
 * 「sqlSessionFactory 创建失败」这种与真实原因隔着十几层的消息（XML 里裸写 {@code <}
 * 就是这么翻车的）。这里在测试期逐个解析，把「后端起不来」提前成「测试红了」。
 */
class MapperXmlSyntaxTest {

    @Test
    @DisplayName("全部 Mapper XML 都是格式正确的 XML")
    void allMapperXmlAreWellFormed() throws Exception {
        Path directory = Paths.get("src/main/resources/mapper");
        assertFalse(!Files.isDirectory(directory), "找不到 mapper 目录：" + directory.toAbsolutePath());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        // DTD 在公网，只验良构性时不需要解析它；关掉外联才能在没有网络的构建机上跑
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream.filter(path -> path.toString().endsWith(".xml")).toList();
        }
        assertFalse(files.isEmpty(), "mapper 目录里没有任何 XML：" + directory.toAbsolutePath());

        for (Path file : files) {
            try (InputStream input = Files.newInputStream(file)) {
                factory.newDocumentBuilder().parse(input);
            } catch (Exception exception) {
                throw new AssertionError(file + " 不是良构 XML：" + exception.getMessage(), exception);
            }
        }
    }
}

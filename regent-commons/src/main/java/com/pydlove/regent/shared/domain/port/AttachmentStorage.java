package com.pydlove.regent.shared.domain.port;

import com.pydlove.regent.shared.kernel.AttachmentScope;

/**
 * 图片附件存储端口 —— 图片本体放哪、怎么命名、怎么读回来，属于外部世界的细节。
 *
 * <p>接口上只有业务对象类型、ID 与扩展名，没有原文件名。调用方拿不到「把用户文件名拼进路径」
 * 这根绳子：原文件名可能带中文，而任务图片路径最终要进 {@code codex exec -i <path>} 的 argv，
 * JVM 按平台字符集编码 argv 时中文会被打成问号。磁盘上只放 UUID + 扩展名，展示名留在数据库里。
 */
public interface AttachmentStorage {

    /**
     * 落盘，返回平台侧的绝对路径。
     *
     * @param extension 带点的扩展名，如 {@code .png}；由共享策略从白名单推导，不来自用户输入
     */
    String store(AttachmentScope scope, long ownerId, String extension, byte[] content);

    /** 读回文件内容。文件不在了要抛 {@code EntityNotFoundException}。 */
    byte[] read(String storagePath);
}

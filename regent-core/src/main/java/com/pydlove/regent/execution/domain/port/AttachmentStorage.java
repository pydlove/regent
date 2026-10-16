package com.pydlove.regent.execution.domain.port;

import com.pydlove.regent.execution.domain.model.TaskId;

/**
 * 附件存储端口 —— 图片本体放哪、怎么命名、怎么读回来，属于外部世界的细节。
 *
 * <p>与 {@code ProjectRegistrationService} 里直接用 {@code Files} 校验路径不同：那是核对一次
 * 用户输入，而这里是真正的存储副作用，且落盘位置要可配置（本地一个目录，将来对象存储是另一套实现）。
 * 凡是「换个环境就要换实现」的东西，一律走端口。
 *
 * <p><b>接口上只有扩展名，没有原文件名。</b>调用方拿不到「把用户文件名拼进路径」这根绳子：
 * 原文件名可能带中文，而参数最终要进 {@code codex exec -i <path>} 的 argv，
 * JVM 按平台字符集编码 argv，中文会被打成问号，模型就找不到文件了（见 CodexCliAdapter 类注释）。
 * 磁盘上只放 UUID + 扩展名，展示用的原名留在数据库里。
 */
public interface AttachmentStorage {

    /**
     * 落盘，返回平台侧的绝对路径。
     *
     * @param extension 带点的扩展名，如 {@code .png}；由应用服务从白名单推导，不来自用户输入
     */
    String store(TaskId taskId, String extension, byte[] content);

    /** 读回文件内容。文件不在了要抛 {@code EntityNotFoundException}（记录还在、文件没了是异常状态）。 */
    byte[] read(String storagePath);
}

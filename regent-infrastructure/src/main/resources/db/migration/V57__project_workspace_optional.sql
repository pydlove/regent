-- 研发项目先建业务主体，本机代码目录由开发成员后续绑定。
ALTER TABLE `project`
    MODIFY COLUMN `path` VARCHAR(512) NULL
        COMMENT '本机工作区绝对路径；未绑定时为空，不参与业务需求流转';

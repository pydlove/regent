package com.pydlove.regent.review.domain.port;

import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffRequest;

/** Git 或文件系统事实读取端口。核心域不直接执行 Git。 */
public interface ReviewDiffReader {

    ReviewDiff read(ReviewDiffRequest request);
}

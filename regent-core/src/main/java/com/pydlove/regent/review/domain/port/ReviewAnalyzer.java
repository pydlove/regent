package com.pydlove.regent.review.domain.port;

import com.pydlove.regent.review.domain.model.ReviewAnalysis;
import com.pydlove.regent.review.domain.model.ReviewAnalysisRequest;

/** AI 或规则预审端口。失败由应用服务降级，不改变代码事实。 */
public interface ReviewAnalyzer {

    ReviewAnalysis analyze(ReviewAnalysisRequest request);
}

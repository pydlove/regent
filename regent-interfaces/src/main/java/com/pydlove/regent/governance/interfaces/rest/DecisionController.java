package com.pydlove.regent.governance.interfaces.rest;

import com.pydlove.regent.governance.application.command.DecisionApprovalService;
import com.pydlove.regent.governance.application.command.ReviewCommand;
import com.pydlove.regent.governance.application.query.DecisionQueryService;
import com.pydlove.regent.governance.domain.repository.DecisionPage;
import com.pydlove.regent.governance.domain.repository.DecisionQuery;
import com.pydlove.regent.governance.interfaces.dto.DecisionReviewRequest;
import com.pydlove.regent.governance.interfaces.dto.DecisionSummaryResponse;
import com.pydlove.regent.interfaces.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 决策单接口。
 *
 * <p>写侧只有批准与驳回两个动作，都返回决策的最新读模型 —— 前端拿到的是「库里现在是什么」，
 * 而不是「你刚才点的是什么」。这条在审批这种场景里特别值钱：并发下别人可能已经批过了，
 * 返回最新状态能让界面立刻显示真实结论，而不是留一个乐观的假象。
 */
@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final DecisionQueryService decisionQueryService;
    private final DecisionApprovalService decisionApprovalService;

    public DecisionController(DecisionQueryService decisionQueryService,
                              DecisionApprovalService decisionApprovalService) {
        this.decisionQueryService = decisionQueryService;
        this.decisionApprovalService = decisionApprovalService;
    }

    @GetMapping
    public PageResponse<DecisionSummaryResponse> list(@RequestParam(required = false) String level,
                                                      @RequestParam(required = false) String scope,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        DecisionPage result = decisionQueryService.list(DecisionQuery.of(level, scope, keyword, page, size));
        return PageResponse.of(
                result.items().stream().map(DecisionSummaryResponse::from).toList(),
                result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    public DecisionSummaryResponse detail(@PathVariable long id) {
        return DecisionSummaryResponse.from(decisionQueryService.get(id));
    }

    @PostMapping("/{id}/approve")
    public DecisionSummaryResponse approve(@PathVariable long id,
                                           @RequestBody(required = false) DecisionReviewRequest request,
                                           HttpServletRequest servletRequest) {
        decisionApprovalService.approve(id, toCommand(request, servletRequest));
        return DecisionSummaryResponse.from(decisionQueryService.get(id));
    }

    @PostMapping("/{id}/reject")
    public DecisionSummaryResponse reject(@PathVariable long id,
                                          @RequestBody(required = false) DecisionReviewRequest request,
                                          HttpServletRequest servletRequest) {
        decisionApprovalService.reject(id, toCommand(request, servletRequest));
        return DecisionSummaryResponse.from(decisionQueryService.get(id));
    }

    /**
     * 来源 IP 只在核销令牌时留痕用。本地直连时它就是 127.0.0.1；将来放在反代后面，
     * 要从 X-Forwarded-For 取，那属于部署配置的一部分，届时在这里统一改一处。
     */
    private static ReviewCommand toCommand(DecisionReviewRequest request, HttpServletRequest servletRequest) {
        if (request == null) {
            return new ReviewCommand(null, null, null, servletRequest.getRemoteAddr());
        }
        return new ReviewCommand(request.choice(), request.note(), request.token(),
                servletRequest.getRemoteAddr());
    }
}

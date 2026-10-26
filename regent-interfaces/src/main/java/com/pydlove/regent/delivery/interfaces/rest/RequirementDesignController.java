package com.pydlove.regent.delivery.interfaces.rest;

import com.pydlove.regent.delivery.application.command.RequirementDesignCommandService;
import com.pydlove.regent.delivery.application.command.SaveDesignModuleCommand;
import com.pydlove.regent.delivery.application.command.SaveModuleDesignCommand;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModule;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModuleId;
import com.pydlove.regent.delivery.interfaces.dto.DesignModuleDesignRequest;
import com.pydlove.regent.delivery.interfaces.dto.DesignModuleRequest;
import com.pydlove.regent.delivery.interfaces.dto.RequirementDeliveryResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 技术负责人给研发派模块，研发维护自己模块的详细设计。 */
@RestController
@RequestMapping("/api/requirement-deliveries/{deliveryId}/modules")
public class RequirementDesignController {

    private final RequirementDesignCommandService commandService;

    public RequirementDesignController(RequirementDesignCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    public RequirementDeliveryResponse.DesignModuleResponse create(
            @PathVariable long deliveryId,
            @RequestBody DesignModuleRequest request,
            HttpServletRequest servletRequest) {
        return toResponse(commandService.create(
                RequirementDeliveryId.of(deliveryId),
                new SaveDesignModuleCommand(
                        request.name(),
                        request.description(),
                        requiredUser(request.assigneeId())),
                currentUser(servletRequest)));
    }

    @PutMapping("/{moduleId}")
    public RequirementDeliveryResponse.DesignModuleResponse updateAssignment(
            @PathVariable long deliveryId,
            @PathVariable long moduleId,
            @RequestBody DesignModuleRequest request,
            HttpServletRequest servletRequest) {
        return toResponse(commandService.updateAssignment(
                RequirementDeliveryId.of(deliveryId),
                RequirementDesignModuleId.of(moduleId),
                new SaveDesignModuleCommand(
                        request.name(),
                        request.description(),
                        requiredUser(request.assigneeId())),
                currentUser(servletRequest)));
    }

    @PutMapping("/{moduleId}/design")
    public RequirementDeliveryResponse.DesignModuleResponse saveDesign(
            @PathVariable long deliveryId,
            @PathVariable long moduleId,
            @RequestBody DesignModuleDesignRequest request,
            HttpServletRequest servletRequest) {
        return toResponse(commandService.saveDesign(
                RequirementDeliveryId.of(deliveryId),
                RequirementDesignModuleId.of(moduleId),
                new SaveModuleDesignCommand(
                        request.designSummary(),
                        request.designContent()),
                currentUser(servletRequest)));
    }

    @PostMapping("/{moduleId}/submit")
    public RequirementDeliveryResponse.DesignModuleResponse submit(
            @PathVariable long deliveryId,
            @PathVariable long moduleId,
            HttpServletRequest servletRequest) {
        return toResponse(commandService.submitModule(
                RequirementDeliveryId.of(deliveryId),
                RequirementDesignModuleId.of(moduleId),
                currentUser(servletRequest)));
    }

    @PostMapping("/{moduleId}/reopen")
    public RequirementDeliveryResponse.DesignModuleResponse reopen(
            @PathVariable long deliveryId,
            @PathVariable long moduleId,
            HttpServletRequest servletRequest) {
        return toResponse(commandService.reopenModule(
                RequirementDeliveryId.of(deliveryId),
                RequirementDesignModuleId.of(moduleId),
                currentUser(servletRequest)));
    }

    @DeleteMapping("/{moduleId}")
    public void delete(
            @PathVariable long deliveryId,
            @PathVariable long moduleId,
            HttpServletRequest servletRequest) {
        commandService.deleteModule(
                RequirementDeliveryId.of(deliveryId),
                RequirementDesignModuleId.of(moduleId),
                currentUser(servletRequest));
    }

    private static RequirementDeliveryResponse.DesignModuleResponse toResponse(
            RequirementDesignModule module) {
        return RequirementDeliveryResponse.DesignModuleResponse.from(module);
    }

    private static UserId requiredUser(Long userId) {
        if (userId == null) throw new IllegalArgumentException("缺少模块研发负责人");
        return UserId.of(userId);
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}

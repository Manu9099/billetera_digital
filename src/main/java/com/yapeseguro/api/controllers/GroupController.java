package com.yapeseguro.api.controllers;

import com.yapeseguro.api.dto.request.AddGroupMemberRequest;
import com.yapeseguro.api.dto.request.CreateGroupRequest;
import com.yapeseguro.api.dto.request.PayGroupContributionRequest;
import com.yapeseguro.api.dto.response.GroupContributionResponse;
import com.yapeseguro.api.dto.response.GroupResponse;
import com.yapeseguro.application.services.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * POST /groups
     * Crea una cuenta grupal: viaje, pollada, trabajo u otro.
     */
    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.createGroup(
                        request,
                        user.getUsername()
                ));
    }

    /**
     * GET /groups
     * Lista grupos creados por mí o donde soy miembro.
     */
    @GetMapping
    public ResponseEntity<List<GroupResponse>> getMyGroups(
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                groupService.getMyGroups(user.getUsername())
        );
    }

    /**
     * GET /groups/{groupId}
     * Detalle del grupo con miembros y avance.
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroupById(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.ok(
                groupService.getGroupById(
                        groupId,
                        user.getUsername()
                )
        );
    }

    /**
     * POST /groups/{groupId}/members
     * Agrega miembro al grupo. Solo creador.
     */
    @PostMapping("/{groupId}/members")
    public ResponseEntity<GroupResponse> addMember(
            @PathVariable UUID groupId,
            @Valid @RequestBody AddGroupMemberRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.addMember(
                        groupId,
                        request,
                        user.getUsername()
                ));
    }

    /**
     * POST /groups/{groupId}/members/{memberId}/pay
     * El miembro paga su cuota. Si no manda amount, paga el pendiente.
     */
    @PostMapping("/{groupId}/members/{memberId}/pay")
    public ResponseEntity<GroupContributionResponse> payContribution(
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @Valid @RequestBody(required = false) PayGroupContributionRequest request,
            @AuthenticationPrincipal UserDetails user
    ) {
        PayGroupContributionRequest safeRequest = request != null
                ? request
                : new PayGroupContributionRequest();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.payContribution(
                        groupId,
                        memberId,
                        safeRequest,
                        user.getUsername()
                ));
    }

    /**
     * DELETE /groups/{groupId}
     * Cancela grupo activo. Solo creador.
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> cancelGroup(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserDetails user
    ) {
        groupService.cancelGroup(
                groupId,
                user.getUsername()
        );

        return ResponseEntity.noContent().build();
    }
}
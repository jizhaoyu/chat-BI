package com.jizhaoyu.chatbi.interfaces.catalog;

import com.jizhaoyu.chatbi.application.catalog.CatalogApplicationService;
import com.jizhaoyu.chatbi.application.catalog.CatalogSyncResult;
import com.jizhaoyu.chatbi.domain.catalog.CatalogColumn;
import com.jizhaoyu.chatbi.domain.catalog.CatalogObjectType;
import com.jizhaoyu.chatbi.domain.catalog.CatalogPermission;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;
import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshotDiff;
import com.jizhaoyu.chatbi.domain.catalog.CatalogTable;
import com.jizhaoyu.chatbi.domain.catalog.SemanticMetadata;
import com.jizhaoyu.chatbi.domain.catalog.SensitivityLevel;
import com.jizhaoyu.chatbi.domain.identity.UserPrincipal;
import com.jizhaoyu.chatbi.interfaces.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogApplicationService service;

    public CatalogController(CatalogApplicationService service) {
        this.service = service;
    }

    @PostMapping("/data-sources/{id}:sync-metadata")
    public SyncResponse synchronize(Authentication authentication, @PathVariable UUID id) {
        CatalogSyncResult result = service.synchronize(AuthenticatedUser.require(authentication), id);
        return new SyncResponse(SnapshotResponse.from(result.snapshot()), result.diff());
    }

    @GetMapping("/data-sources/{id}/catalog")
    public SnapshotResponse active(Authentication authentication, @PathVariable UUID id) {
        return SnapshotResponse.from(service.active(AuthenticatedUser.require(authentication), id));
    }

    @GetMapping("/data-sources/{id}/catalog/diff")
    public CatalogSnapshotDiff diff(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam UUID before,
            @RequestParam UUID after) {
        return service.diff(AuthenticatedUser.require(authentication), id, before, after);
    }

    @PutMapping("/catalog/columns/{columnId}/semantic-config")
    public void updateSemantic(
            Authentication authentication,
            @PathVariable UUID columnId,
            @Valid @RequestBody SemanticRequest request) {
        service.updateColumnSemantic(AuthenticatedUser.require(authentication), request.dataSourceId(), columnId,
                new SemanticMetadata(request.businessName(), request.synonyms(), request.sensitivity()),
                request.enabled());
    }

    @PutMapping("/data-sources/{id}/permissions")
    public void replacePermissions(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody PermissionRequest request) {
        UserPrincipal actor = AuthenticatedUser.require(authentication);
        List<CatalogPermission> grants = request.grants().stream()
                .map(grant -> new CatalogPermission(UUID.randomUUID(), actor.tenantId(), "USER",
                        request.subjectId(), id, grant.objectType(), grant.objectId(), grant.maskPolicy()))
                .toList();
        service.replacePermissions(actor, id, request.subjectId(), grants);
    }

    public record SemanticRequest(
            @NotNull UUID dataSourceId,
            @Size(max = 200) String businessName,
            @NotNull @Size(max = 50) List<@Size(max = 200) String> synonyms,
            @NotNull SensitivityLevel sensitivity,
            boolean enabled) {
    }

    public record PermissionRequest(
            @NotNull UUID subjectId,
            @NotNull @Size(max = 10_000) List<@Valid PermissionGrant> grants) {
    }

    public record PermissionGrant(
            @NotNull CatalogObjectType objectType,
            @NotNull UUID objectId,
            @Size(max = 100) String maskPolicy) {
    }

    public record SyncResponse(SnapshotResponse snapshot, CatalogSnapshotDiff diff) {
    }

    public record SnapshotResponse(
            UUID id, UUID dataSourceId, long version, String status,
            List<TableResponse> tables, int objectCount) {
        static SnapshotResponse from(CatalogSnapshot snapshot) {
            return new SnapshotResponse(snapshot.id(), snapshot.dataSourceId(), snapshot.version(),
                    snapshot.status().name(), snapshot.tables().stream().map(TableResponse::from).toList(),
                    snapshot.objectCount());
        }
    }

    public record TableResponse(
            UUID id, String schema, String name, String businessName,
            SensitivityLevel sensitivity, List<ColumnResponse> columns) {
        static TableResponse from(CatalogTable table) {
            return new TableResponse(table.id(), table.schemaName(), table.name(),
                    table.semantic().businessName(), table.semantic().sensitivity(),
                    table.columns().stream().map(ColumnResponse::from).toList());
        }
    }

    public record ColumnResponse(
            UUID id, String name, String dataType, boolean nullable, String businessName,
            List<String> synonyms, SensitivityLevel sensitivity) {
        static ColumnResponse from(CatalogColumn column) {
            return new ColumnResponse(column.id(), column.name(), column.dataType(), column.nullable(),
                    column.semantic().businessName(), column.semantic().synonyms(),
                    column.semantic().sensitivity());
        }
    }
}

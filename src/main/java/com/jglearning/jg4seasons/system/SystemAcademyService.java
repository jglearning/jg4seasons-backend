package com.jglearning.jg4seasons.system;

import com.jglearning.jg4seasons.academy.api.AcademyRegistry;
import com.jglearning.jg4seasons.academy.api.AcademySummary;
import com.jglearning.jg4seasons.academy.api.NewAcademy;
import com.jglearning.jg4seasons.auth.api.NewStaff;
import com.jglearning.jg4seasons.auth.api.Operators;
import com.jglearning.jg4seasons.auth.api.StaffAccount;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import com.jglearning.jg4seasons.infra.audit.AuditAction;
import com.jglearning.jg4seasons.infra.audit.AuditLogger;
import com.jglearning.jg4seasons.infra.audit.AuditTarget;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.system.web.dto.AcademyCreateRequest;
import com.jglearning.jg4seasons.system.web.dto.AcademyCreatedResponse;
import com.jglearning.jg4seasons.system.web.dto.AcademyListResponse;
import com.jglearning.jg4seasons.system.web.dto.AcademyResponse;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 7.1·7.2 운영자 학원 목록·만들기(SYS-07, ADR 0031·0032). 학원은 academy, 첫 관리자는 auth, 감사 기록은 infra — 한 트랜잭션에서 조립한다(관리자
 * 아이디가 겹치면 학원도 남지 않음). actorId는 컨트롤러가 JWT에서 넘긴다. 모든 메서드는 먼저 요청한 사람이 지금도 활성 운영자인지 확인한다(남은 access 대비).
 */
@Service
@Transactional
public class SystemAcademyService {

    static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Asia/Seoul");

    private final Operators operators;
    private final AcademyRegistry academies;
    private final StaffAccounts staffAccounts;
    private final AuditLogger auditLogger;

    SystemAcademyService(
            Operators operators, AcademyRegistry academies, StaffAccounts staffAccounts, AuditLogger auditLogger) {
        this.operators = operators;
        this.academies = academies;
        this.staffAccounts = staffAccounts;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public AcademyListResponse list(long actorId) {
        operators.requireActiveOperator(actorId);
        return new AcademyListResponse(
                academies.list().stream().map(SystemAcademyService::toResponse).toList());
    }

    public AcademyCreatedResponse create(long actorId, AcademyCreateRequest request) {
        operators.requireActiveOperator(actorId);
        AcademySummary academy = academies.create(new NewAcademy(
                request.name(), request.slug(), timezone(request.timezone()), request.licenseEnforced()));
        AcademyCreateRequest.Admin admin = request.admin();
        StaffAccount account = staffAccounts.create(
                academy.id(), new NewStaff(admin.name(), admin.loginId(), admin.password(), StaffRole.ADMIN));
        // 비밀번호는 남기지 않는다
        auditLogger.record(
                academy.id(),
                actorId,
                AuditAction.ACADEMY_CREATE,
                AuditTarget.ACADEMY,
                academy.id(),
                Map.of("slug", academy.slug(), "licenseEnforced", academy.licenseEnforced(), "adminId", account.id()));
        return new AcademyCreatedResponse(
                academy.id(),
                academy.name(),
                academy.slug(),
                academy.timezone().getId(),
                academy.licenseEnforced(),
                academy.status(),
                new AcademyCreatedResponse.AdminRef(account.id(), account.loginId(), account.name()));
    }

    static AcademyResponse toResponse(AcademySummary academy) {
        return new AcademyResponse(
                academy.id(),
                academy.name(),
                academy.slug(),
                academy.timezone().getId(),
                academy.licenseEnforced(),
                academy.status());
    }

    /** 없으면 Asia/Seoul. IANA 이름만 — 오프셋(+09:00)은 서머타임 지역의 날짜를 틀리게 만든다 */
    static ZoneId timezone(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        try {
            ZoneId zone = ZoneId.of(raw.strip());
            if (!(zone instanceof ZoneOffset)) {
                return zone;
            }
        } catch (DateTimeException e) {
            // 아래 400으로
        }
        throw ApiException.validation("timezone", "IANA 타임존 이름이에요(예: Asia/Seoul)");
    }
}

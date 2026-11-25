package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
class AcademyDirectoryService implements AcademyDirectory {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private final AcademyRepository academies;
    private final JsonMapper jsonMapper;

    AcademyDirectoryService(AcademyRepository academies, JsonMapper jsonMapper) {
        this.academies = academies;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String nameOf(long academyId) {
        return academies
                .findById(academyId)
                .map(Academy::getName)
                .orElseThrow(() -> new IllegalStateException("학원이 없다: " + academyId));
    }

    @Override
    public boolean licenseEnforced(long academyId) {
        return academies
                .findById(academyId)
                .map(Academy::isLicenseEnforced)
                .orElseThrow(() -> new IllegalStateException("학원이 없다: " + academyId));
    }

    @Override
    public boolean isActive(long academyId) {
        return academies.findById(academyId).map(Academy::isActive).orElse(false);
    }

    @Override
    public ZoneId zoneOf(long academyId) {
        JsonNode settings = settingsOf(academyId);
        if (settings == null) {
            return DEFAULT_ZONE;
        }
        JsonNode timezone = settings.get("timezone");
        if (timezone == null || timezone.isNull()) {
            return DEFAULT_ZONE;
        }
        if (!timezone.isString()) {
            throw new IllegalStateException("학원 타임존이 문자열이 아니다: " + academyId);
        }
        try {
            return ZoneId.of(timezone.asString());
        } catch (DateTimeException e) {
            throw new IllegalStateException("학원 타임존이 잘못됐다: " + academyId, e);
        }
    }

    @Override
    public boolean wordTestEnabled(long academyId) {
        return feature(academyId, "wordTest");
    }

    @Override
    public boolean talkEnabled(long academyId) {
        return feature(academyId, "talk");
    }

    @Override
    public boolean talkEntitled(long academyId) {
        return feature(academyId, "talkEntitled");
    }

    /** settings.features.{key}가 JSON true인가. 없음·null·false는 꺼짐, 불리언이 아니면 IllegalStateException */
    private boolean feature(long academyId, String key) {
        JsonNode settings = settingsOf(academyId);
        if (settings == null) {
            return false;
        }
        JsonNode features = settings.get("features");
        if (features == null || features.isNull()) {
            return false;
        }
        // 운영은 SQL로 직접 켠다 — 형식 실수를 "꺼짐"으로 조용히 넘기지 않는다(settings가 객체가 아닐 때와 같은 정책)
        if (!features.isObject()) {
            throw new IllegalStateException("학원 settings.features가 객체가 아니다: " + academyId);
        }
        JsonNode value = features.get(key);
        if (value == null || value.isNull()) {
            return false;
        }
        if (!value.isBoolean()) {
            throw new IllegalStateException("학원 settings.features." + key + "가 true·false가 아니다: " + academyId);
        }
        return value.booleanValue();
    }

    /** settings JSON 객체, 비어 있으면 null. 없는 학원·객체가 아닌 settings는 IllegalStateException */
    private JsonNode settingsOf(long academyId) {
        Academy academy =
                academies.findById(academyId).orElseThrow(() -> new IllegalStateException("학원이 없다: " + academyId));
        if (academy.getSettings() == null) {
            return null;
        }
        JsonNode settings = jsonMapper.readTree(academy.getSettings());
        if (!settings.isObject()) {
            throw new IllegalStateException("학원 settings가 객체가 아니다: " + academyId);
        }
        return settings;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForAdminWrite(long academyId) {
        academies.lockById(academyId);
    }

    @Override
    public Optional<Long> findIdBySlug(String slug) {
        return academies.findBySlug(slug).map(Academy::getId);
    }
}

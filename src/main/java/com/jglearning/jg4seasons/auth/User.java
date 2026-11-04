package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.infra.persistence.AcademyScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 사용자(학생·교사·관리자). 개인정보는 이름·학교·학년만(학교·학년은 명단 이슈에서 매핑). */
@Entity
@Table(name = "`user`")
public class User implements AcademyScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 시스템 관리자는 null */
    @Column(name = "academy_id")
    private Long academyId;

    @Convert(converter = UserRole.DbConverter.class)
    @Column(nullable = false, columnDefinition = "enum('student','teacher','admin','superadmin')")
    private UserRole role;

    @Column(nullable = false)
    private String name;

    @Convert(converter = UserStatus.DbConverter.class)
    @Column(nullable = false, columnDefinition = "enum('active','inactive')")
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {}

    /** 교사·관리자(관리자 API가 만든다) */
    static User staff(long academyId, String name, UserRole role) {
        if (role != UserRole.TEACHER && role != UserRole.ADMIN) {
            throw new IllegalArgumentException("교사·관리자만 만든다: " + role);
        }
        User user = new User();
        user.academyId = academyId;
        user.role = role;
        user.name = name;
        user.status = UserStatus.ACTIVE;
        return user;
    }

    void rename(String name) {
        this.name = name;
    }

    void changeStatus(UserStatus status) {
        this.status = status;
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    @Override
    public Long getAcademyId() {
        return academyId;
    }

    public UserRole getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}

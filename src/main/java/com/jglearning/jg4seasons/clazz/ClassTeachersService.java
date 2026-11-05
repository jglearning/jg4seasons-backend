package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassTeachers;
import com.jglearning.jg4seasons.clazz.persistence.ClassTeacherStore;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ClassTeachersService implements ClassTeachers {

    private final ClassTeacherStore store;
    private final ClassRepository classes;

    ClassTeachersService(ClassTeacherStore store, ClassRepository classes) {
        this.store = store;
        this.classes = classes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> classIdsOf(long academyId, long teacherId) {
        return store.classIdsOf(academyId, teacherId);
    }

    @Override
    public void replace(long academyId, long teacherId, Set<Long> classIds) {
        Set<Long> ids = new TreeSet<>(classIds);
        if (store.countClassesInAcademy(academyId, ids) != ids.size()) {
            throw notFound();
        }
        store.deleteInAcademy(academyId, teacherId);
        ids.forEach(classId -> store.insert(classId, teacherId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> teacherIdsOf(long academyId, long classId) {
        classes.findByAcademyIdAndId(academyId, classId).orElseThrow(ClassTeachersService::notFound);
        return store.teacherIdsOf(classId);
    }

    @Override
    public void replaceTeachersOf(long academyId, long classId, Set<Long> teacherIds) {
        // 반 행 잠금 → 같은 반 동시 변경을 줄 세운다. 범위 delete 대신 차이만 PK로 반영해 gap lock 교착을 피한다
        classes.findByAcademyIdAndIdForUpdate(academyId, classId).orElseThrow(ClassTeachersService::notFound);
        Set<Long> current = new TreeSet<>(store.teacherIdsOf(classId));
        Set<Long> wanted = new TreeSet<>(teacherIds);
        for (Long removed : current) {
            if (!wanted.contains(removed)) {
                store.delete(classId, removed);
            }
        }
        for (Long added : wanted) {
            if (!current.contains(added)) {
                store.insert(classId, added);
            }
        }
    }

    private static ApiException notFound() {
        // 다른 학원 반·없는 반 — 존재를 드러내지 않게 404(API 명세 §0.3)
        return new ApiException(CommonErrorCode.NOT_FOUND, "반을 찾을 수 없어요");
    }
}

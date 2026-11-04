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

    ClassTeachersService(ClassTeacherStore store) {
        this.store = store;
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
            // 다른 학원 반·없는 반 — 존재를 드러내지 않게 404(API 명세 §0.3)
            throw new ApiException(CommonErrorCode.NOT_FOUND, "반을 찾을 수 없어요");
        }
        store.deleteInAcademy(academyId, teacherId);
        ids.forEach(classId -> store.insert(classId, teacherId));
    }
}

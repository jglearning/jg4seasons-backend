package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.auth.api.Operators;
import com.jglearning.jg4seasons.auth.persistence.AuthUser;
import com.jglearning.jg4seasons.auth.persistence.PreAuthUserLookup;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class OperatorsService implements Operators {

    private final PreAuthUserLookup users;

    OperatorsService(PreAuthUserLookup users) {
        this.users = users;
    }

    @Override
    public void requireActiveOperator(long actorId) {
        users.findActiveUser(actorId)
                .filter(AuthUser::isOperator)
                .orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHORIZED));
    }
}

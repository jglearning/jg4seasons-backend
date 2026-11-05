package com.jglearning.archfixture.words;

import com.jglearning.archfixture.listening.apix.Lookalike;

/** 위반: listening.apix는 api 패키지가 아니다. */
public class ApiPrefixUser {
    public int value() {
        return new Lookalike().value();
    }
}

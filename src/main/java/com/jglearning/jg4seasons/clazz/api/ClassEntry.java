package com.jglearning.jg4seasons.clazz.api;

/** 입장할 수 있는(활성) 반. */
public record ClassEntry(long id, long academyId, long bookId, String name) {}

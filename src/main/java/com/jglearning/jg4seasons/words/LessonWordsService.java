package com.jglearning.jg4seasons.words;

import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.content.api.LessonWord;
import com.jglearning.jg4seasons.infra.audio.AudioProperties;
import com.jglearning.jg4seasons.words.web.dto.LessonWordsResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.4 회차 단어. 학생·학원은 JWT에서만. 열린 회차인지 확인한 뒤(OpenLessons) 교재 순서로 단어를 준다. */
@Service
@Transactional(readOnly = true)
public class LessonWordsService {

    private final OpenLessons lessons;
    private final LessonCatalog catalog;
    private final AudioProperties audio;

    LessonWordsService(OpenLessons lessons, LessonCatalog catalog, AudioProperties audio) {
        this.lessons = lessons;
        this.catalog = catalog;
        this.audio = audio;
    }

    public LessonWordsResponse words(long academyId, long studentId, String lessonCode) {
        LessonContent lesson = lessons.require(academyId, studentId, lessonCode);
        List<LessonWordsResponse.WordItem> items =
                catalog.words(lesson.lessonId()).stream().map(this::item).toList();
        return new LessonWordsResponse(items, items.size());
    }

    private LessonWordsResponse.WordItem item(LessonWord word) {
        return new LessonWordsResponse.WordItem(
                word.id(),
                word.order(),
                word.source(),
                word.english(),
                word.korean(),
                word.pos(),
                word.exampleEn(),
                word.exampleKo(),
                audio(word.audio()),
                audio(word.exampleAudio()));
    }

    private LessonWordsResponse.WordAudio audio(LessonWord.Audio track) {
        return track == null
                ? null
                : new LessonWordsResponse.WordAudio(track.trackId(), audio.urlOf(track.fileKey()), track.durationMs());
    }
}

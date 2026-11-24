package com.jglearning.jg4seasons.infra.storage;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 테스트용 사진 저장소(외부 호출 없음). 테스트가 {@link #put}으로 "올라간 사진"을 만든다. 테스트마다 {@link #clear} */
public class MemoryPhotoStorage implements PhotoStorage {

    private record Photo(byte[] bytes, String contentType) {}

    private final Map<String, Photo> photos = new ConcurrentHashMap<>();
    private final Clock clock;

    public MemoryPhotoStorage(Clock clock) {
        this.clock = clock;
    }

    public void put(String key, byte[] bytes, String contentType) {
        photos.put(key, new Photo(bytes.clone(), contentType));
    }

    /** 내용 없이 크기만 정한 사진(크기 한도 테스트용) */
    public void putSized(String key, int size, String contentType) {
        put(key, new byte[size], contentType);
    }

    public void clear() {
        photos.clear();
    }

    @Override
    public UploadTicket presignPut(String key, String contentType, Duration ttl) {
        return new UploadTicket(
                URI.create("https://photos.test/" + key + "?signed"),
                Map.of("Content-Type", contentType),
                clock.instant().plus(ttl));
    }

    @Override
    public Optional<StoredPhoto> head(String key) {
        Photo photo = photos.get(key);
        return photo == null
                ? Optional.empty()
                : Optional.of(new StoredPhoto(photo.bytes().length, photo.contentType()));
    }

    @Override
    public byte[] read(String key, long maxBytes) {
        Photo photo = photos.get(key);
        if (photo == null) {
            throw new NoSuchElementException("사진이 없다");
        }
        if (photo.bytes().length > maxBytes) {
            throw new PhotoTooLargeException(maxBytes);
        }
        return photo.bytes().clone();
    }

    @Override
    public Optional<URI> presignGet(String key, Duration ttl) {
        return Optional.of(URI.create("https://photos.test/" + key + "?view"));
    }
}

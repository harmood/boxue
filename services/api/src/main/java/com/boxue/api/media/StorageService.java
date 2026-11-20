package com.boxue.api.media;

public interface StorageService {

    void put(String key, byte[] data, String contentType);

    byte[] get(String key);

    void delete(String key);
}

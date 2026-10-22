package com.nageoffer.ai.ragent.framework.convention;

import cn.hutool.crypto.digest.DigestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrievedChunkKeyTest {

    @Test
    void prefersChunkId() {
        RetrievedChunk chunk = RetrievedChunk.builder().id("chunk-id").text("text").build();

        assertEquals("chunk-id", RetrievedChunkKey.of(chunk));
    }

    @Test
    void hashesTextWhenIdIsMissing() {
        RetrievedChunk chunk = RetrievedChunk.builder().text("text").build();

        assertEquals(DigestUtil.sha256Hex("text"), RetrievedChunkKey.of(chunk));
    }

    @Test
    void hashesTextWhenIdIsBlank() {
        RetrievedChunk chunk = RetrievedChunk.builder().id(" ").text("text").build();

        assertEquals(DigestUtil.sha256Hex("text"), RetrievedChunkKey.of(chunk));
    }

    @Test
    void hashesEmptyTextWhenIdAndTextAreMissing() {
        assertEquals(DigestUtil.sha256Hex(""), RetrievedChunkKey.of(RetrievedChunk.builder().build()));
    }
}

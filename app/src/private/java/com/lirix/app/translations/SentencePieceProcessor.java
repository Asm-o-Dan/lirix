package com.lirix.app.translations;

/** Thin owner of the official SentencePiece processor; no network or model approximation. */
final class SentencePieceProcessor implements AutoCloseable {
    static { System.loadLibrary("lirix_sentencepiece"); }
    private long pointer;
    SentencePieceProcessor(byte[] model) { pointer = create(model); }
    synchronized String[] encode(String text) { checkOpen(); return encodePieces(pointer, text); }
    synchronized String decode(String[] pieces) { checkOpen(); return decodePieces(pointer, pieces); }
    private void checkOpen() { if (pointer == 0) throw new IllegalStateException("SentencePiece is closed"); }
    @Override public synchronized void close() { if (pointer != 0) { destroy(pointer); pointer = 0; } }
    private static native long create(byte[] model);
    private static native String[] encodePieces(long pointer, String text);
    private static native String decodePieces(long pointer, String[] pieces);
    private static native void destroy(long pointer);
}

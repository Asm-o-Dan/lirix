#include <jni.h>
#include <sentencepiece_processor.h>
#include <codecvt>
#include <locale>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
using Processor = sentencepiece::SentencePieceProcessor;
void fail(JNIEnv* env, const char* message) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), message);
}
std::string utf8(JNIEnv* env, jstring value) {
    if (!value) throw std::invalid_argument("Null text");
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (!chars) throw std::runtime_error("Cannot read text");
    std::u16string copied(reinterpret_cast<const char16_t*>(chars), length);
    env->ReleaseStringChars(value, chars);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().to_bytes(copied);
}
jstring utf16(JNIEnv* env, const std::string& value) {
    auto decoded = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().from_bytes(value);
    return env->NewString(reinterpret_cast<const jchar*>(decoded.data()), static_cast<jsize>(decoded.size()));
}
Processor* processor(jlong pointer) {
    if (!pointer) throw std::invalid_argument("Closed processor");
    return reinterpret_cast<Processor*>(pointer);
}
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_lirix_app_translations_SentencePieceProcessor_create(JNIEnv* env, jclass, jbyteArray model) {
    try {
        if (!model || env->GetArrayLength(model) <= 0 || env->GetArrayLength(model) > 4000000) throw std::invalid_argument("Invalid model size");
        std::string data(env->GetArrayLength(model), '\0');
        env->GetByteArrayRegion(model, 0, static_cast<jsize>(data.size()), reinterpret_cast<jbyte*>(data.data()));
        if (env->ExceptionCheck()) return 0;
        auto owned = std::make_unique<Processor>();
        if (!owned->LoadFromSerializedProto(data).ok()) throw std::invalid_argument("Invalid SentencePiece model");
        return reinterpret_cast<jlong>(owned.release());
    } catch (...) { fail(env, "Could not load SentencePiece model"); return 0; }
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_lirix_app_translations_SentencePieceProcessor_encodePieces(JNIEnv* env, jclass, jlong pointer, jstring text) {
    try {
        std::vector<std::string> pieces;
        if (!processor(pointer)->Encode(utf8(env, text), &pieces).ok()) throw std::runtime_error("Encode failed");
        auto result = env->NewObjectArray(static_cast<jsize>(pieces.size()), env->FindClass("java/lang/String"), nullptr);
        if (!result) return nullptr;
        for (size_t i = 0; i < pieces.size(); ++i) {
            auto value = utf16(env, pieces[i]);
            env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
            env->DeleteLocalRef(value);
            if (env->ExceptionCheck()) return nullptr;
        }
        return result;
    } catch (...) { fail(env, "Could not tokenize text"); return nullptr; }
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_lirix_app_translations_SentencePieceProcessor_decodePieces(JNIEnv* env, jclass, jlong pointer, jobjectArray values) {
    try {
        if (!values || env->GetArrayLength(values) > 1024) throw std::invalid_argument("Invalid token count");
        std::vector<std::string> pieces;
        for (jsize i = 0; i < env->GetArrayLength(values); ++i) {
            auto value = static_cast<jstring>(env->GetObjectArrayElement(values, i));
            pieces.push_back(utf8(env, value)); env->DeleteLocalRef(value);
        }
        std::string decoded;
        if (!processor(pointer)->Decode(pieces, &decoded).ok()) throw std::runtime_error("Decode failed");
        return utf16(env, decoded);
    } catch (...) { fail(env, "Could not decode translation"); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_lirix_app_translations_SentencePieceProcessor_destroy(JNIEnv*, jclass, jlong pointer) {
    delete reinterpret_cast<Processor*>(pointer);
}

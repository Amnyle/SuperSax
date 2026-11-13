// JNI bridge to Signalsmith Stretch (MIT): high-quality time stretch and pitch shift for the song player.
#include <jni.h>
#include <vector>
#include "signalsmith-stretch.h"

namespace {
using Stretch = signalsmith::stretch::SignalsmithStretch<float>;

struct Handle {
    Stretch stretch;
    int channels;
    std::vector<std::vector<float>> in, out;
};

Handle *get(jlong h) { return reinterpret_cast<Handle *>(h); }

void deinterleave(JNIEnv *env, jfloatArray src, int frames, int channels, std::vector<std::vector<float>> &dst) {
    if ((int) dst.size() != channels) dst.resize(channels);
    for (auto &d : dst) if ((int) d.size() < frames) d.resize(frames);
    jfloat *p = env->GetFloatArrayElements(src, nullptr);
    for (int i = 0; i < frames; i++) for (int c = 0; c < channels; c++) dst[c][i] = p[i * channels + c];
    env->ReleaseFloatArrayElements(src, p, JNI_ABORT);
}
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_app_supersax_audio_NativeStretch_create0(JNIEnv *, jclass, jint channels, jfloat rate) {
    auto *h = new Handle();
    h->channels = channels;
    h->stretch.presetDefault(channels, rate, true);
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT void JNICALL Java_app_supersax_audio_NativeStretch_destroy(JNIEnv *, jclass, jlong h) { delete get(h); }

JNIEXPORT void JNICALL Java_app_supersax_audio_NativeStretch_reset(JNIEnv *, jclass, jlong h) { get(h)->stretch.reset(); }

JNIEXPORT void JNICALL Java_app_supersax_audio_NativeStretch_setTranspose(JNIEnv *, jclass, jlong h, jfloat semitones, jfloat tonalityLimit) {
    get(h)->stretch.setTransposeSemitones(semitones, tonalityLimit);
}

JNIEXPORT jint JNICALL Java_app_supersax_audio_NativeStretch_inputLatency(JNIEnv *, jclass, jlong h) { return get(h)->stretch.inputLatency(); }

JNIEXPORT jint JNICALL Java_app_supersax_audio_NativeStretch_outputLatency(JNIEnv *, jclass, jlong h) { return get(h)->stretch.outputLatency(); }

JNIEXPORT void JNICALL Java_app_supersax_audio_NativeStretch_seek(JNIEnv *env, jclass, jlong hp, jfloatArray input, jint frames, jdouble rate) {
    Handle *h = get(hp);
    deinterleave(env, input, frames, h->channels, h->in);
    h->stretch.seek(h->in, frames, rate);
}

JNIEXPORT void JNICALL Java_app_supersax_audio_NativeStretch_process(JNIEnv *env, jclass, jlong hp, jfloatArray input, jint inFrames,
                                                                     jfloatArray output, jint outFrames) {
    Handle *h = get(hp);
    deinterleave(env, input, inFrames, h->channels, h->in);
    if ((int) h->out.size() != h->channels) h->out.resize(h->channels);
    for (auto &o : h->out) if ((int) o.size() < outFrames) o.resize(outFrames);
    h->stretch.process(h->in, inFrames, h->out, outFrames);
    jfloat *p = env->GetFloatArrayElements(output, nullptr);
    for (int i = 0; i < outFrames; i++) for (int c = 0; c < h->channels; c++) p[i * h->channels + c] = h->out[c][i];
    env->ReleaseFloatArrayElements(output, p, 0);
}

}  // extern "C"

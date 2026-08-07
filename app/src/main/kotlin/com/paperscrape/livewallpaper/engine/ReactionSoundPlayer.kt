package com.paperscrape.livewallpaper.engine

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Plays a short, playful sound when the user taps an animal in the scene.
 *
 * Deliberately uses [ToneGenerator] (built into Android, zero asset files) instead of raw audio
 * resources: it keeps the project 100% self-contained and compilable without anyone needing to
 * source/license bark or meow sound files first. Swapping this for real recorded sounds later is
 * a natural follow-up — see the TODO below and CONTRIBUTING.md.
 *
 * TODO(sound-assets): replace with SoundPool + res/raw/{bark,meow,honk}.ogg once real sound
 * effects are sourced (must be original or properly licensed).
 */
class ReactionSoundPlayer {

    private var toneGenerator: ToneGenerator? = null

    private fun generator(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            } catch (_: RuntimeException) {
                null // some devices/emulators without an audio output can fail to allocate this
            }
        }
        return toneGenerator
    }

    fun playFor(type: SceneObjectType) {
        val gen = generator() ?: return
        val tone = when (type) {
            SceneObjectType.DOG -> ToneGenerator.TONE_PROP_BEEP // short low "bark"-ish blip
            SceneObjectType.BUNNY -> ToneGenerator.TONE_PROP_BEEP // reuse the low blip, bunnies don't have a distinct sound
            SceneObjectType.PENGUIN -> ToneGenerator.TONE_PROP_BEEP2 // short higher "squawk"-ish blip
            SceneObjectType.CAR -> ToneGenerator.TONE_CDMA_PIP // brief "honk"
            SceneObjectType.GIFT -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD // brighter "unwrap" chime
            SceneObjectType.TREE, SceneObjectType.SNOWMAN, SceneObjectType.PALM_TREE ->
                ToneGenerator.TONE_PROP_ACK // soft, short "rustle"-ish blip
            else -> return
        }
        gen.startTone(tone, 140)
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}

package io.github.duwenji.yorisoi.kit.songs

import android.media.AudioAttributes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherAudioTest {
    @Test
    fun `another app's video or music counts`() {
        assertTrue(OtherAudio.isOthers(AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_MOVIE))
        assertTrue(OtherAudio.isOthers(AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_MUSIC))
        assertTrue(OtherAudio.isOthers(AudioAttributes.USAGE_GAME, AudioAttributes.CONTENT_TYPE_UNKNOWN))
    }

    @Test
    fun `the app's own background sound and voice don't`() {
        assertFalse(OtherAudio.isOthers(AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_SONIFICATION))
        assertFalse(OtherAudio.isOthers(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, AudioAttributes.CONTENT_TYPE_SPEECH))
    }
}

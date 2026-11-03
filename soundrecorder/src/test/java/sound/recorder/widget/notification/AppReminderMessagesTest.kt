package sound.recorder.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppReminderMessagesTest {

    private val languages = listOf(
        "en", "id", "in", "ms", "es", "pt", "fr", "de", "nl", "ru",
        "ar", "hi", "ja", "ko", "zh", "th", "vi", "fil", "tl"
    )

    @Test
    fun greetingFollowsHourInEnglish() {
        val en = Locale.ENGLISH
        assertEquals("Hi, good morning 👋", AppReminderMessages.build("Gendang", 4, en).first)
        assertEquals("Hi, good morning 👋", AppReminderMessages.build("Gendang", 10, en).first)
        assertEquals("Hi, good afternoon 👋", AppReminderMessages.build("Gendang", 11, en).first)
        assertEquals("Hi, good afternoon 👋", AppReminderMessages.build("Gendang", 17, en).first)
        assertEquals("Hi, good evening 👋", AppReminderMessages.build("Gendang", 18, en).first)
        assertEquals("Hi, good evening 👋", AppReminderMessages.build("Gendang", 3, en).first)
    }

    @Test
    fun indonesianUsesFourSlots() {
        val id = Locale("id")
        assertEquals("Hai, selamat pagi 👋", AppReminderMessages.build("Gendang", 8, id).first)
        assertEquals("Hai, selamat siang 👋", AppReminderMessages.build("Gendang", 12, id).first)
        assertEquals("Hai, selamat sore 👋", AppReminderMessages.build("Gendang", 16, id).first)
        assertEquals("Hai, selamat malam 👋", AppReminderMessages.build("Gendang", 21, id).first)
    }

    @Test
    fun oldIndonesianCodeAndTagalogAreMapped() {
        assertEquals(
            AppReminderMessages.build("X", 8, Locale("id")).first,
            AppReminderMessages.build("X", 8, Locale("in")).first
        )
        assertEquals(
            AppReminderMessages.build("X", 8, Locale("fil")).first,
            AppReminderMessages.build("X", 8, Locale("tl")).first
        )
    }

    @Test
    fun unknownLanguageFallsBackToEnglish() {
        assertEquals(
            AppReminderMessages.build("X", 8, Locale.ENGLISH).first,
            AppReminderMessages.build("X", 8, Locale("sw")).first
        )
    }

    @Test
    fun everyLanguageAndHourHasAppNameAndNoPlaceholder() {
        for (code in languages) {
            for (hour in 0..23) {
                repeat(5) { // isi dipilih acak
                    val (title, body) = AppReminderMessages.build("Kendang Sunda", hour, Locale(code))
                    assertTrue("$code jam $hour: judul kosong", title.isNotBlank())
                    assertTrue("$code: '$body' tanpa nama app", body.contains("Kendang Sunda"))
                    assertFalse("$code: '$body' masih ada %", body.contains("%"))
                }
            }
        }
    }
}

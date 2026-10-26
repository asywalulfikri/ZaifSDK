package sound.recorder.widget.notification

import java.util.Locale

/**
 * Teks notifikasi pengingat buka aplikasi, hardcode per bahasa device.
 * Judul = sapaan sesuai jam, isi = salah satu ajakan acak (`%s` = nama aplikasi / alat musik).
 * Bahasa yang tidak ada di mapping memakai bahasa Inggris.
 */
internal object AppReminderMessages {

    private class Lang(
        /** Sapaan: pagi, siang, sore, malam. */
        val greetings: Array<String>,
        val bodies: Array<String>
    )

    private val EN = Lang(
        arrayOf("Hi, good morning", "Hi, good afternoon", "Hi, good afternoon", "Hi, good evening"),
        arrayOf(
            "Let's play %s again! 🎶",
            "%s misses you. Ready to play? 🥁",
            "Got 5 minutes? Play a song on %s 🎵"
        )
    )

    private val ID = Lang(
        arrayOf("Hai, selamat pagi", "Hai, selamat siang", "Hai, selamat sore", "Hai, selamat malam"),
        arrayOf(
            "Ayo main %s lagi yuk! 🎶",
            "%s kangen dimainin nih. Main sebentar yuk 🥁",
            "Ada 5 menit? Yuk mainkan satu lagu di %s 🎵"
        )
    )

    private val LANGS: Map<String, Lang> = mapOf(
        "en" to EN,
        "id" to ID,
        "in" to ID, // kode lama bahasa Indonesia di Java/Android
        "ms" to Lang(
            arrayOf("Hai, selamat pagi", "Hai, selamat tengah hari", "Hai, selamat petang", "Hai, selamat malam"),
            arrayOf(
                "Jom main %s lagi! 🎶",
                "%s rindu nak dimainkan. Jom main sekejap 🥁",
                "Ada 5 minit? Jom mainkan satu lagu dengan %s 🎵"
            )
        ),
        "es" to Lang(
            arrayOf("Hola, buenos días", "Hola, buenas tardes", "Hola, buenas tardes", "Hola, buenas noches"),
            arrayOf(
                "¡Vamos a tocar %s otra vez! 🎶",
                "%s te extraña. ¿Tocamos un rato? 🥁",
                "¿Tienes 5 minutos? Toca una canción en %s 🎵"
            )
        ),
        "pt" to Lang(
            arrayOf("Olá, bom dia", "Olá, boa tarde", "Olá, boa tarde", "Olá, boa noite"),
            arrayOf(
                "Vamos tocar %s de novo! 🎶",
                "%s está com saudade de você. Bora tocar um pouco? 🥁",
                "Tem 5 minutos? Toque uma música com %s 🎵"
            )
        ),
        "fr" to Lang(
            arrayOf("Salut, bonjour", "Salut, bon après-midi", "Salut, bon après-midi", "Salut, bonsoir"),
            arrayOf(
                "On rejoue avec %s ? 🎶",
                "%s t'attend. Prêt à jouer ? 🥁",
                "Tu as 5 minutes ? Joue un morceau sur %s 🎵"
            )
        ),
        "de" to Lang(
            arrayOf("Hallo, guten Morgen", "Hallo, guten Tag", "Hallo, guten Tag", "Hallo, guten Abend"),
            arrayOf(
                "Lust, wieder mit %s zu spielen? 🎶",
                "%s wartet auf dich. Kurz spielen? 🥁",
                "5 Minuten Zeit? Spiel einen Song mit %s 🎵"
            )
        ),
        "nl" to Lang(
            arrayOf("Hoi, goedemorgen", "Hoi, goedemiddag", "Hoi, goedemiddag", "Hoi, goedenavond"),
            arrayOf(
                "Zin om weer met %s te spelen? 🎶",
                "%s mist je. Even spelen? 🥁",
                "5 minuutjes? Speel een liedje op %s 🎵"
            )
        ),
        "ru" to Lang(
            arrayOf("Привет! Доброе утро", "Привет! Добрый день", "Привет! Добрый день", "Привет! Добрый вечер"),
            arrayOf(
                "Давай снова сыграем! Открой %s 🎶",
                "%s ждёт тебя. Сыграем немного? 🥁",
                "Есть 5 минут? Сыграй мелодию в приложении %s 🎵"
            )
        ),
        "ar" to Lang(
            arrayOf("مرحبًا، صباح الخير", "مرحبًا، طاب يومك", "مرحبًا، مساء الخير", "مرحبًا، مساء الخير"),
            arrayOf(
                "هيا نعزف على %s مرة أخرى! 🎶",
                "%s يشتاق إليك. هل نعزف قليلًا؟ 🥁",
                "لديك 5 دقائق؟ اعزف أغنية على %s 🎵"
            )
        ),
        "hi" to Lang(
            arrayOf("नमस्ते, सुप्रभात", "नमस्ते, शुभ दोपहर", "नमस्ते, शुभ संध्या", "नमस्ते, शुभ संध्या"),
            arrayOf(
                "चलो फिर से %s बजाएँ! 🎶",
                "%s आपको याद कर रहा है। थोड़ा बजाएँ? 🥁",
                "5 मिनट हैं? %s पर एक गाना बजाइए 🎵"
            )
        ),
        "ja" to Lang(
            arrayOf("おはようございます", "こんにちは", "こんにちは", "こんばんは"),
            arrayOf(
                "また%sで遊ぼう！🎶",
                "%sが待っています。少し演奏しませんか？🥁",
                "5分だけ、%sで1曲演奏してみよう 🎵"
            )
        ),
        "ko" to Lang(
            arrayOf("안녕하세요, 좋은 아침이에요", "안녕하세요, 좋은 오후예요", "안녕하세요, 좋은 오후예요", "안녕하세요, 좋은 저녁이에요"),
            arrayOf(
                "%s 다시 연주해 볼까요? 🎶",
                "%s에서 기다리고 있어요. 잠깐 연주해요 🥁",
                "5분만 시간 있나요? %s에서 한 곡 연주해 보세요 🎵"
            )
        ),
        "zh" to Lang(
            arrayOf("嗨，早上好", "嗨，中午好", "嗨，下午好", "嗨，晚上好"),
            arrayOf(
                "再来玩一次%s吧！🎶",
                "%s想你了，来演奏一会儿吧 🥁",
                "有5分钟吗？用%s演奏一首歌吧 🎵"
            )
        ),
        "th" to Lang(
            arrayOf("สวัสดีตอนเช้า", "สวัสดีตอนบ่าย", "สวัสดีตอนเย็น", "สวัสดีตอนค่ำ"),
            arrayOf(
                "มาเล่น %s กันอีกครั้งนะ! 🎶",
                "%s คิดถึงคุณแล้ว มาเล่นสักหน่อยไหม 🥁",
                "มีเวลา 5 นาทีไหม? มาเล่นเพลงด้วย %s กัน 🎵"
            )
        ),
        "vi" to Lang(
            arrayOf("Chào buổi sáng", "Chào buổi trưa", "Chào buổi chiều", "Chào buổi tối"),
            arrayOf(
                "Cùng chơi %s lại nào! 🎶",
                "%s đang nhớ bạn đó. Chơi một chút nhé? 🥁",
                "Có 5 phút không? Chơi một bài với %s nhé 🎵"
            )
        ),
        "fil" to Lang(
            arrayOf("Hi, magandang umaga", "Hi, magandang tanghali", "Hi, magandang hapon", "Hi, magandang gabi"),
            arrayOf(
                "Tara, tugtog ulit tayo sa %s! 🎶",
                "Miss ka na ng %s. Tugtog tayo saglit? 🥁",
                "May 5 minuto ka? Tumugtog ng isang kanta sa %s 🎵"
            )
        )
    )

    /** 0 = pagi (04–10), 1 = siang (11–14), 2 = sore (15–17), 3 = malam (18–03). */
    private fun slot(hour: Int) = when (hour) {
        in 4..10 -> 0
        in 11..14 -> 1
        in 15..17 -> 2
        else -> 3
    }

    private fun langFor(locale: Locale): Lang {
        val code = locale.language.lowercase(Locale.ROOT)
        return LANGS[code] ?: if (code == "tl") LANGS.getValue("fil") else EN
    }

    /** @return pasangan (judul, isi) siap tampil. */
    fun build(appName: String, hour: Int, locale: Locale = Locale.getDefault()): Pair<String, String> {
        val lang = langFor(locale)
        val title = "${lang.greetings[slot(hour)]} 👋"
        val body = String.format(locale, lang.bodies.random(), appName)
        return title to body
    }
}

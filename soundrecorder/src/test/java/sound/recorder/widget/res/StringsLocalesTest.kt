package sound.recorder.widget.res

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Setiap string di values/strings.xml harus ada di semua folder values-* dengan placeholder yang sama.
 * Placeholder beda (mis. %1$d jadi %s) bisa crash saat getString(..., args) di bahasa itu.
 */
class StringsLocalesTest {

    private val resDir = listOf(File("src/main/res"), File("soundrecorder/src/main/res")).first { it.isDirectory }
    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc]""")

    private fun load(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private fun placeholders(text: String): List<String> =
        placeholder.findAll(text.replace("%%", "")).map { it.value }.sorted().toList()

    private val base by lazy { load(File(resDir, "values/strings.xml")) }
    private val locales by lazy {
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
            .sortedBy { it.name }
    }

    @Test
    fun allNineteenLanguagesExist() {
        // values/ (bahasa Inggris default) + 18 folder values-* = 19 bahasa
        assertTrue("hanya ${locales.size} folder bahasa: ${locales.map { it.name }}", locales.size >= 18)
    }

    @Test
    fun everyLocaleHasEveryString() {
        val problems = locales.mapNotNull { dir ->
            val missing = base.keys - load(File(dir, "strings.xml")).keys
            if (missing.isEmpty()) null else "${dir.name} kurang ${missing.size}: ${missing.sorted().take(15)}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun placeholdersMatchDefault() {
        val problems = locales.flatMap { dir ->
            val strings = load(File(dir, "strings.xml"))
            base.mapNotNull { (key, text) ->
                val translated = strings[key] ?: return@mapNotNull null
                val want = placeholders(text)
                val got = placeholders(translated)
                if (want == got) null else "${dir.name}/$key: $want ≠ $got"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

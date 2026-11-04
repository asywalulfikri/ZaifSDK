package sound.recorder.widget.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sound.recorder.widget.testutil.FakeContext
import sound.recorder.widget.util.Constant

class ZaifSDKStorageTest {

    private fun config(name: String = "Gendang") = ZaifSDKConfig(
        appName = name, versionCode = 45, versionName = "2.3.1", applicationId = "com.zaif.gendang",
        developerName = "Zaif", showNote = true, showChangeColor = false, backgroundWidgetColor = "#000000",
        showListSong = true, showVolume = true, showTooltip = false, fcmKey = "",
        isCoin = true, isVideo = true
    )

    @Test
    fun nothingSavedReturnsNull() {
        assertNull(ZaifSDKStorage.load(FakeContext()))
    }

    @Test
    fun saveAndLoadKeepsEveryField() {
        val ctx = FakeContext()
        ZaifSDKStorage.save(ctx, config())
        assertEquals(config(), ZaifSDKStorage.load(ctx))
    }

    @Test
    fun saveOverwritesPreviousConfig() {
        val ctx = FakeContext()
        ZaifSDKStorage.save(ctx, config("Lama"))
        ZaifSDKStorage.save(ctx, config("Baru"))
        assertEquals("Baru", ZaifSDKStorage.load(ctx)?.appName)
    }

    @Test
    fun clearRemovesConfig() {
        val ctx = FakeContext()
        ZaifSDKStorage.save(ctx, config())
        ZaifSDKStorage.clear(ctx)
        assertNull(ZaifSDKStorage.load(ctx))
    }

    @Test
    fun corruptJsonReturnsNullInsteadOfCrash() {
        val ctx = FakeContext()
        ctx.getSharedPreferences(Constant.KeyShared.shareKey, 0).edit()
            .putString(Constant.KeyShared.zaifSDKBuilder, "{bukan json")
            .apply()
        assertNull(ZaifSDKStorage.load(ctx))
    }

    @Test
    fun builderSavesWhatHostSet() {
        val ctx = FakeContext()
        val built = ZaifSDKBuilder.builder(ctx)
            .setAppName("Darbuka")
            .setVersionName("1.9.0")
            .setVersionCode(19)
            .isCoin(true)
            .isVideo(true)
            .build()
        assertEquals("Darbuka", built.appName)
        assertEquals("1.9.0", built.versionName)
        assertEquals(19, built.versionCode)
        assertTrue(built.isCoin && built.isVideo)
        assertEquals(built, ZaifSDKStorage.load(ctx))
    }

    @Test
    fun appsHavePremiumPurchaseByDefault() {
        val built = ZaifSDKBuilder.builder(FakeContext()).setAppName("Rebana").build()
        assertTrue(built.hasPremiumPurchase)
        assertFalse(built.noPremiumPurchase)
    }

    @Test
    fun noPurchaseAppIsSavedAndLoaded() {
        val ctx = FakeContext()
        val built = ZaifSDKBuilder.builder(ctx).setAppName("Darbuka").hasPremiumPurchase(false).build()
        assertFalse(built.hasPremiumPurchase)
        assertEquals(true, ZaifSDKStorage.load(ctx)?.noPremiumPurchase)
    }

    @Test
    fun oldSavedConfigWithoutFlagCountsAsHavingPurchase() {
        // Config tersimpan dari SDK lama belum punya field ini: harus dianggap app dengan pembelian
        val ctx = FakeContext()
        ctx.getSharedPreferences(Constant.KeyShared.shareKey, 0).edit()
            .putString(Constant.KeyShared.zaifSDKBuilder, """{"appName":"Lama","versionCode":1,"versionName":"1","applicationId":"a","developerName":"d","showNote":false,"showChangeColor":false,"backgroundWidgetColor":"#fff","showListSong":true,"showVolume":true,"showTooltip":false,"fcmKey":"","isCoin":true}""")
            .apply()
        val loaded = ZaifSDKStorage.load(ctx)
        assertEquals("Lama", loaded?.appName)
        assertEquals(true, loaded?.hasPremiumPurchase)
    }
}

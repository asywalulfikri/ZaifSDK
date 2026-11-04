package sound.recorder.widget.util

import android.content.Context
import android.util.Log
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Event koin dan pembelian ke Firebase Analytics. Isinya hanya jenis kejadian dan jumlah koin,
 * tanpa data pribadi. Host tanpa google-services tidak mengirim apa-apa.
 */
object ZaifAnalytics {

    private const val TAG = "ZaifAnalytics"

    const val SOURCE_MISSION = "mission"
    const val SOURCE_CHECK_IN = "check_in"
    const val SOURCE_AD = "ad"
    const val SOURCE_DAILY_BONUS = "daily_bonus"
    /** Koin dikembalikan karena video yang sudah dibayar batal / gagal. */
    const val SOURCE_REFUND = "refund"
    const val SOURCE_OTHER = "other"

    const val PURPOSE_PREMIUM_PASS = "premium_pass"
    const val PURPOSE_EXPORT = "export"
    const val PURPOSE_NOTE_UNLOCK = "note_unlock"
    const val PURPOSE_OTHER = "other"

    private const val EVENT_COIN_EARNED = "coin_earned"
    private const val EVENT_COIN_SPENT = "coin_spent"
    private const val EVENT_COIN_AD_WATCHED = "coin_ad_watched"
    private const val EVENT_PASS_REDEEMED = "premium_pass_redeemed"
    private const val EVENT_REMOVE_ADS = "remove_ads_purchased"

    private const val PARAM_SOURCE = "source"
    private const val PARAM_PURPOSE = "purpose"
    private const val PARAM_AMOUNT = "amount"
    private const val PARAM_COST = "cost"

    const val PLACE_REMOVE_ADS = "remove_ads"
    const val PLACE_VIDEO = "video"

    const val SOURCE_RECORDING = "recording"
    const val SOURCE_TUTORIAL = "tutorial"

    const val FORMAT_VIDEO = "video"

    /** `reason` export_failed saat user membatalkan export. */
    const val REASON_CANCELLED = "cancelled"

    const val UNLOCK_FREE = "free"
    const val UNLOCK_AD = "ad"
    const val UNLOCK_COIN = "coin"

    const val AD_EARNED = "earned"
    const val AD_CLOSED_EARLY = "closed_early"
    const val AD_SHOW_FAILED_GRANTED = "show_failed_granted"
    const val AD_LOAD_FAILED_GRANTED = "load_failed_granted"
    const val AD_TIMEOUT_GRANTED = "timeout_granted"
    const val AD_NOT_STARTED_GRANTED = "not_started_granted"
    const val AD_NO_INTERNET = "no_internet"
    const val AD_CANCELLED = "cancelled"
    const val AD_LEFT_APP = "left_app"

    private const val EVENT_PREMIUM_SHOWN = "premium_offer_shown"
    private const val EVENT_PREMIUM_CLICKED = "premium_offer_clicked"
    private const val EVENT_EXPORT_STARTED = "export_started"
    private const val EVENT_EXPORT_FINISHED = "export_finished"
    private const val EVENT_EXPORT_FAILED = "export_failed"
    private const val EVENT_EXPORT_UNLOCK = "export_unlock"
    private const val EVENT_REWARD_REQUESTED = "reward_ad_requested"
    private const val EVENT_REWARD_RESULT = "reward_ad_result"

    private const val PARAM_PLACE = "place"
    private const val PARAM_FORMAT = "format"
    private const val PARAM_METHOD = "method"
    private const val PARAM_RESULT = "result"
    private const val PARAM_REASON = "reason"

    fun premiumOfferShown(context: Context, place: String) {
        log(context, EVENT_PREMIUM_SHOWN) { bundleOf(PARAM_PLACE to place) }
    }

    fun premiumOfferClicked(context: Context, place: String) {
        log(context, EVENT_PREMIUM_CLICKED) { bundleOf(PARAM_PLACE to place) }
    }

    fun exportStarted(context: Context, format: String, source: String) {
        log(context, EVENT_EXPORT_STARTED) { bundleOf(PARAM_FORMAT to format, PARAM_SOURCE to source) }
    }

    fun exportFinished(context: Context, format: String, source: String) {
        log(context, EVENT_EXPORT_FINISHED) { bundleOf(PARAM_FORMAT to format, PARAM_SOURCE to source) }
    }

    /** @param reason nama kelas error saja, bukan pesan (pesan bisa memuat nama file user). */
    fun exportFailed(context: Context, format: String, source: String, reason: String) {
        log(context, EVENT_EXPORT_FAILED) {
            bundleOf(PARAM_FORMAT to format, PARAM_SOURCE to source, PARAM_REASON to reason.take(MAX_VALUE_LENGTH))
        }
    }

    fun exportUnlock(context: Context, method: String) {
        log(context, EVENT_EXPORT_UNLOCK) { bundleOf(PARAM_METHOD to method) }
    }

    fun rewardAdRequested(context: Context) {
        log(context, EVENT_REWARD_REQUESTED) { null }
    }

    fun rewardAdResult(context: Context, result: String) {
        log(context, EVENT_REWARD_RESULT) { bundleOf(PARAM_RESULT to result) }
    }

    /** Batas panjang nilai parameter Firebase Analytics. */
    private const val MAX_VALUE_LENGTH = 100

    fun coinEarned(context: Context, source: String, amount: Int) {
        log(context, EVENT_COIN_EARNED) {
            bundleOf(PARAM_SOURCE to source, PARAM_AMOUNT to amount.toLong())
        }
    }

    fun coinSpent(context: Context, purpose: String, amount: Int) {
        log(context, EVENT_COIN_SPENT) {
            bundleOf(PARAM_PURPOSE to purpose, PARAM_AMOUNT to amount.toLong())
        }
    }

    fun coinAdWatched(context: Context) {
        log(context, EVENT_COIN_AD_WATCHED) { null }
    }

    fun premiumPassRedeemed(context: Context, cost: Int) {
        log(context, EVENT_PASS_REDEEMED) { bundleOf(PARAM_COST to cost.toLong()) }
    }

    fun removeAdsPurchased(context: Context) {
        log(context, EVENT_REMOVE_ADS) { null }
    }

    /** Pencatatan tidak boleh mengganggu fitur koin atau pembelian, jadi kegagalannya hanya di-log. */
    private inline fun log(context: Context, event: String, params: () -> android.os.Bundle?) {
        try {
            FirebaseAnalytics.getInstance(context.applicationContext).logEvent(event, params())
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mencatat $event: ${e.message}")
        }
    }
}

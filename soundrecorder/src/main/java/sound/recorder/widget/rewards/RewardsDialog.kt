package sound.recorder.widget.rewards

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import sound.recorder.widget.R
import sound.recorder.widget.base.BaseActivityWidget
import sound.recorder.widget.recording.RecordingDialogPalette
import sound.recorder.widget.recording.RecordingDialogStyle
import sound.recorder.widget.recording.RecordingDialogViews.applyWindow
import sound.recorder.widget.recording.RecordingDialogViews.boxButton
import sound.recorder.widget.recording.RecordingDialogViews.rounded
import sound.recorder.widget.recording.RecordingDialogViews.sdp
import sound.recorder.widget.recording.RecordingDialogViews.textButton
import sound.recorder.widget.recording.RecordingDialogViews.vSpace
import sound.recorder.widget.recording.export.RecordingExporter
import sound.recorder.widget.util.CoinManager
import com.intuit.sdp.R as SdpR

/**
 * Panel Hadiah Harian: check-in beruntun, misi harian [DailyRewards.missions], koin dari iklan,
 * dan tukar koin dengan [PremiumPass]. Warna mengikuti [RecordingDialogStyle.default].
 *
 * Iklan rewarded memakai [BaseActivityWidget.showRewardedAd] bila [activity] turunan BaseActivityWidget.
 */
object RewardsDialog {

    /**
     * @param onChanged dipanggil setelah koin / status berubah (mis. refresh tombol Misi di host,
     *        atau sembunyikan banner setelah Premium Pass aktif).
     */
    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity, style: RecordingDialogStyle = RecordingDialogStyle.default, onChanged: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        val c = RecordingDialogPalette(activity, style)
        val dialog = AlertDialog.Builder(activity).create()
        val pad = activity.sdp(SdpR.dimen._12sdp)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(activity, c.surface, SdpR.dimen._12sdp)
            setPadding(pad, pad, pad, pad)
        }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        var adShowing = false

        lateinit var render: () -> Unit
        fun changed(earned: Int = 0) {
            if (earned > 0) Toast.makeText(activity, CoinManager.withIcon(activity.getString(R.string.daily_rewards_coins_earned, earned)), Toast.LENGTH_SHORT).show()
            render()
            onChanged?.invoke()
        }

        val actions = Actions(
            onCheckIn = { changed(DailyRewards.checkIn(activity)) },
            onClaim = { changed(DailyRewards.claim(activity, it)) },
            onRedeem = {
                if (PremiumPass.redeem(activity)) {
                    Toast.makeText(activity, activity.getString(R.string.daily_rewards_premium_redeemed, PremiumPass.durationDays), Toast.LENGTH_LONG).show()
                    changed()
                }
            },
            onWatchAd = watch@{
                val host = activity as? BaseActivityWidget ?: return@watch
                if (adShowing || DailyRewards.adsLeftToday(activity) <= 0) return@watch
                adShowing = true
                val app = activity.applicationContext
                // false: pengguna premium tetap menonton (opt-in) agar koin tidak gratis
                host.showRewardedAd(false) {
                    adShowing = false
                    val earned = DailyRewards.rewardAd(app)
                    if (dialog.isShowing) changed(earned)
                }
                // SDK tidak memanggil callback bila offline / iklan belum siap; buka lagi kuncinya
                root.postDelayed({ adShowing = false }, 3_000L)
            },
            canWatchAd = activity is BaseActivityWidget
        )

        render = {
            content.removeAllViews()
            buildContent(activity, c, content, actions)
        }

        root.addView(header(activity, c) { dialog.dismiss() })
        root.addView(vSpace(activity, SdpR.dimen._8sdp))
        root.addView(ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }, LinearLayout.LayoutParams(-1, -2))
        render()

        dialog.setView(root)
        dialog.show()
        applyWindow(dialog, activity, 0.94, 0.80)
    }

    private class Actions(
        val onCheckIn: () -> Unit,
        val onClaim: (RewardMission) -> Unit,
        val onRedeem: () -> Unit,
        val onWatchAd: () -> Unit,
        val canWatchAd: Boolean,
    )

    private fun header(activity: Activity, c: RecordingDialogPalette, onClose: () -> Unit) = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(activity).apply {
            text = "🏆 " + activity.getString(R.string.daily_rewards_title)
            setTextColor(c.text)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(textButton(activity, c, "✕", c.textSecondary).apply { setOnClickListener { onClose() } })
    }

    private fun buildContent(activity: Activity, c: RecordingDialogPalette, content: LinearLayout, a: Actions) {
        val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val left = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        // Saldo koin
        left.addView(TextView(activity).apply {
            text = "${CoinManager.icon} " + activity.getString(R.string.your_coin) + ": " + CoinManager.getBalance(activity)
            setTextColor(c.text)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        left.addView(vSpace(activity, SdpR.dimen._8sdp))
        streakSection(activity, c, left, a)
        if (DailyRewards.missions.isNotEmpty()) {
            left.addView(vSpace(activity, SdpR.dimen._10sdp))
            left.addView(sectionTitle(activity, c, activity.getString(R.string.daily_rewards_missions)))
            DailyRewards.missions.forEach { left.addView(missionRow(activity, c, it, a)) }
        }

        right.addView(premiumCard(activity, c, a))
        right.addView(vSpace(activity, SdpR.dimen._8sdp))
        right.addView(infoCard(activity, c))
        val adsLeft = DailyRewards.adsLeftToday(activity)
        if (a.canWatchAd && adsLeft > 0) {
            right.addView(vSpace(activity, SdpR.dimen._8sdp))
            right.addView(
                boxButton(activity, c, "▶ " + activity.getString(R.string.daily_rewards_watch_ad, DailyRewards.adReward, adsLeft), primary = false) { a.onWatchAd() },
                LinearLayout.LayoutParams(-1, activity.sdp(SdpR.dimen._32sdp))
            )
        }

        if (landscape) {
            content.addView(LinearLayout(activity).apply {
                addView(left, LinearLayout.LayoutParams(0, -2, 1.4f))
                addView(View(activity), LinearLayout.LayoutParams(activity.sdp(SdpR.dimen._10sdp), 1))
                addView(right, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            content.addView(left, LinearLayout.LayoutParams(-1, -2))
            content.addView(vSpace(activity, SdpR.dimen._10sdp))
            content.addView(right, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun sectionTitle(activity: Activity, c: RecordingDialogPalette, label: String) = TextView(activity).apply {
        text = label.uppercase()
        setTextColor(c.accent)
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.1f
        setPadding(0, 0, 0, activity.sdp(SdpR.dimen._4sdp))
    }

    private fun streakSection(activity: Activity, c: RecordingDialogPalette, parent: LinearLayout, a: Actions) {
        parent.addView(sectionTitle(activity, c, activity.getString(R.string.daily_rewards_streak)))
        val canCheckIn = DailyRewards.canCheckIn(activity)
        val streak = DailyRewards.streak(activity)
        val streakDay = if (canCheckIn) streak + 1 else streak.coerceAtLeast(1)
        // Tampilkan minggu berjalan: hari 1–7, lalu 8–14, dst.
        val weekStart = ((streakDay - 1) / 7) * 7 + 1
        parent.addView(LinearLayout(activity).apply {
            for (day in weekStart until weekStart + 7) {
                val done = day <= streak
                val isNext = canCheckIn && day == streakDay
                if (day > weekStart) addView(View(activity), LinearLayout.LayoutParams(activity.sdp(SdpR.dimen._4sdp), 1))
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    val vp = activity.sdp(SdpR.dimen._4sdp)
                    setPadding(0, vp, 0, vp)
                    background = rounded(
                        activity,
                        when {
                            done -> c.accent
                            isNext -> c.accentSoft
                            else -> c.surfaceAlt
                        },
                        SdpR.dimen._6sdp,
                        if (isNext) c.accent else null
                    )
                    val color = if (done) c.onAccent else c.text
                    addView(TextView(activity).apply {
                        text = activity.getString(R.string.daily_rewards_day, day)
                        setTextColor(ColorUtils.setAlphaComponent(color, 0xCC))
                        textSize = 9f
                        maxLines = 1
                        gravity = Gravity.CENTER
                    })
                    addView(TextView(activity).apply {
                        text = if (done) "✓" else "+${DailyRewards.rewardForDay(day)}"
                        setTextColor(color)
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }, LinearLayout.LayoutParams(-1, -2))
        parent.addView(vSpace(activity, SdpR.dimen._6sdp))
        if (canCheckIn) {
            parent.addView(
                boxButton(activity, c, activity.getString(R.string.daily_rewards_check_in, DailyRewards.rewardForDay(streakDay)), primary = true) { a.onCheckIn() },
                LinearLayout.LayoutParams(-1, activity.sdp(SdpR.dimen._30sdp))
            )
        } else {
            parent.addView(TextView(activity).apply {
                text = activity.getString(R.string.daily_rewards_checked_in)
                setTextColor(c.textSecondary)
                textSize = 11f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun missionRow(activity: Activity, c: RecordingDialogPalette, m: RewardMission, a: Actions): View {
        val progress = DailyRewards.progress(activity, m)
        val claimed = DailyRewards.isClaimed(activity, m)
        val canClaim = DailyRewards.canClaim(activity, m)
        val shownTarget = if (m.inMinutes) m.target / 60 else m.target
        val shownProgress = if (m.inMinutes) progress / 60 else progress
        val pad = activity.sdp(SdpR.dimen._8sdp)
        return LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(activity, c.surfaceAlt, SdpR.dimen._8sdp)
            setPadding(pad, pad, pad, pad)
            alpha = if (claimed) 0.55f else 1f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.sdp(SdpR.dimen._4sdp) }

            addView(TextView(activity).apply {
                text = m.emoji
                textSize = 18f
            })
            addView(View(activity), LinearLayout.LayoutParams(pad, 1))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    text = activity.getString(m.titleRes, shownTarget)
                    setTextColor(c.text)
                    textSize = 12f
                    maxLines = 2
                })
                addView(LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = m.target
                        this.progress = progress
                        progressTintList = ColorStateList.valueOf(if (progress >= m.target) c.success else c.accent)
                        progressBackgroundTintList = ColorStateList.valueOf(c.outline)
                    }, LinearLayout.LayoutParams(0, activity.sdp(SdpR.dimen._6sdp), 1f))
                    addView(TextView(activity).apply {
                        text = "  $shownProgress/$shownTarget"
                        setTextColor(c.textSecondary)
                        textSize = 10f
                    })
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(View(activity), LinearLayout.LayoutParams(pad, 1))
            if (claimed) {
                addView(TextView(activity).apply {
                    text = "✓ " + activity.getString(R.string.daily_rewards_done)
                    setTextColor(c.success)
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                })
            } else {
                addView(
                    boxButton(activity, c, activity.getString(R.string.daily_rewards_claim, m.reward), primary = canClaim) {
                        if (canClaim) a.onClaim(m)
                    }.apply {
                        alpha = if (canClaim) 1f else 0.5f
                        val ph = activity.sdp(SdpR.dimen._8sdp)
                        setPadding(ph, 0, ph, 0)
                        textSize = 11f
                    },
                    LinearLayout.LayoutParams(-2, activity.sdp(SdpR.dimen._26sdp))
                )
            }
        }
    }

    private fun premiumCard(activity: Activity, c: RecordingDialogPalette, a: Actions): View {
        val balance = CoinManager.getBalance(activity)
        val cost = PremiumPass.cost(activity)
        val remaining = PremiumPass.remainingMs(activity)
        val pad = activity.sdp(SdpR.dimen._10sdp)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(activity, c.accentSoft, SdpR.dimen._10sdp, c.accent)
            setPadding(pad, pad, pad, pad)
        }
        card.addView(TextView(activity).apply {
            text = "👑 " + activity.getString(R.string.daily_rewards_premium_title)
            setTextColor(c.text)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(activity).apply {
            text = activity.getString(R.string.daily_rewards_premium_desc, PremiumPass.durationDays)
            setTextColor(c.textSecondary)
            textSize = 11f
            gravity = Gravity.CENTER
        })
        if (PremiumPass.isFirstRedeem(activity)) {
            card.addView(vSpace(activity, SdpR.dimen._4sdp))
            card.addView(TextView(activity).apply {
                text = activity.getString(R.string.daily_rewards_premium_intro)
                setTextColor(c.onAccent)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                val ph = activity.sdp(SdpR.dimen._6sdp)
                setPadding(ph, 0, ph, 0)
                background = rounded(activity, c.accent, SdpR.dimen._6sdp)
            })
        }
        if (remaining > 0) {
            card.addView(vSpace(activity, SdpR.dimen._4sdp))
            card.addView(TextView(activity).apply {
                text = activity.getString(R.string.daily_rewards_premium_active, DailyRewards.formatRemaining(activity, remaining))
                setTextColor(c.success)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            })
        }
        card.addView(vSpace(activity, SdpR.dimen._8sdp))

        // Tombol tukar → konfirmasi di tempat (ganti isi slot), tanpa dialog bertumpuk
        val slot = FrameLayout(activity)
        fun showRedeemButton() {
            slot.removeAllViews()
            if (balance >= cost) {
                val label = if (remaining > 0) activity.getString(R.string.daily_rewards_premium_extend, PremiumPass.durationDays, cost)
                else activity.getString(R.string.daily_rewards_premium_redeem, cost)
                slot.addView(boxButton(activity, c, label, primary = true) {
                    slot.removeAllViews()
                    slot.addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        addView(TextView(activity).apply {
                            text = activity.getString(R.string.daily_rewards_premium_confirm, cost)
                            setTextColor(c.text)
                            textSize = 12f
                        })
                        addView(LinearLayout(activity).apply {
                            gravity = Gravity.CENTER
                            addView(textButton(activity, c, activity.getString(R.string.cancel), c.textSecondary).apply {
                                setOnClickListener { showRedeemButton() }
                            })
                            addView(textButton(activity, c, activity.getString(R.string.daily_rewards_yes)).apply {
                                setOnClickListener { a.onRedeem() }
                            })
                        })
                    }, FrameLayout.LayoutParams(-1, -2))
                }, FrameLayout.LayoutParams(-1, activity.sdp(SdpR.dimen._30sdp)))
            } else {
                slot.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = cost
                        progress = balance
                        progressTintList = ColorStateList.valueOf(c.accent)
                        progressBackgroundTintList = ColorStateList.valueOf(c.outline)
                    }, LinearLayout.LayoutParams(-1, activity.sdp(SdpR.dimen._6sdp)))
                    addView(TextView(activity).apply {
                        text = activity.getString(R.string.daily_rewards_premium_need, cost - balance)
                        setTextColor(c.textSecondary)
                        textSize = 11f
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-1, -2))
                }, FrameLayout.LayoutParams(-1, -2))
            }
        }
        showRedeemButton()
        card.addView(slot, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    /** Info: koin juga bisa dipakai untuk export tanpa iklan. */
    private fun infoCard(activity: Activity, c: RecordingDialogPalette) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val pad = activity.sdp(SdpR.dimen._8sdp)
        setPadding(pad, pad, pad, pad)
        background = rounded(activity, Color.TRANSPARENT, SdpR.dimen._8sdp, c.outline)
        addView(TextView(activity).apply {
            text = "${CoinManager.icon} " + activity.getString(R.string.daily_rewards_export_title)
            setTextColor(c.text)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(TextView(activity).apply {
            text = activity.getString(R.string.daily_rewards_export_desc, RecordingExporter.exportCoinCost)
            setTextColor(c.textSecondary)
            textSize = 11f
        })
    }
}

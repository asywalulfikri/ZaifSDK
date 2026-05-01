package sound.recorder.widget.payment

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.*
import java.lang.ref.WeakReference

class BillingManager(
    activity: Activity,
    private var onPurchaseSuccess: (() -> Unit)? // 🔥 FIX: Ubah jadi 'var' dan nullable
) {

    private val TAG = "BillingManager"
    private val PRODUCT_ID = "remove_ads_permanent"

    // 🔥 FIX: pakai WeakReference
    private val activityRef = WeakReference(activity)

    // 🔥 Handler untuk retry
    private val handler = Handler(Looper.getMainLooper())

    // 🔥 Limit retry biar ga infinite loop
    private var reconnectAttempt = 0
    private val MAX_RETRY = 3

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(activity.applicationContext)
            .setListener { billingResult, purchases ->
                Log.d(TAG, "OnPurchasesUpdated: ${billingResult.responseCode}")

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    purchases.forEach { handlePurchase(it) }
                }
            }
            .enablePendingPurchases()
            .build()
    }

    init {
        startConnection()
    }

    private fun startConnection() {
        if (billingClient.isReady) return

        billingClient.startConnection(object : BillingClientStateListener {

            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    reconnectAttempt = 0
                    checkHistory()
                } else {
                    Log.e(TAG, "Setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                if (reconnectAttempt < MAX_RETRY) {
                    reconnectAttempt++
                    Log.w(TAG, "Reconnect attempt $reconnectAttempt")

                    handler.postDelayed({
                        startConnection()
                    }, 2000)
                } else {
                    Log.e(TAG, "Max reconnect reached")
                }
            }
        })
    }

    fun makePurchase() {
        val activity = activityRef.get()
        if (activity == null) {
            Log.e(TAG, "Activity is null, cannot launch billing flow")
            return
        }

        if (!billingClient.isReady) {
            Log.e(TAG, "BillingClient not ready")
            return
        }

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->

            if (result.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {

                val productDetails = productDetailsList[0]

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()

                activityRef.get()?.let {
                    Log.d(TAG, "Launching billing flow")
                    billingClient.launchBillingFlow(it, flowParams)
                }

            } else {
                Log.e(TAG, "Product not found: ${result.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_ID)) return

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {

            if (!purchase.isAcknowledged) {

                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(params) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase acknowledged")
                        onPurchaseSuccess?.invoke() // 🔥 FIX: safe call
                    }
                }

            } else {
                onPurchaseSuccess?.invoke() // 🔥 FIX: safe call
            }
        }
    }

    fun checkHistory() {
        if (!billingClient.isReady) return

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->

            if (result.responseCode == BillingClient.BillingResponseCode.OK) {

                purchases.forEach {
                    if (it.products.contains(PRODUCT_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        Log.d(TAG, "Purchase history found")
                        onPurchaseSuccess?.invoke() // 🔥 FIX: safe call
                    }
                }
            }
        }
    }

    // 🔥 WAJIB dipanggil di onDestroy()
    fun destroy() {
        try {
            // Bersihkan antrian reconnect
            handler.removeCallbacksAndMessages(null)

            // Tutup koneksi Google Play
            if (billingClient.isReady) {
                billingClient.endConnection()
            }

            // Lepas reference activity
            activityRef.clear()

            // 🔥 Putuskan rantai lambda (Memory Leak ultimate fix)
            onPurchaseSuccess = null

            Log.d(TAG, "BillingManager destroyed safely")

        } catch (e: Exception) {
            Log.e(TAG, "Error on destroy: ${e.message}")
        }
    }
}

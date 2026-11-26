package io.github.duwenji.yorisoi.kit.billing

import io.github.duwenji.yorisoi.core.text.localized
import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import io.github.duwenji.yorisoi.kit.llm.PlanPurchase
import io.github.duwenji.yorisoi.kit.llm.PlanPurchases
import io.github.duwenji.yorisoi.kit.llm.RelayClient
import io.github.duwenji.yorisoi.kit.llm.RelayException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** One way to pay for たっぷりプラン: a base plan and its price as Google Play shows it (API-AI-RELAY R-21). */
data class PlusOffer(val basePlanId: String, val price: String, val offerToken: String)

/** Where a purchase stands, for the settings screen. */
sealed interface PlusState {
    data object Idle : PlusState
    data object Checking : PlusState
    data object Active : PlusState
    data class Failed(val message: String) : PlusState
}

/**
 * たっぷりプラン on Google Play's billing library (platform API-AI-RELAY R-15〜R-21): the offers of the
 * subscription `plus`, the purchase flow, and the active purchase, which the relay checks and acknowledges.
 * The app never acknowledges it: only a purchase the relay has checked is kept (R-15).
 */
class PlusPlan(context: Context, private val scope: CoroutineScope) : PlanPurchases {
    // One subscription, `plus`, in each app that sells it (API-AI-RELAY R-21).
    /** Set after the relay is made: the relay needs this class, and this class sends to the relay. */
    var relay: RelayClient? = null

    private val _offers = MutableStateFlow<List<PlusOffer>>(emptyList())
    /** The base plans on sale, monthly first; empty while Play has none (before the product is made, or offline). */
    val offers: StateFlow<List<PlusOffer>> = _offers.asStateFlow()

    private val _state = MutableStateFlow<PlusState>(PlusState.Idle)
    val state: StateFlow<PlusState> = _state.asStateFlow()

    private var details: ProductDetails? = null
    private val connectLock = Mutex()

    private val updates = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().firstOrNull { it.isPlus() }?.let { scope.launch { send(it) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> _state.value = PlusState.Idle
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { active()?.let { p -> sendToRelay(p) } }
            else -> _state.value = PlusState.Failed(
                localized("購入できませんでした", "The purchase didn't go through", "购买未能完成", "구매하지 못했습니다") +
                    " (${result.debugMessage.ifEmpty { result.responseCode.toString() }})",
            )
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(updates)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private suspend fun connected(): Boolean = connectLock.withLock {
        if (client.isReady) return true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    /** Reads the offers from Google Play; quietly leaves them empty when it can't. */
    suspend fun refreshOffers() {
        if (!connected()) return
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()),
        ).build()
        val out = client.queryProductDetails(params)
        if (out.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        val d = out.productDetailsList?.firstOrNull { it.productId == PRODUCT_ID } ?: return
        details = d
        _offers.value = d.subscriptionOfferDetails.orEmpty()
            // The base plan itself, not a promotional offer on it.
            .filter { it.offerId == null }
            .mapNotNull { o ->
                val price = o.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return@mapNotNull null
                PlusOffer(o.basePlanId, price, o.offerToken)
            }
            .sortedBy { BASE_PLANS.indexOf(it.basePlanId).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    /** Opens Google Play's purchase sheet for [offer]. */
    fun buy(activity: Activity, offer: PlusOffer) {
        val d = details ?: return
        _state.value = PlusState.Checking
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(offer.offerToken).build()),
        ).build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = PlusState.Failed(
                localized("購入の画面を開けませんでした", "Could not open the purchase screen", "无法打开购买画面", "구매 화면을 열 수 없었습니다") +
                    " (${result.responseCode})",
            )
        }
    }

    /** The purchase Google Play holds for this account, for the relay (API-AI-RELAY R-18). */
    override suspend fun active(): PlanPurchase? {
        if (!connected()) return null
        val out = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        if (out.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        return out.purchasesList.firstOrNull { it.isPlus() }?.let { PlanPurchase(it.purchaseToken, PRODUCT_ID) }
    }

    private suspend fun send(purchase: Purchase) = sendToRelay(PlanPurchase(purchase.purchaseToken, PRODUCT_ID))

    private suspend fun sendToRelay(purchase: PlanPurchase) {
        val r = relay ?: return
        _state.value = PlusState.Checking
        _state.value = try {
            r.sendPlan(purchase)
            PlusState.Active
        } catch (e: RelayException) {
            Log.w("PlusPlan", "The relay did not take the plan: ${e.code}")
            PlusState.Failed(
                if (e.code == "plan_not_active") {
                    e.message + localized(
                        "。少し待ってから、もう一度開いてください", ". Wait a little and open it again", "。请稍等，然后再打开一次", ". 잠시 기다린 뒤 다시 열어 주세요",
                    )
                } else {
                    localized(
                        "購入は済みました。確認に失敗したので、あとで自動でやり直します",
                        "The purchase is done. Checking it failed, so it is tried again later by itself",
                        "购买已完成。确认失败，稍后会自动重试",
                        "구매는 완료되었습니다. 확인에 실패하여 나중에 자동으로 다시 시도합니다",
                    ) + " (${e.code})"
                },
            )
        }
    }

    private fun Purchase.isPlus() = PRODUCT_ID in products && purchaseState == Purchase.PurchaseState.PURCHASED

    companion object {
        /** The subscription and its base plans (API-AI-RELAY R-21). */
        const val PRODUCT_ID = "plus"
        val BASE_PLANS = listOf("monthly", "yearly")

        /** Google Play's page for managing this app's subscription. */
        fun manageUrl(packageName: String) = "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=$packageName"
    }
}

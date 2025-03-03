package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import rx.Observable
import rx.Producer
import rx.Subscription
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

// Based on https://github.com/gildor/kotlin-coroutines-okhttp
suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (!response.isSuccessful) {
                        continuation.resumeWithException(HttpException(response.code))
                        return
                    }

                    continuation.resume(response) {
                        response.body?.closeQuietly()
                    }
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    // Don't bother with resuming the continuation if it is already cancelled.
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            },
        )

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                // Ignore cancel exception
            }
        }
    }
}

fun Call.asObservable(): Observable<Response> {
    return Observable.unsafeCreate { subscriber ->
        // Since Call is a one-shot type, clone it for each new subscriber.
        val call = clone()

        // Wrap the call in a helper which handles both unsubscription and backpressure.
        val requestArbiter =
            object : AtomicBoolean(), Producer, Subscription {
                override fun request(n: Long) {
                    if (n == 0L || !compareAndSet(false, true)) return

                    try {
                        val response = call.execute()
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onNext(response)
                            subscriber.onCompleted()
                        }
                    } catch (error: Exception) {
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onError(error)
                        }
                    }
                }

                override fun unsubscribe() {
                    // call.cancel()
                }

                override fun isUnsubscribed(): Boolean = call.isCanceled()
            }

        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

fun Call.asObservableSuccess(): Observable<Response> =
    asObservable().doOnNext { response ->
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
    }

fun OkHttpClient.newCallWithProgress(
    request: Request,
    listener: ProgressListener,
): Call {
    val progressClient =
        newBuilder()
            .build()

    return progressClient.newCall(request)
}

class HttpException(
    val code: Int,
) : IllegalStateException("HTTP error $code")

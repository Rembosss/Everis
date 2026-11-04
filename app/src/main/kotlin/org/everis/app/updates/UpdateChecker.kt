package org.everis.app.updates

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.everis.app.BuildConfig
import org.everis.sources.HttpResult
import org.everis.sources.PublicHttpClient
import java.net.URI

sealed interface UpdateResult {
    data object Skipped : UpdateResult
    data object Current : UpdateResult
    data object Failed : UpdateResult
    data class Available(val update: AppUpdate) : UpdateResult
}

/** No credentials, analytics, work requests or notification. Runs only while the app is open. */
class UpdateChecker(context: Context) {
    private val preferences = context.getSharedPreferences("app-update-check", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val client = PublicHttpClient(setOf("api.github.com"), maxBytes = 1024 * 1024,
        connectTimeoutMillis = 10_000, readTimeoutMillis = 15_000)
    // The boolean commit result is required: do not report a persisted throttle on disk failure.
    @android.annotation.SuppressLint("UseKtx")
    suspend fun check(manual: Boolean): UpdateResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!manual && !automaticCheckDue(now, preferences.getLong("lastAttempt", 0))) return@withContext UpdateResult.Skipped
            // Record failures too: an offline launch must not trigger a new request every time.
            if (!preferences.edit().putLong("lastAttempt", now).commit()) return@withContext UpdateResult.Failed
            val repository = BuildConfig.UPDATE_REPOSITORY
            if (!validRepository(repository)) return@withContext UpdateResult.Failed
            val response = client.get(URI("https://api.github.com/repos/$repository/releases?per_page=20"))
            if (response !is HttpResult.Body) return@withContext UpdateResult.Failed
            try {
                val update = findAppUpdate(response.bytes.toString(Charsets.UTF_8), repository, BuildConfig.VERSION_NAME,
                    BuildConfig.DEBUG, "arm64-v8a")
                if (update == null) UpdateResult.Current else UpdateResult.Available(update)
            } catch (_: Exception) { UpdateResult.Failed }
        }
    }
}

package blue.starry.onemorecoffee.core.data.starbucks

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.delay

class StarbucksStoreClient(
    private val httpClient: HttpClient,
) {
    suspend fun fetchAllStores(): List<CloudSearchResponse.Hit> {
        val stores = mutableListOf<CloudSearchResponse.Hit>()
        var start = 0

        while (true) {
            val response = fetchPage(start)
            stores += response.hits.hit

            val nextStart = response.hits.start + response.hits.hit.size
            if (nextStart >= response.hits.found || response.hits.hit.isEmpty()) {
                return stores
            }

            delay(PAGE_DELAY_MILLIS)
            start = nextStart
        }
    }

    private suspend fun fetchPage(start: Int): CloudSearchResponse {
        return httpClient.get(STORE_SEARCH_ENDPOINT) {
            parameter("size", PAGE_SIZE)
            parameter("q.parser", "structured")
            parameter("q", "(and ver:10000 record_type:1)")
            parameter("fq", "(and data_type:'prd')")
            parameter("sort", "zip_code asc,store_id asc")
            parameter("start", start)
            header("Referer", "https://store.starbucks.co.jp/")
            header("User-Agent", USER_AGENT)
        }.body()
    }

    private companion object {
        private const val STORE_SEARCH_ENDPOINT =
            "https://hn8madehag.execute-api.ap-northeast-1.amazonaws.com/prd-2019-08-21/storesearch"
        private const val PAGE_SIZE = 100
        private const val PAGE_DELAY_MILLIS = 1_500L
        private const val USER_AGENT = "OneMoreCoffee/0.1.0 (Android; personal use)"
    }
}

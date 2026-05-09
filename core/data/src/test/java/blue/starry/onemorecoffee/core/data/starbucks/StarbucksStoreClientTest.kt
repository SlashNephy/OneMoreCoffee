package blue.starry.onemorecoffee.core.data.starbucks

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StarbucksStoreClientTest {
    @Test
    fun fetchAllStores_fetchesMultiplePages() = runTest {
        val requestedStarts = mutableListOf<Int>()
        val client = client(
            responses = listOf(
                response(found = 101, start = 0, id = "store-1"),
                response(found = 101, start = 100, id = "store-101"),
            ),
            onStart = { start -> requestedStarts += start },
        )

        val stores = client.fetchAllStores()

        assertThat(stores.map { it.id }).containsExactly("store-1", "store-101").inOrder()
        assertThat(requestedStarts).containsExactly(0, 100).inOrder()
    }

    @Test
    fun fetchAllStores_stopsAfterSingleExactPage() = runTest {
        val requestedStarts = mutableListOf<Int>()
        val client = client(
            responses = listOf(response(found = 100, start = 0, id = "store-1")),
            onStart = { start -> requestedStarts += start },
        )

        val stores = client.fetchAllStores()

        assertThat(stores.map { it.id }).containsExactly("store-1")
        assertThat(requestedStarts).containsExactly(0)
    }

    @Test
    fun fetchAllStores_returnsEmptyWhenFirstPageHasNoHits() = runTest {
        val requestedStarts = mutableListOf<Int>()
        val client = client(
            responses = listOf(emptyResponse(found = 0, start = 0)),
            onStart = { start -> requestedStarts += start },
        )

        val stores = client.fetchAllStores()

        assertThat(stores).isEmpty()
        assertThat(requestedStarts).containsExactly(0)
    }

    @Test
    fun fetchAllStores_sendsRequiredSearchParamsAndHeaders() = runTest {
        val observed = mutableListOf<RequestSnapshot>()
        val client = client(
            responses = listOf(emptyResponse(found = 0, start = 0)),
            onRequest = { snapshot -> observed += snapshot },
        )

        client.fetchAllStores()

        val request = observed.single()
        assertThat(request.parameters["size"]).isEqualTo("100")
        assertThat(request.parameters["q.parser"]).isEqualTo("structured")
        assertThat(request.parameters["q"]).isEqualTo("(and ver:10000 record_type:1)")
        assertThat(request.parameters["fq"]).isEqualTo("(and data_type:'prd')")
        assertThat(request.parameters["sort"]).isEqualTo("zip_code asc,store_id asc")
        assertThat(request.headers[HttpHeaders.Referrer]).isEqualTo("https://store.starbucks.co.jp/")
        assertThat(request.headers[HttpHeaders.UserAgent]).isEqualTo("OneMoreCoffee/0.1.0 (Android; personal use)")
    }

    private fun client(
        responses: List<String>,
        onStart: (Int) -> Unit = {},
        onRequest: (RequestSnapshot) -> Unit = {},
    ): StarbucksStoreClient {
        var index = 0
        val engine = MockEngine { request ->
            val start = request.url.parameters["start"]!!.toInt()
            onStart(start)
            onRequest(
                RequestSnapshot(
                    parameters = request.url.parameters.entries().associate { (key, values) -> key to values.single() },
                    headers = request.headers.entries().associate { (key, values) -> key to values.single() },
                ),
            )

            respond(
                content = responses[index++],
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        return StarbucksStoreClient(
            httpClient = HttpClient(engine),
            pageDelay = {},
        )
    }

    private fun response(found: Int, start: Int, id: String): String {
        return """
            {
              "hits": {
                "found": $found,
                "start": $start,
                "hit": [
                  {
                    "id": "$id",
                    "fields": {
                      "store_id": ["$id"]
                    },
                    "unknown": true
                  }
                ],
                "unknown": true
              },
              "unknown": true
            }
        """.trimIndent()
    }

    private fun emptyResponse(found: Int, start: Int): String {
        return """
            {
              "hits": {
                "found": $found,
                "start": $start,
                "hit": []
              }
            }
        """.trimIndent()
    }

    private data class RequestSnapshot(
        val parameters: Map<String, String>,
        val headers: Map<String, String>,
    )
}

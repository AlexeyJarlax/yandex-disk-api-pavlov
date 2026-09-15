import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

class YandexDiskApiTest {

    private val baseUrl = "https://cloud-api.yandex.net/v1/disk"
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private val token = requireNotNull(System.getenv("YANDEX_DISK_TOKEN")) {
        "Environment variable YANDEX_DISK_TOKEN is not set"
    }

    private val testRoots = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        testRoots.asReversed().forEach { path ->
            request(
                method = "DELETE",
                endpoint = "/resources",
                params = mapOf(
                    "path" to path,
                    "permanently" to "true",
                ),
            )
        }
    }

    @Test
    fun `GET disk info returns disk data`() {
        val response = request(
            method = "GET",
            endpoint = "",
        )

        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.body().contains("\"total_space\""))
        assertTrue(response.body().contains("\"used_space\""))
    }

    @Test
    fun `PUT creates folder`() {
        val root = createTestRoot()
        val folder = "$root/created-by-put"

        val response = request(
            method = "PUT",
            endpoint = "/resources",
            params = mapOf("path" to folder),
        )

        assertEquals(201, response.statusCode(), response.body())

        val check = request(
            method = "GET",
            endpoint = "/resources",
            params = mapOf("path" to folder),
        )

        assertEquals(200, check.statusCode(), check.body())
        assertTrue(Regex("\"type\"\\s*:\\s*\"dir\"").containsMatchIn(check.body()))
    }

    @Test
    fun `POST copies folder`() {
        val root = createTestRoot()
        val source = "$root/source"
        val copy = "$root/copy"

        val createResponse = request(
            method = "PUT",
            endpoint = "/resources",
            params = mapOf("path" to source),
        )
        assertEquals(201, createResponse.statusCode(), createResponse.body())

        val copyResponse = request(
            method = "POST",
            endpoint = "/resources/copy",
            params = mapOf(
                "from" to source,
                "path" to copy,
            ),
        )

        assertEquals(201, copyResponse.statusCode(), copyResponse.body())

        val check = request(
            method = "GET",
            endpoint = "/resources",
            params = mapOf("path" to copy),
        )

        assertEquals(200, check.statusCode(), check.body())
        assertTrue(Regex("\"type\"\\s*:\\s*\"dir\"").containsMatchIn(check.body()))
    }

    @Test
    fun `DELETE removes folder`() {
        val root = createTestRoot()
        val folder = "$root/to-delete"

        val createResponse = request(
            method = "PUT",
            endpoint = "/resources",
            params = mapOf("path" to folder),
        )
        assertEquals(201, createResponse.statusCode(), createResponse.body())

        val deleteResponse = request(
            method = "DELETE",
            endpoint = "/resources",
            params = mapOf(
                "path" to folder,
                "permanently" to "true",
            ),
        )

        assertEquals(204, deleteResponse.statusCode(), deleteResponse.body())

        val check = request(
            method = "GET",
            endpoint = "/resources",
            params = mapOf("path" to folder),
        )

        assertEquals(404, check.statusCode(), check.body())
    }

    private fun createTestRoot(): String {
        val path = "/api-autotest-${UUID.randomUUID().toString().take(8)}"

        val response = request(
            method = "PUT",
            endpoint = "/resources",
            params = mapOf("path" to path),
        )

        assertEquals(201, response.statusCode(), response.body())
        testRoots += path

        return path
    }

    private fun request(
        method: String,
        endpoint: String,
        params: Map<String, String> = emptyMap(),
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(buildUri(endpoint, params))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "OAuth $token")
            .header("Accept", "application/json")
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun buildUri(
        endpoint: String,
        params: Map<String, String>,
    ): URI {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }

        val url = buildString {
            append(baseUrl)
            append(endpoint)
            if (query.isNotEmpty()) {
                append("?")
                append(query)
            }
        }

        return URI.create(url)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
}

package blue.starry.onemorecoffee.core.data.starbucks

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class StoreFieldMapperTest {
    @Test
    fun toEntity_usesWgs84LocationAndMapsStoreFields() {
        val now = Instant.parse("2026-05-09T12:34:56Z")
        val fields = jsonObject(
            """
            {
              "store_id": ["1369"],
              "name": ["銀座松屋通り店"],
              "en_name": ["Ginza Matsuya-dori"],
              "pref_code": ["13"],
              "address_1": ["東京都"],
              "address_2": ["中央区"],
              "address_3": ["銀座3-7-14"],
              "address_4": ["ESKビル"],
              "location": ["35.672090,139.765320"],
              "location_jp": ["43.000000,141.000000"],
              "reserve_flg": ["1"]
            }
            """.trimIndent(),
        )

        val entity = StoreFieldMapper.toEntity(
            fields = fields,
            rawJson = """{"id":"1369"}""",
            now = now,
        )

        assertThat(entity).isNotNull()
        assertThat(entity!!.id).isEqualTo("1369")
        assertThat(entity.name).isEqualTo("銀座松屋通り店")
        assertThat(entity.nameEn).isEqualTo("Ginza Matsuya-dori")
        assertThat(entity.prefCode).isEqualTo("13")
        assertThat(entity.prefecture).isEqualTo("東京都")
        assertThat(entity.fullAddress).isEqualTo("東京都中央区銀座3-7-14ESKビル")
        assertThat(entity.latitude).isEqualTo(35.672090)
        assertThat(entity.longitude).isEqualTo(139.765320)
        assertThat(entity.isReserve).isTrue()
        assertThat(entity.rawJson).isEqualTo("""{"id":"1369"}""")
        assertThat(entity.lastSeenAt).isEqualTo(now)
    }

    @Test
    fun toEntity_returnsNullIfLocationMissing() {
        val fields = jsonObject(
            """
            {
              "store_id": ["1369"],
              "name": ["銀座松屋通り店"]
            }
            """.trimIndent(),
        )

        val entity = StoreFieldMapper.toEntity(fields, rawJson = "{}")

        assertThat(entity).isNull()
    }

    @Test
    fun toEntity_returnsNullIfLocationInvalid() {
        val fields = jsonObject(
            """
            {
              "store_id": ["1369"],
              "name": ["銀座松屋通り店"],
              "location": ["not-a-location"]
            }
            """.trimIndent(),
        )

        val entity = StoreFieldMapper.toEntity(fields, rawJson = "{}")

        assertThat(entity).isNull()
    }

    @Test
    fun toEntity_handlesPrimitiveStringFields() {
        val now = Instant.parse("2026-05-09T12:34:56Z")
        val fields = jsonObject(
            """
            {
              "store_id": "2048",
              "name": "Primitive Store",
              "pref_code": "27",
              "address_1": "大阪府",
              "location": "34.702485,135.495951",
              "reserve_flg": "0"
            }
            """.trimIndent(),
        )

        val entity = StoreFieldMapper.toEntity(fields, rawJson = "{}", now = now)

        assertThat(entity).isNotNull()
        assertThat(entity!!.id).isEqualTo("2048")
        assertThat(entity.name).isEqualTo("Primitive Store")
        assertThat(entity.prefCode).isEqualTo("27")
        assertThat(entity.prefecture).isEqualTo("大阪府")
        assertThat(entity.latitude).isEqualTo(34.702485)
        assertThat(entity.longitude).isEqualTo(135.495951)
        assertThat(entity.isReserve).isFalse()
    }

    private fun jsonObject(value: String): JsonObject {
        return Json.parseToJsonElement(value) as JsonObject
    }
}

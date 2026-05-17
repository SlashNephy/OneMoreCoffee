package blue.starry.onemorecoffee.core.ui

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class StoreBusinessHoursTextTest {
    @Test
    fun storeBusinessHoursText_returnsTodayHoursWhenStoreIsOpen() {
        val status = storeBusinessHoursText(
            rawJson = rawJson(
                monThu = "07:00～22:00",
                fri = "07:00～22:30",
                sat = "08:00～21:00",
                sun = "10:00～20:00",
            ),
            now = tokyo("2026-05-18T12:00:00+09:00"),
        )

        assertThat(status).isEqualTo(StoreBusinessHoursText.Open("7:00 ~ 22:00"))
    }

    @Test
    fun storeBusinessHoursText_returnsClosedAndNextOpenTimeBeforeOpening() {
        val status = storeBusinessHoursText(
            rawJson = rawJson(
                monThu = "08:00～22:00",
                fri = "08:00～22:30",
                sat = "09:00～21:00",
                sun = "10:00～20:00",
            ),
            now = tokyo("2026-05-18T07:30:00+09:00"),
        )

        assertThat(status).isEqualTo(StoreBusinessHoursText.Closed("営業開始: 8:00 (月)"))
    }

    @Test
    fun storeBusinessHoursText_returnsClosedAndNextOpenDayAfterClosing() {
        val status = storeBusinessHoursText(
            rawJson = rawJson(
                monThu = "08:00～22:00",
                fri = "08:00～22:30",
                sat = "09:00～21:00",
                sun = "10:00～20:00",
            ),
            now = tokyo("2026-05-18T23:00:00+09:00"),
        )

        assertThat(status).isEqualTo(StoreBusinessHoursText.Closed("営業開始: 8:00 (火)"))
    }

    @Test
    fun storeBusinessHoursText_fallsBackToOpenCloseFields() {
        val status = storeBusinessHoursText(
            rawJson = """
                {
                  "mon_open": "07:30",
                  "mon_close": "21:00"
                }
            """.trimIndent(),
            now = tokyo("2026-05-18T08:00:00+09:00"),
        )

        assertThat(status).isEqualTo(StoreBusinessHoursText.Open("7:30 ~ 21:00"))
    }

    @Test
    fun storeBusinessHoursText_returnsUnknownWhenRawJsonHasNoHours() {
        val status = storeBusinessHoursText(
            rawJson = "{}",
            now = tokyo("2026-05-18T08:00:00+09:00"),
        )

        assertThat(status).isEqualTo(StoreBusinessHoursText.Unknown)
    }

    private fun rawJson(
        monThu: String,
        fri: String,
        sat: String,
        sun: String,
    ): String {
        return """
            {
              "business_day_mon_thu": "$monThu",
              "business_day_fri": "$fri",
              "business_day_sat": "$sat",
              "business_day_sun": "$sun"
            }
        """.trimIndent()
    }

    private fun tokyo(value: String): ZonedDateTime {
        return ZonedDateTime.parse(value).withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
    }
}

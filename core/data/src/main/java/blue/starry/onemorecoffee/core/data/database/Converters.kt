package blue.starry.onemorecoffee.core.data.database

import androidx.room.TypeConverter
import blue.starry.onemorecoffee.core.domain.model.VisitSource
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun instantToString(value: Instant?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun stringToInstant(value: String?): Instant? {
        return value?.let(Instant::parse)
    }

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? {
        return value?.let(LocalDate::parse)
    }

    @TypeConverter
    fun visitSourceToString(value: VisitSource?): String? {
        return value?.name
    }

    @TypeConverter
    fun stringToVisitSource(value: String?): VisitSource? {
        return value?.let(VisitSource::valueOf)
    }
}

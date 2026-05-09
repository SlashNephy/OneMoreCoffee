package blue.starry.onemorecoffee.core.domain.usecase

import blue.starry.onemorecoffee.core.domain.model.ProgressStats
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveProgressStatsUseCase @Inject constructor(
    private val storeRepository: StoreRepository,
) {
    operator fun invoke(): Flow<ProgressStats> {
        return storeRepository.observeStoreSummaries().map(ProgressStats::from)
    }
}

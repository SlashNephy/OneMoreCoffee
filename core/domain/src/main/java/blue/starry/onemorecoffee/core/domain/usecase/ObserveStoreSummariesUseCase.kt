package blue.starry.onemorecoffee.core.domain.usecase

import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.domain.repository.StoreRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveStoreSummariesUseCase @Inject constructor(
    private val storeRepository: StoreRepository,
) {
    operator fun invoke(): Flow<List<StoreVisitSummary>> {
        return storeRepository.observeStoreSummaries()
    }
}

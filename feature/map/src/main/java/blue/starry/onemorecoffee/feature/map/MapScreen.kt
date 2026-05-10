package blue.starry.onemorecoffee.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.ui.StoreDetailSheet
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapScreenViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        MapUiState.Empty -> EmptyMap(modifier = modifier)
        MapUiState.Loading -> LoadingMap(modifier = modifier)
        is MapUiState.Ready -> StoreMap(
            stores = state.stores,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingMap(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyMap(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "店舗データを更新してください")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreMap(
    stores: List<StoreVisitSummary>,
    modifier: Modifier = Modifier,
) {
    var selectedStore by remember { mutableStateOf<StoreVisitSummary?>(null) }
    val tokyoStation = LatLng(35.681236, 139.767125)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(tokyoStation, 11f)
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
    ) {
        stores.forEach { store ->
            val position = LatLng(store.latitude, store.longitude)
            val markerHue = when {
                store.isVisited -> BitmapDescriptorFactory.HUE_GREEN
                store.isReserve -> BitmapDescriptorFactory.HUE_VIOLET
                else -> BitmapDescriptorFactory.HUE_RED
            }

            key(store.id) {
                Marker(
                    state = rememberUpdatedMarkerState(position = position),
                    title = store.name,
                    snippet = if (store.isVisited) "Visited" else "Unvisited",
                    icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                    onClick = {
                        selectedStore = store
                        true
                    },
                )
            }
        }
    }

    selectedStore?.let { store ->
        ModalBottomSheet(
            onDismissRequest = {
                selectedStore = null
            },
        ) {
            StoreDetailSheet(store = store)
        }
    }
}

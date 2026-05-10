package blue.starry.onemorecoffee.feature.map

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.ui.StoreDetailSheet
import blue.starry.onemorecoffee.core.ui.StoreRefreshProgressDialog
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: MapScreenViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val content = uiState.content) {
            MapContentState.Empty -> EmptyMap(
                onRefreshStoresClick = viewModel::refreshStores,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
            MapContentState.Loading -> LoadingMap(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
            is MapContentState.Ready -> StoreMap(
                stores = content.stores,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }

        uiState.statusMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .padding(contentPadding),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (uiState.isRefreshing) {
        StoreRefreshProgressDialog(message = uiState.progressMessage ?: "店舗マスタを更新しています")
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
    onRefreshStoresClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "店舗マスタが未取得です")
            Button(onClick = onRefreshStoresClick) {
                Text(text = "店舗データを取得")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreMap(
    stores: List<StoreVisitSummary>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedStore by remember { mutableStateOf<StoreVisitSummary?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(context.hasLocationPermission())
    }
    var movedToInitialLocation by remember { mutableStateOf(false) }
    val tokyoStation = LatLng(35.681236, 139.767125)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(tokyoStation, 11f)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && !movedToInitialLocation) {
            movedToInitialLocation = true
            context.currentLatLngOrNull()?.let { currentLocation ->
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLocation, 14f))
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = contentPadding,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = hasLocationPermission,
                zoomControlsEnabled = true,
            ),
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

private fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
private suspend fun Context.currentLatLngOrNull(): LatLng? {
    if (!hasLocationPermission()) {
        return null
    }

    val client = LocationServices.getFusedLocationProviderClient(this)
    val tokenSource = CancellationTokenSource()
    val location = suspendCancellableCoroutine<Location?> { continuation ->
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
            .addOnSuccessListener { location ->
                continuation.resume(location)
            }
            .addOnFailureListener {
                continuation.resume(null)
            }

        continuation.invokeOnCancellation {
            tokenSource.cancel()
        }
    }

    return location?.let { LatLng(it.latitude, it.longitude) }
}

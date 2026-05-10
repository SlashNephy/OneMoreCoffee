package blue.starry.onemorecoffee.feature.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import blue.starry.onemorecoffee.core.domain.model.StoreVisitSummary
import blue.starry.onemorecoffee.core.ui.StoreDetailSheet
import blue.starry.onemorecoffee.core.ui.StoreRefreshProgressDialog
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.AbstractAlgorithm
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.clustering.algo.StaticCluster
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.google.android.gms.maps.GoogleMap as MapsGoogleMap

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

@OptIn(ExperimentalMaterial3Api::class, MapsComposeExperimentalApi::class)
@Composable
private fun StoreMap(
    stores: List<StoreVisitSummary>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    var selectedStore by remember { mutableStateOf<StoreVisitSummary?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(context.hasLocationPermission())
    }
    var movedToInitialLocation by remember { mutableStateOf(false) }
    var locationMoveRequest by remember { mutableIntStateOf(0) }
    val tokyoStation = LatLng(35.681236, 139.767125)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(tokyoStation, 11f)
    }
    val clusterItems = remember(stores) {
        stores.map { StoreClusterItem(store = it) }
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
            locationMoveRequest += 1
            movedToInitialLocation = true
        }
    }

    LaunchedEffect(locationMoveRequest) {
        if (locationMoveRequest > 0 && hasLocationPermission) {
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
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
            ),
        ) {
            val clusterManager = rememberClusterManager<StoreClusterItem>()
            var clusterRenderer by remember(clusterManager) { mutableStateOf<StoreClusterRenderer?>(null) }

            clusterManager?.let { manager ->
                MapEffect(manager) { map ->
                    val renderer = StoreClusterRenderer(context, map, manager)
                    manager.renderer = renderer
                    manager.algorithm = ZoomLevelClusterAlgorithm(ClusterReleaseZoom)
                    manager.setAnimation(false)
                    clusterRenderer = renderer
                }

                SideEffect {
                    manager.setOnClusterItemClickListener { item ->
                        selectedStore = item.store
                        true
                    }
                }

                if (clusterRenderer != null) {
                    Clustering(
                        items = clusterItems,
                        clusterManager = manager,
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (!hasLocationPermission) {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                    return@FloatingActionButton
                }
                locationMoveRequest += 1
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_mylocation),
                contentDescription = "現在位置へ移動",
            )
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

internal fun buildClusterLabel(
    totalCount: Int,
    visitedCount: Int,
): String {
    val totalLabel = if (totalCount < 10) {
        totalCount.toString()
    } else {
        "${totalCount / 10 * 10}+"
    }

    return if (visitedCount > 0) {
        "$totalLabel ($visitedCount)"
    } else {
        totalLabel
    }
}

internal fun shouldReleaseClusterAtZoom(zoom: Float): Boolean {
    return zoom >= ClusterReleaseZoom
}

internal fun clusterFillColor(
    totalCount: Int,
    visitedCount: Int,
): Int {
    val unvisitedCount = totalCount - visitedCount
    return if (unvisitedCount > totalCount / 2) {
        ClusterUnvisitedMajorityColor
    } else {
        ClusterDefaultColor
    }
}

private class ZoomLevelClusterAlgorithm(
    private val releaseZoom: Float,
) : AbstractAlgorithm<StoreClusterItem>() {
    private val delegate = NonHierarchicalDistanceBasedAlgorithm<StoreClusterItem>()

    override fun addItem(item: StoreClusterItem): Boolean {
        return delegate.addItem(item)
    }

    override fun addItems(items: Collection<StoreClusterItem>): Boolean {
        return delegate.addItems(items)
    }

    override fun clearItems() {
        delegate.clearItems()
    }

    override fun removeItem(item: StoreClusterItem): Boolean {
        return delegate.removeItem(item)
    }

    override fun updateItem(item: StoreClusterItem): Boolean {
        return delegate.updateItem(item)
    }

    override fun removeItems(items: Collection<StoreClusterItem>): Boolean {
        return delegate.removeItems(items)
    }

    override fun getClusters(zoom: Float): Set<Cluster<StoreClusterItem>> {
        if (zoom >= releaseZoom) {
            return delegate.items.mapTo(linkedSetOf()) { item ->
                StaticCluster<StoreClusterItem>(item.position).apply {
                    add(item)
                }
            }
        }

        return delegate.getClusters(zoom)
    }

    override fun getItems(): Collection<StoreClusterItem> {
        return delegate.items
    }

    override fun setMaxDistanceBetweenClusteredItems(maxDistance: Int) {
        delegate.maxDistanceBetweenClusteredItems = maxDistance
    }

    override fun getMaxDistanceBetweenClusteredItems(): Int {
        return delegate.maxDistanceBetweenClusteredItems
    }
}

private class StoreClusterRenderer(
    private val context: Context,
    map: MapsGoogleMap,
    clusterManager: ClusterManager<StoreClusterItem>,
) : DefaultClusterRenderer<StoreClusterItem>(context, map, clusterManager) {
    private val clusterIconCache = mutableMapOf<ClusterIconKey, BitmapDescriptor>()
    private val storeIconCache = mutableMapOf<StoreMarkerStyle, BitmapDescriptor>()

    init {
        minClusterSize = 2
        setAnimation(false)
    }

    override fun onBeforeClusterItemRendered(
        item: StoreClusterItem,
        markerOptions: MarkerOptions,
    ) {
        super.onBeforeClusterItemRendered(item, markerOptions)
        markerOptions
            .icon(storeIconFor(item.store))
            .anchor(0.5f, 0.5f)
    }

    override fun onClusterItemUpdated(
        item: StoreClusterItem,
        marker: Marker,
    ) {
        super.onClusterItemUpdated(item, marker)
        marker.setIcon(storeIconFor(item.store))
        marker.setAnchor(0.5f, 0.5f)
    }

    override fun onBeforeClusterRendered(
        cluster: Cluster<StoreClusterItem>,
        markerOptions: MarkerOptions,
    ) {
        markerOptions
            .icon(clusterIconFor(cluster))
            .anchor(0.5f, 0.5f)
    }

    override fun onClusterUpdated(
        cluster: Cluster<StoreClusterItem>,
        marker: Marker,
    ) {
        marker.setIcon(clusterIconFor(cluster))
        marker.setAnchor(0.5f, 0.5f)
    }

    private fun storeIconFor(store: StoreVisitSummary): BitmapDescriptor {
        val style = when {
            store.isVisited -> StoreMarkerStyle.Visited
            store.isReserve -> StoreMarkerStyle.Reserve
            else -> StoreMarkerStyle.Unvisited
        }

        return storeIconCache.getOrPut(style) {
            BitmapDescriptorFactory.fromBitmap(createStoreMarkerBitmap(context, style.color))
        }
    }

    private fun clusterIconFor(cluster: Cluster<StoreClusterItem>): BitmapDescriptor {
        val visitedCount = cluster.items.count { it.store.isVisited }
        val label = buildClusterLabel(
            totalCount = cluster.size,
            visitedCount = visitedCount,
        )
        val fillColor = clusterFillColor(
            totalCount = cluster.size,
            visitedCount = visitedCount,
        )
        val cacheKey = ClusterIconKey(label = label, fillColor = fillColor)

        return clusterIconCache.getOrPut(cacheKey) {
            BitmapDescriptorFactory.fromBitmap(createClusterMarkerBitmap(context, label, fillColor))
        }
    }
}

private data class ClusterIconKey(
    val label: String,
    val fillColor: Int,
)

private enum class StoreMarkerStyle(
    val color: Int,
) {
    Visited(0xFF2E7D32.toInt()),
    Reserve(0xFF6A1B9A.toInt()),
    Unvisited(0xFFC62828.toInt()),
}

private data class StoreClusterItem(
    val store: StoreVisitSummary,
) : ClusterItem {
    override fun getPosition(): LatLng {
        return LatLng(store.latitude, store.longitude)
    }

    override fun getTitle(): String {
        return store.name
    }

    override fun getSnippet(): String {
        return if (store.isVisited) "Visited" else "Unvisited"
    }

    override fun getZIndex(): Float {
        return 0f
    }
}

private fun createStoreMarkerBitmap(
    context: Context,
    fillColor: Int,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt()
    val iconSize = (20 * density).toInt()
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
    }

    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    val icon = requireNotNull(ResourcesCompat.getDrawable(context.resources, R.drawable.local_cafe_fill, context.theme)) {
        "local_cafe_fill drawable is missing"
    }.mutate()
    icon.setTint(Color.WHITE)

    val iconLeft = (size - iconSize) / 2
    val iconTop = (size - iconSize) / 2
    icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
    icon.draw(canvas)

    return bitmap
}

private fun createClusterMarkerBitmap(
    context: Context,
    label: String,
    fillColor: Int,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13 * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textBounds = Rect()
    textPaint.getTextBounds(label, 0, label.length, textBounds)

    val horizontalPadding = (10 * density).toInt()
    val verticalPadding = (7 * density).toInt()
    val height = maxOf((34 * density).toInt(), textBounds.height() + verticalPadding * 2)
    val width = maxOf(height, textBounds.width() + horizontalPadding * 2)
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
    }

    canvas.drawRoundRect(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        height / 2f,
        height / 2f,
        backgroundPaint,
    )
    canvas.drawText(
        label,
        (width - textBounds.width()) / 2f - textBounds.left,
        (height + textBounds.height()) / 2f - textBounds.bottom,
        textPaint,
    )

    return bitmap
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
    val location = suspendCancellableCoroutine { continuation ->
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

private const val ClusterReleaseZoom = 14f
private const val ClusterDefaultColor = 0xFF00704A.toInt()
private const val ClusterUnvisitedMajorityColor = 0xFFC62828.toInt()

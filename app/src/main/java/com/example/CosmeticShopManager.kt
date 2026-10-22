package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RunnerAccessoryStyle {
    CLASSIC_CAP,
    CYBER_NINJA_SCARF,
    PARKOUR_HOODIE_PACK,
    DESERT_NOMAD_KEFFIYEH,
    ROYAL_CYBORG_CREST,
    OLYMPIC_VISOR_PONYTAIL
}

enum class CosmeticCategory(val label: String) {
    OUTFIT("OUTFIT"),
    RUNNER_MODEL("3D MODEL")
}

data class RunnerCosmetic(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: CosmeticCategory,
    val price: Int,
    val accessoryStyle: RunnerAccessoryStyle,
    val buildWidthScale: Float = 1.0f,
    val skinBase: Color,
    val skinShade: Color,
    val jerseyBase: Color,
    val jerseyShade: Color,
    val jerseyPanel: Color,
    val reflectiveStripe: Color,
    val shortsBase: Color,
    val shortsShade: Color,
    val compressionColor: Color,
    val shoePrimary: Color,
    val shoeSole: Color,
    val shoeAccent: Color,
    val sockColor: Color,
    val hairColor: Color,
    val capColor: Color,
    val capAccent: Color,
    val isUnlocked: Boolean = false,
    val isEquipped: Boolean = false
)

enum class CosmeticPurchaseResult {
    UNLOCKED_AND_EQUIPPED,
    EQUIPPED,
    INSUFFICIENT_FUNDS,
    NOT_FOUND
}

object CosmeticShopManager {
    private const val PREFS_NAME = "city_runner_cosmetic_shop"
    private const val KEY_EQUIPPED_ID = "equipped_cosmetic_id"
    private const val DEFAULT_ID = "metro_sprinter"

    private var prefs: SharedPreferences? = null

    private val defaultCatalog: List<RunnerCosmetic> = listOf(
        RunnerCosmetic(
            id = "metro_sprinter",
            name = "Metro Sprinter",
            subtitle = "Standard-issue crimson tech jersey & cobalt marathon racers",
            category = CosmeticCategory.OUTFIT,
            price = 0,
            accessoryStyle = RunnerAccessoryStyle.CLASSIC_CAP,
            buildWidthScale = 1.0f,
            skinBase = Color(0xFFE5AA7A),
            skinShade = Color(0xFFC68652),
            jerseyBase = Color(0xFFE64A19),
            jerseyShade = Color(0xFFBF360C),
            jerseyPanel = Color(0xFF263238),
            reflectiveStripe = Color(0xFF00E5FF),
            shortsBase = Color(0xFF1E293B),
            shortsShade = Color(0xFF0F172A),
            compressionColor = Color(0xFF111827),
            shoePrimary = Color(0xFF1E88E5),
            shoeSole = Color(0xFFF8FAFC),
            shoeAccent = Color(0xFF00E676),
            sockColor = Color(0xFFECEFF1),
            hairColor = Color(0xFF2D1E18),
            capColor = Color(0xFF1E293B),
            capAccent = Color(0xFF00E5FF),
            isUnlocked = true,
            isEquipped = true
        ),
        RunnerCosmetic(
            id = "paris_parkour_pro",
            name = "Parisian Parkour Pro",
            subtitle = "3D hooded windbreaker, cross-body sling pack & gum-sole high-tops",
            category = CosmeticCategory.OUTFIT,
            price = 200,
            accessoryStyle = RunnerAccessoryStyle.PARKOUR_HOODIE_PACK,
            buildWidthScale = 1.04f,
            skinBase = Color(0xFFF1C27D),
            skinShade = Color(0xFFD2995A),
            jerseyBase = Color(0xFF1565C0),
            jerseyShade = Color(0xFF0D47A1),
            jerseyPanel = Color(0xFFFFB300),
            reflectiveStripe = Color(0xFFFFF176),
            shortsBase = Color(0xFF37474F),
            shortsShade = Color(0xFF263238),
            compressionColor = Color(0xFF1C262B),
            shoePrimary = Color(0xFFD32F2F),
            shoeSole = Color(0xFFD7CCC8),
            shoeAccent = Color(0xFFFFFFFF),
            sockColor = Color(0xFF263238),
            hairColor = Color(0xFF3E2723),
            capColor = Color(0xFF0D47A1),
            capAccent = Color(0xFFFFCA28)
        ),
        RunnerCosmetic(
            id = "tokyo_cyber_ninja",
            name = "Tokyo Cyber-Ninja",
            subtitle = "Stealth shinobi 3D model with flowing neon scarf & twin back katanas",
            category = CosmeticCategory.RUNNER_MODEL,
            price = 250,
            accessoryStyle = RunnerAccessoryStyle.CYBER_NINJA_SCARF,
            buildWidthScale = 0.94f,
            skinBase = Color(0xFF263238),
            skinShade = Color(0xFF11171A),
            jerseyBase = Color(0xFF1A1A2E),
            jerseyShade = Color(0xFF0F0F1A),
            jerseyPanel = Color(0xFF311B92),
            reflectiveStripe = Color(0xFFFF007F),
            shortsBase = Color(0xFF12121F),
            shortsShade = Color(0xFF080810),
            compressionColor = Color(0xFF1F1F33),
            shoePrimary = Color(0xFF1A1A2E),
            shoeSole = Color(0xFFFF007F),
            shoeAccent = Color(0xFF00E5FF),
            sockColor = Color(0xFF311B92),
            hairColor = Color(0xFF0D0D14),
            capColor = Color(0xFF16213E),
            capAccent = Color(0xFFFF007F)
        ),
        RunnerCosmetic(
            id = "cairo_desert_nomad",
            name = "Cairo Desert Nomad",
            subtitle = "Royal turquoise & gold tunic with billowing white keffiyeh headwrap",
            category = CosmeticCategory.OUTFIT,
            price = 300,
            accessoryStyle = RunnerAccessoryStyle.DESERT_NOMAD_KEFFIYEH,
            buildWidthScale = 1.0f,
            skinBase = Color(0xFFC68642),
            skinShade = Color(0xFF8D5524),
            jerseyBase = Color(0xFF00695C),
            jerseyShade = Color(0xFF004D40),
            jerseyPanel = Color(0xFFFFB300),
            reflectiveStripe = Color(0xFFFFE082),
            shortsBase = Color(0xFFD7CCC8),
            shortsShade = Color(0xFFA1887F),
            compressionColor = Color(0xFF4E342E),
            shoePrimary = Color(0xFFFF8F00),
            shoeSole = Color(0xFFFFE082),
            shoeAccent = Color(0xFF26A69A),
            sockColor = Color(0xFFECEFF1),
            hairColor = Color(0xFF1B120C),
            capColor = Color(0xFFFAFAFA),
            capAccent = Color(0xFFFFB300)
        ),
        RunnerCosmetic(
            id = "sydney_gold_medalist",
            name = "Sydney Gold Medalist",
            subtitle = "Olympic sprinter model with sport visor, ponytail & golden spikes",
            category = CosmeticCategory.RUNNER_MODEL,
            price = 350,
            accessoryStyle = RunnerAccessoryStyle.OLYMPIC_VISOR_PONYTAIL,
            buildWidthScale = 0.96f,
            skinBase = Color(0xFFE0AC69),
            skinShade = Color(0xFFB87D42),
            jerseyBase = Color(0xFF00897B),
            jerseyShade = Color(0xFF004D40),
            jerseyPanel = Color(0xFFFFD600),
            reflectiveStripe = Color(0xFFFFFFFF),
            shortsBase = Color(0xFF004D40),
            shortsShade = Color(0xFF00251A),
            compressionColor = Color(0xFFFFCA28),
            shoePrimary = Color(0xFFFFD600),
            shoeSole = Color(0xFFFFFFFF),
            shoeAccent = Color(0xFF00E676),
            sockColor = Color(0xFFFFF59D),
            hairColor = Color(0xFFD4A359),
            capColor = Color(0xFFFFD600),
            capAccent = Color(0xFF00695C)
        ),
        RunnerCosmetic(
            id = "london_royal_cyborg",
            name = "Royal Guard Mecha",
            subtitle = "Cyber-alloy 3D android model with back jet thrusters & energy crest",
            category = CosmeticCategory.RUNNER_MODEL,
            price = 450,
            accessoryStyle = RunnerAccessoryStyle.ROYAL_CYBORG_CREST,
            buildWidthScale = 1.08f,
            skinBase = Color(0xFF90A4AE),
            skinShade = Color(0xFF546E7A),
            jerseyBase = Color(0xFFB71C1C),
            jerseyShade = Color(0xFF7F0000),
            jerseyPanel = Color(0xFF263238),
            reflectiveStripe = Color(0xFFFFD740),
            shortsBase = Color(0xFF263238),
            shortsShade = Color(0xFF11171A),
            compressionColor = Color(0xFF37474F),
            shoePrimary = Color(0xFF455A64),
            shoeSole = Color(0xFFFFAB00),
            shoeAccent = Color(0xFF00E5FF),
            sockColor = Color(0xFF78909C),
            hairColor = Color(0xFF212121),
            capColor = Color(0xFF212121),
            capAccent = Color(0xFFFFD740)
        )
    )

    private val _cosmetics = MutableStateFlow(defaultCatalog)
    val cosmetics: StateFlow<List<RunnerCosmetic>> = _cosmetics.asStateFlow()

    private val _equippedCosmetic = MutableStateFlow(defaultCatalog.first())
    val equippedCosmetic: StateFlow<RunnerCosmetic> = _equippedCosmetic.asStateFlow()

    fun init(context: Context? = null) {
        if (context != null) {
            try {
                val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs = sp
                val equippedId = sp.getString(KEY_EQUIPPED_ID, DEFAULT_ID) ?: DEFAULT_ID
                val updated = defaultCatalog.map { item ->
                    val unlocked = item.id == DEFAULT_ID || sp.getBoolean("unlocked_${item.id}", false)
                    val equipped = unlocked && item.id == equippedId
                    item.copy(isUnlocked = unlocked, isEquipped = equipped)
                }.let { list ->
                    if (list.none { it.isEquipped }) {
                        list.map { it.copy(isEquipped = it.id == DEFAULT_ID) }
                    } else list
                }
                _cosmetics.value = updated
                _equippedCosmetic.value = updated.first { it.isEquipped }
            } catch (_: Exception) {
                resetInMemory()
            }
        }
    }

    fun resetForTesting() {
        prefs?.edit()?.clear()?.apply()
        resetInMemory()
    }

    private fun resetInMemory() {
        _cosmetics.value = defaultCatalog
        _equippedCosmetic.value = defaultCatalog.first()
    }

    /**
     * Unlocks (spending virtual currency via [DailyChallengeManager]) and equips the cosmetic with [cosmeticId],
     * or simply equips it if already unlocked.
     */
    fun unlockOrEquip(cosmeticId: String): CosmeticPurchaseResult {
        val currentList = _cosmetics.value
        val target = currentList.find { it.id == cosmeticId } ?: return CosmeticPurchaseResult.NOT_FOUND

        if (target.isUnlocked) {
            equip(cosmeticId)
            return CosmeticPurchaseResult.EQUIPPED
        }

        val spent = DailyChallengeManager.spendCurrency(target.price)
        if (!spent) {
            return CosmeticPurchaseResult.INSUFFICIENT_FUNDS
        }

        val updatedList = currentList.map { item ->
            when {
                item.id == cosmeticId -> item.copy(isUnlocked = true, isEquipped = true)
                else -> item.copy(isEquipped = false)
            }
        }
        _cosmetics.value = updatedList
        _equippedCosmetic.value = updatedList.first { it.isEquipped }
        SoundManager.playCollectSound()
        persistState(updatedList)
        return CosmeticPurchaseResult.UNLOCKED_AND_EQUIPPED
    }

    fun equip(cosmeticId: String): Boolean {
        val currentList = _cosmetics.value
        val target = currentList.find { it.id == cosmeticId } ?: return false
        if (!target.isUnlocked) return false

        val updatedList = currentList.map { item ->
            item.copy(isEquipped = item.id == cosmeticId)
        }
        _cosmetics.value = updatedList
        _equippedCosmetic.value = updatedList.first { it.isEquipped }
        persistState(updatedList)
        return true
    }

    private fun persistState(list: List<RunnerCosmetic>) {
        val sp = prefs ?: return
        val equipped = list.firstOrNull { it.isEquipped }?.id ?: DEFAULT_ID
        sp.edit().apply {
            putString(KEY_EQUIPPED_ID, equipped)
            for (item in list) {
                putBoolean("unlocked_${item.id}", item.isUnlocked)
            }
            apply()
        }
    }
}

@Composable
fun CosmeticShopDialog(
    cosmetics: List<RunnerCosmetic>,
    equippedCosmetic: RunnerCosmetic,
    currencyBalance: Int,
    onUnlockOrEquip: (String) -> CosmeticPurchaseResult,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<CosmeticCategory?>(null) }
    var previewCosmeticId by remember(equippedCosmetic.id) { mutableStateOf(equippedCosmetic.id) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val previewCosmetic = cosmetics.find { it.id == previewCosmeticId } ?: equippedCosmetic
    val filteredList = remember(cosmetics, selectedCategory) {
        if (selectedCategory == null) cosmetics else cosmetics.filter { it.category == selectedCategory }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ShopPreviewRunner")
    val previewDistance by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12.566f, // 4 * PI for smooth continuous stride cycles
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "previewStride"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .testTag("cosmetic_shop_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Checkroom,
                            contentDescription = "Cosmetic Shop",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "COSMETIC ARMORY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "UNLOCK OUTFITS & 3D RUNNER MODELS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    VirtualCurrencyBadge(balance = currencyBalance)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live 3D Runner Preview Stage
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF0F172A))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = previewCosmetic.reflectiveStripe.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("cosmetic_3d_preview_stage")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Holographic turntable platform beneath the runner
                        val stageCenterX = size.width * 0.30f
                        val stageGroundY = size.height * 0.85f
                        drawOval(
                            color = previewCosmetic.reflectiveStripe.copy(alpha = 0.25f),
                            topLeft = Offset(stageCenterX - 52.dp.toPx(), stageGroundY - 10.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(104.dp.toPx(), 20.dp.toPx())
                        )

                        with(RunnerRenderer) {
                            drawRunnerPreview(
                                cosmetic = previewCosmetic,
                                previewDistance = previewDistance,
                                centerX = stageCenterX,
                                groundY = stageGroundY,
                                scale = 1.15f
                            )
                        }
                    }

                    // Previewed Cosmetic Info Overlay on the Right of the Stage
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxWidth(0.54f)
                            .padding(end = 14.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = previewCosmetic.reflectiveStripe.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = previewCosmetic.category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = previewCosmetic.reflectiveStripe,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = previewCosmetic.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = previewCosmetic.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f),
                            lineHeight = 15.sp
                        )
                        if (feedbackMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = feedbackMessage!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFCA28),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("ALL (${cosmetics.size})", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    FilterChip(
                        selected = selectedCategory == CosmeticCategory.OUTFIT,
                        onClick = { selectedCategory = CosmeticCategory.OUTFIT },
                        label = { Text("OUTFITS", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    FilterChip(
                        selected = selectedCategory == CosmeticCategory.RUNNER_MODEL,
                        onClick = { selectedCategory = CosmeticCategory.RUNNER_MODEL },
                        label = { Text("3D MODELS", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // List of Cosmetics
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        val isPreviewed = item.id == previewCosmetic.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    previewCosmeticId = item.id
                                    feedbackMessage = null
                                }
                                .testTag("cosmetic_card_${item.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPreviewed) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                                }
                            ),
                            border = BorderStroke(
                                width = if (item.isEquipped || isPreviewed) 1.5.dp else 1.dp,
                                color = when {
                                    item.isEquipped -> Color(0xFF64FFDA)
                                    isPreviewed -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Color swatch badge
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(item.jerseyBase, RoundedCornerShape(8.dp))
                                        .border(2.dp, item.reflectiveStripe, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(item.capColor, CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "[${item.category.label}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = item.reflectiveStripe,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Action button: EQUIPPED / EQUIP / UNLOCK (Price CR)
                                val canAfford = currencyBalance >= item.price
                                Button(
                                    onClick = {
                                        previewCosmeticId = item.id
                                        val res = onUnlockOrEquip(item.id)
                                        feedbackMessage = when (res) {
                                            CosmeticPurchaseResult.UNLOCKED_AND_EQUIPPED ->
                                                "Unlocked & Equipped ${item.name}!"
                                            CosmeticPurchaseResult.EQUIPPED ->
                                                "Equipped ${item.name}!"
                                            CosmeticPurchaseResult.INSUFFICIENT_FUNDS ->
                                                "Need ${item.price - currencyBalance} more CR! Complete Daily Challenges."
                                            CosmeticPurchaseResult.NOT_FOUND -> null
                                        }
                                    },
                                    enabled = !item.isEquipped,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            item.isEquipped -> Color(0xFF004D40)
                                            item.isUnlocked -> MaterialTheme.colorScheme.primary
                                            canAfford -> Color(0xFFFFB300)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = when {
                                            item.isUnlocked -> Color.Black
                                            canAfford -> Color.Black
                                            else -> Color.White.copy(alpha = 0.7f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("cosmetic_action_${item.id}")
                                ) {
                                    when {
                                        item.isEquipped -> {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Equipped",
                                                tint = Color(0xFF64FFDA),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "EQUIPPED",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF64FFDA),
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        item.isUnlocked -> {
                                            Text(
                                                text = "EQUIP",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Unlock",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${item.price} CR",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("close_cosmetic_shop_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                ) {
                    Text("DONE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

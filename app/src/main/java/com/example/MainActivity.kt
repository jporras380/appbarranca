@file:OptIn(ExperimentalMaterial3Api::class)
package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ConsumptionLog
import com.example.data.Product
import com.example.ui.theme.*
import com.example.utils.PdfReporter
import com.example.viewmodel.InventoryViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: InventoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channel for critical stock warning
        createNotificationChannel(this)

        // Request notification permission if Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("app_scaffold")
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas de Insumos Críticos"
            val descriptionText = "Notifica cuando un insumo está por debajo de su stock mínimo"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("CRITICAL_STOCK_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

// Function to trigger system notification (fully independent to avoid compose overload names)
fun triggerLowStockSystemNotification(context: Context, product: Product) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    val builder = NotificationCompat.Builder(context, "CRITICAL_STOCK_CHANNEL")
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("⚠️ Stock Crítico - El Paraíso")
        .setContentText("El insumo '${product.name}' bajó a ${product.currentStock} ${product.unit}. ¡Reponer pronto!")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)

    try {
        notificationManager.notify(product.id, builder.build())
    } catch (e: SecurityException) {
        e.printStackTrace()
    }
}

@Composable
fun MainScreen(viewModel: InventoryViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    
    val products by viewModel.products.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val workers by viewModel.workers.collectAsStateWithLifecycle()
    
    var isAdminMode by remember { mutableStateOf(false) }
    var isLoginPassed by remember { mutableStateOf(false) }
    var activeWorker by remember { mutableStateOf<com.example.data.Worker?>(null) }
    
    // Auto-select the first worker once workers list is loaded and activeWorker is null
    LaunchedEffect(workers) {
        if (activeWorker == null && workers.isNotEmpty()) {
            activeWorker = workers.first()
        }
    }
    
    // In-app alert flag indicating any critical stock
    val lowStockCount = products.count { it.currentStock <= it.minStockAlert }

    if (!isLoginPassed) {
        LoginScreen(
            workers = workers,
            onLoginAdminSuccess = {
                isAdminMode = true
                isLoginPassed = true
                activeWorker = workers.find { it.role.contains("Admin", ignoreCase = true) } ?: workers.firstOrNull()
            },
            onLoginWorkerSuccess = { worker ->
                isAdminMode = false
                isLoginPassed = true
                activeWorker = worker
            }
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            // Resort Header Banner with role info and logout action
            ResortHeaderBanner(
                lowStockCount = lowStockCount,
                isAdminMode = isAdminMode,
                activeWorker = activeWorker,
                onLogoutClick = {
                    isLoginPassed = false
                    selectedTab = 0
                }
            )

            val tabCount = if (isAdminMode) 3 else 2
            val coercedTab = selectedTab.coerceAtMost(tabCount - 1)

            // Tab selection row
            TabRow(
                selectedTabIndex = coercedTab,
                containerColor = ParadiseDeepTeal,
                contentColor = ParadiseIvory,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[coercedTab]),
                        color = ParadiseTealAccent
                    )
                }
            ) {
                Tab(
                    selected = coercedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Inventario", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.List, contentDescription = "Inventario") }
                )
                Tab(
                    selected = coercedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Historial", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "Historial") }
                )
                if (isAdminMode) {
                    Tab(
                        selected = coercedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Gestión", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Gestión") }
                    )
                }
            }

            when (coercedTab) {
                0 -> InventoryTab(
                    products = products,
                    lowStockCount = lowStockCount,
                    workers = workers,
                    activeWorker = activeWorker,
                    onActiveWorkerChange = { activeWorker = it },
                onInstantConsumeClick = { product ->
                    val workerName = activeWorker?.name ?: "Personal de Turno"
                    if (product.currentStock >= 1.0) {
                        viewModel.registerConsumption(product, 1.0, workerName, "Descuento rápido en 1 click") { success ->
                            if (success) {
                                Toast.makeText(context, "Se descontó 1 ${product.unit} de '${product.name}' (Cargado a: $workerName)", Toast.LENGTH_SHORT).show()
                                
                                // Check if stock just dropped below threshold
                                val finalStock = product.currentStock - 1.0
                                if (finalStock <= product.minStockAlert) {
                                    triggerLowStockSystemNotification(context, product.copy(currentStock = finalStock))
                                }
                            } else {
                                Toast.makeText(context, "¡Error! Stock insuficiente disponible", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "¡Stock Insuficiente! El stock actual de '${product.name}' es ${product.currentStock}", Toast.LENGTH_LONG).show()
                    }
                }
            )
            1 -> {
                HistoryTab(
                    logs = logs,
                    products = products,
                    onUndoLog = { viewModel.undoConsumption(it) }
                )
            }
            2 -> {
                ManagementTab(
                    products = products,
                    workers = workers,
                    onAddProduct = { name, cat, stock, min, unit, icon, cost ->
                        viewModel.addProduct(name, cat, stock, min, unit, icon, cost)
                    },
                    onDeleteProduct = { viewModel.deleteProduct(it) },
                    onAddWorker = { name, role ->
                        viewModel.addWorker(name, role)
                    },
                    onDeleteWorker = { worker ->
                        viewModel.deleteWorker(worker)
                        if (activeWorker?.id == worker.id) {
                            activeWorker = null
                        }
                    }
                )
            }
        }
    }
}
}

@Composable
fun ResortHeaderBanner(
    lowStockCount: Int,
    isAdminMode: Boolean,
    activeWorker: com.example.data.Worker?,
    onLogoutClick: () -> Unit
) {
    Surface(
        color = ParadiseIvory,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Brand Logo & Title Group
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(ParadiseMediumTeal, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Hotel Logo",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "El Paraíso",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ParadiseCoal,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Badge indicating current role
                            Text(
                                text = if (isAdminMode) "Admin" else (activeWorker?.name?.split(" ")?.firstOrNull() ?: "Personal"),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ParadiseIvory,
                                modifier = Modifier
                                    .background(if (isAdminMode) Color(0xFFE53935) else ParadiseMediumTeal, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = "Gestión de Insumos",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ParadiseGrayDark
                        )
                    }
                }

                // Right Notification / Status Badge Group
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(ParadiseGrayLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notificaciones",
                            tint = ParadiseCoal,
                            modifier = Modifier.size(18.dp)
                        )
                        if (lowStockCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 4.dp)
                                    .size(6.dp)
                                    .background(ParadiseRed, CircleShape)
                            )
                        }
                    }

                    // Logout Button
                    IconButton(
                        onClick = onLogoutClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(ParadiseRed.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            tint = ParadiseRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sleek Critical Alert Chip/Pill from HTML
            if (lowStockCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFDAD6), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alerta",
                        tint = Color(0xFFBA1A1A),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "$lowStockCount INSUMOS EN STOCK CRÍTICO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF410002),
                        letterSpacing = 0.5.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ParadiseTealAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Todo en Orden",
                        tint = ParadiseMediumTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "TODOS LOS INSUMOS TIENEN STOCK ÓPTIMO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ParadiseDeepTeal,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InventoryTab(
    products: List<Product>,
    lowStockCount: Int,
    workers: List<com.example.data.Worker>,
    activeWorker: com.example.data.Worker?,
    onActiveWorkerChange: (com.example.data.Worker) -> Unit,
    onInstantConsumeClick: (Product) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }
    
    val categories = listOf("Todos", "Limpieza", "Higiene", "Blanquería", "Alimentos")
    
    // Filter product logs
    val filteredProducts = products.filter {
        val matchesSearch = it.name.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == "Todos" || it.category == selectedCategory
        matchesSearch && matchesCategory
    }

    Scaffold(
        containerColor = ParadiseIvory,
        floatingActionButton = {
            if (lowStockCount > 0) {
                FloatingActionButton(
                    onClick = { selectedCategory = "Todos"; searchQuery = "" },
                    containerColor = ParadiseRed,
                    contentColor = ParadiseIvory
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Low Stock Filter")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver Alertas ($lowStockCount)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Active Worker Duty / Session Banner!
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ParadiseTealAccent.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User active icon",
                                tint = ParadiseMediumTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "COLABORADOR ACTIVO EN TURNO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ParadiseMediumTeal,
                                letterSpacing = 0.5.sp
                            )
                        }
                        
                        // Current selection tag label
                        Text(
                            text = activeWorker?.name ?: "Personal de Turno",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ParadiseIvory,
                            modifier = Modifier
                                .background(ParadiseMediumTeal, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    if (workers.isEmpty()) {
                        Text(
                            text = "No hay colaboradores registrados. Configura accesos en la pestaña 'Gestión'.",
                            fontSize = 11.sp,
                            color = ParadiseGrayDark,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        // Quick Horizontal scroll selection of registered hotel staff
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            workers.forEach { worker ->
                                val isSelected = activeWorker?.id == worker.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onActiveWorkerChange(worker) },
                                    label = { Text(worker.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ParadiseMediumTeal,
                                        selectedLabelColor = ParadiseIvory,
                                        containerColor = Color.White,
                                        labelColor = ParadiseCoal
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar insumo...") },
                placeholder = { Text("Ej. Cif limpiador, papel...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inventory_search_bar"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ParadiseMediumTeal,
                    unfocusedBorderColor = ParadiseGrayDark.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Chips Selection Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ParadiseMediumTeal,
                            selectedLabelColor = ParadiseIvory,
                            containerColor = ParadiseGrayLight,
                            labelColor = ParadiseCoal
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = ParadiseMediumTeal.copy(alpha = 0.2f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LOW STOCK IN-APP ALERT BANNER
            if (lowStockCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ParadiseRed.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(ParadiseRed, ParadiseRed))),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(ParadiseRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alerta",
                                tint = ParadiseIvory,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "¡Alerta de Reabastecimiento!",
                                fontWeight = FontWeight.Bold,
                                color = ParadiseRed,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Hay $lowStockCount insumo(s) crítico(s) bajo el mínimo. ¡Toca las píldoras de stock (-1) para descontar rápidamente sin modals!",
                                color = ParadiseCoal.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // Products Header Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Insumos Disponibles (${filteredProducts.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ParadiseCoal
                )
                Text(
                    text = "Presiona la píldora de stock para restar 1",
                    fontSize = 10.sp,
                    color = ParadiseGrayDark,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "No items",
                            tint = ParadiseGrayDark.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No se encontraron insumos en esta categoría.",
                            color = ParadiseGrayDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts) { product ->
                        ProductItemCard(
                            product = product,
                            onConsumeClick = { onInstantConsumeClick(product) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductItemCard(product: Product, onConsumeClick: () -> Unit) {
    val isLowStock = product.currentStock <= product.minStockAlert
    
    // Choose theme colors depending on status
    val statusColor = if (isLowStock) ParadiseRed else ParadiseGreen
    val statusLabel = if (isLowStock) "Comprar" else "Suficiente"
    
    // Unit color styling
    val badgeBgColor = when (product.category) {
        "Limpieza" -> ParadiseTealAccent.copy(alpha = 0.12f)
        "Higiene" -> Color(0xFF673AB7).copy(alpha = 0.12f)
        "Blanquería" -> Color(0xFF009688).copy(alpha = 0.12f)
        "Alimentos" -> ParadiseSand.copy(alpha = 0.18f)
        else -> ParadiseGrayLight
    }
    
    val badgeTextColor = when (product.category) {
        "Limpieza" -> ParadiseMediumTeal
        "Higiene" -> Color(0xFF673AB7)
        "Blanquería" -> Color(0xFF009688)
        "Alimentos" -> Color(0xFFE65100)
        else -> ParadiseCoal
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConsumeClick() } // clickable entire card as subtraction trigger
            .testTag("product_card_${product.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left small decorated badge "imagen pequeña al costado" representation
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBgColor),
                contentAlignment = Alignment.Center
            ) {
                // If it is a real image URL from Gestión, use AsyncImage! Otherwise fallback to initial letter
                if (product.iconName.startsWith("http")) {
                    AsyncImage(
                        model = product.iconName,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    val initialLetter = if (product.name.isNotEmpty()) product.name.substring(0, 1).uppercase() else "P"
                    Text(
                        text = initialLetter,
                        color = badgeTextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Middle product labels
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ParadiseCoal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // Categoría
                    Text(
                        text = product.category,
                        color = badgeTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(badgeBgColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${product.currentStock}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isLowStock) ParadiseRed else ParadiseDeepTeal
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = product.unit,
                                fontSize = 11.sp,
                                color = ParadiseGrayDark,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Text(
                            text = "Mín: ${product.minStockAlert} ${product.unit}",
                            fontSize = 10.sp,
                            color = ParadiseGrayDark
                        )
                    }

                    // Cost estimation indicator
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "S/. ${String.format(Locale.US, "%.2f", product.unitCost)}/${product.unit}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ParadiseCoal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Clickable action stock pill that auto discounts 1 unit!
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor)
                                .clickable { onConsumeClick() } // instant decrement!
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "−",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ParadiseIvory
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "$statusLabel (-1)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ParadiseIvory
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    logs: List<ConsumptionLog>,
    products: List<Product>,
    onUndoLog: (ConsumptionLog) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var responsibleFilter by remember { mutableStateOf("") }
    
    // Filter variables for time ranges
    var selectedTimePeriod by remember { mutableStateOf("Periodo Completo") }
    val periods = listOf("Periodo Completo", "Últimos 7 días", "Último mes", "Filtrar por Fecha 📅")

    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }

    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    // Android Date dialogs for precise calendar selection
    val datePickerDialogStart = android.app.DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDay ->
            val c = Calendar.getInstance()
            c.set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
            startDate = c.timeInMillis
            selectedTimePeriod = "Filtrar por Fecha 📅"
        },
        year, month, day
    )

    val datePickerDialogEnd = android.app.DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDay ->
            val c = Calendar.getInstance()
            c.set(selectedYear, selectedMonth, selectedDay, 23, 59, 59)
            endDate = c.timeInMillis
            selectedTimePeriod = "Filtrar por Fecha 📅"
        },
        year, month, day
    )

    val filteredLogs = logs.filter { log ->
        val matchesResponsable = log.responsibleName.contains(responsibleFilter, ignoreCase = true)
        val now = System.currentTimeMillis()
        val oneWeek = 7L * 24 * 60 * 60 * 1000
        val oneMonth = 30L * 24 * 60 * 60 * 1000
        val matchesTime = when (selectedTimePeriod) {
            "Últimos 7 días" -> (now - log.timestamp) <= oneWeek
            "Último mes" -> (now - log.timestamp) <= oneMonth
            "Filtrar por Fecha 📅" -> {
                val start = startDate ?: 0L
                val end = endDate ?: Long.MAX_VALUE
                log.timestamp in start..end
            }
            else -> true
        }
        matchesResponsable && matchesTime
    }

    // Dynamic cost calculation of spent elements in filter
    val totalExpense = filteredLogs.sumOf { log ->
        val originalProduct = products.find { it.id == log.productId }
        (originalProduct?.unitCost ?: 0.0) * log.quantity
    }

    Scaffold(
        containerColor = ParadiseIvory,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    isGeneratingPdf = true
                    coroutineScope.launch {
                        try {
                            val simpleFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                            val d1 = if (filteredLogs.isNotEmpty()) simpleFormat.format(Date(filteredLogs.last().timestamp)) else "Inicio"
                            val d2 = if (filteredLogs.isNotEmpty()) simpleFormat.format(Date(filteredLogs.first().timestamp)) else "Fin"
                            
                            val file = PdfReporter.generateAndShareReport(
                                context = context,
                                logs = filteredLogs,
                                products = products,
                                startDateStr = d1,
                                endDateStr = d2
                            )
                            
                            if (file != null && file.exists()) {
                                // Launch Intent using FileProvider to safely share document
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "com.example.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir Reporte de Gastos PDF"))
                            } else {
                                Toast.makeText(context, "Error al crear el archivo PDF", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isGeneratingPdf = false
                        }
                    }
                },
                containerColor = ParadiseMediumTeal,
                contentColor = ParadiseIvory,
                icon = { Icon(Icons.Default.Share, contentDescription = "PDF icon") },
                text = { Text("Exportar Reporte PDF", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // General Stats & Expenses Header Card matching frontend elegance
            Card(
                colors = CardDefaults.cardColors(containerColor = ParadiseDeepTeal),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RESUMEN DE GASTOS",
                        color = ParadiseSand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "S/. ${String.format(Locale.US, "%.2f", totalExpense)}",
                        color = ParadiseIvory,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Paraíso Apart Hotel - Control de egresos basado en $selectedTimePeriod con ${filteredLogs.size} actividades registradas.",
                        color = ParadiseIvory.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Time Period select chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                periods.forEach { period ->
                    val isSel = selectedTimePeriod == period
                    FilterChip(
                        selected = isSel,
                        onClick = { 
                            selectedTimePeriod = period 
                            if (period != "Filtrar por Fecha 📅") {
                                startDate = null
                                endDate = null
                            }
                        },
                        label = { Text(period, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ParadiseMediumTeal,
                            selectedLabelColor = ParadiseIvory,
                            containerColor = ParadiseGrayLight,
                            labelColor = ParadiseCoal
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Calendar Pickers layout when dates are active or when selected filter is Date
            if (selectedTimePeriod == "Filtrar por Fecha 📅") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { datePickerDialogStart.show() },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(ParadiseMediumTeal.copy(alpha = 0.3f), ParadiseMediumTeal.copy(alpha = 0.3f))))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("DESDE", fontSize = 9.sp, color = ParadiseGrayDark, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            val dText = if (startDate != null) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(startDate!!)) else "Seleccionar 📅"
                            Text(dText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ParadiseCoal)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { datePickerDialogEnd.show() },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(ParadiseMediumTeal.copy(alpha = 0.3f), ParadiseMediumTeal.copy(alpha = 0.3f))))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("HASTA", fontSize = 9.sp, color = ParadiseGrayDark, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            val dText = if (endDate != null) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(endDate!!)) else "Seleccionar 📅"
                            Text(dText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ParadiseCoal)
                        }
                    }

                    // Reset button for date filter
                    if (startDate != null || endDate != null) {
                        IconButton(
                            onClick = {
                                startDate = null
                                endDate = null
                                selectedTimePeriod = "Periodo Completo"
                            },
                            modifier = Modifier.background(ParadiseRed.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar Filtro", tint = ParadiseRed)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Filter by employee responsible name
            OutlinedTextField(
                value = responsibleFilter,
                onValueChange = { responsibleFilter = it },
                label = { Text("Filtrar por Responsable...") },
                placeholder = { Text("Ej. María Gómez") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Person Filter") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ParadiseMediumTeal,
                    unfocusedBorderColor = ParadiseGrayDark.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Consumption log table view
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Consumos Registrados (${filteredLogs.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ParadiseCoal
                )
                Text(
                    text = "Devuelve stock al deshacer",
                    fontSize = 9.sp,
                    color = ParadiseGrayDark
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron egresos en el filtro.",
                        color = ParadiseGrayDark,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs) { log ->
                        ConsumptionLogItemCard(
                            log = log,
                            onUndo = { onUndoLog(log) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConsumptionLogItemCard(log: ConsumptionLog, onUndo: () -> Unit) {
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(log.timestamp))
    
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Small circular badge representation next to the responsible user
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(ParadiseTealAccent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = log.responsibleName.take(1).uppercase(),
                            color = ParadiseMediumTeal,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = log.productName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ParadiseCoal
                        )
                        Text(
                            text = "Por: ${log.responsibleName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ParadiseGrayDark
                        )
                    }
                }

                // Action to Undo
                IconButton(onClick = { onUndo() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Deshacer Consumo (Devolver Stock)",
                        tint = ParadiseRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = ParadiseGrayLight, thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Fecha",
                            tint = ParadiseGrayDark,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = date,
                            fontSize = 10.sp,
                            color = ParadiseGrayDark
                        )
                    }
                    if (log.notes.isNotEmpty()) {
                        Text(
                            text = "Nota: ${log.notes}",
                            fontSize = 10.sp,
                            color = ParadiseCoal.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "Se retiró: ",
                        fontSize = 10.sp,
                        color = ParadiseGrayDark
                    )
                    Text(
                        text = "- ${log.quantity} ${log.productUnit}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ParadiseRed
                    )
                }
            }
        }
    }
}

@Composable
fun ManagementTab(
    products: List<Product>,
    workers: List<com.example.data.Worker>,
    onAddProduct: (String, String, Double, Double, String, String, Double) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onAddWorker: (String, String) -> Unit,
    onDeleteWorker: (com.example.data.Worker) -> Unit
) {
    val context = LocalContext.current
    
    // Form States for Products
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Limpieza") }
    var stockText by remember { mutableStateOf("") }
    var minText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Unidades") }
    var costText by remember { mutableStateOf("") }
    var imageUrlText by remember { mutableStateOf("") }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val file = File(context.filesDir, "prod_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                imageUrlText = file.absolutePath
                Toast.makeText(context, "¡Imagen cargada con éxito! 📸", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error al cargar la imagen local: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    val units = listOf("Unidades", "Kilos", "Litros", "Paquetes", "Galones")
    val categories = listOf("Limpieza", "Higiene", "Blanquería", "Alimentos", "Otros")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form to add products
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REGISTRAR NUEVO INSUMO",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ParadiseMediumTeal,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Agrega nuevos insumos para el hotel para que tus responsables puedan descontarlos.",
                        fontSize = 10.sp,
                        color = ParadiseGrayDark,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Form Inputs
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del Producto") },
                        placeholder = { Text("Ej. Cif limpiador abrillantador") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Category selection
                        var catExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = catExpanded,
                                onExpandedChange = { catExpanded = !catExpanded }
                            ) {
                                OutlinedTextField(
                                    readOnly = true,
                                    value = category,
                                    onValueChange = {},
                                    label = { Text("Categoría") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                                )
                                ExposedDropdownMenu(
                                    expanded = catExpanded,
                                    onDismissRequest = { catExpanded = false }
                                ) {
                                    categories.forEach { selection ->
                                        DropdownMenuItem(
                                            text = { Text(selection) },
                                            onClick = {
                                                category = selection
                                                catExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Unit selection
                        var unitExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = unitExpanded,
                                onExpandedChange = { unitExpanded = !unitExpanded }
                            ) {
                                OutlinedTextField(
                                    readOnly = true,
                                    value = unit,
                                    onValueChange = {},
                                    label = { Text("Unidad Medida") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                                )
                                ExposedDropdownMenu(
                                    expanded = unitExpanded,
                                    onDismissRequest = { unitExpanded = false }
                                ) {
                                    units.forEach { selection ->
                                        DropdownMenuItem(
                                            text = { Text(selection) },
                                            onClick = {
                                                unit = selection
                                                unitExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Current Stock
                        OutlinedTextField(
                            value = stockText,
                            onValueChange = { stockText = it },
                            label = { Text("Stock Inicial") },
                            placeholder = { Text("Ej. 25.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                        )

                        // Min Alert
                        OutlinedTextField(
                            value = minText,
                            onValueChange = { minText = it },
                            label = { Text("Alerta Mín.") },
                            placeholder = { Text("Ej. 5.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Unit Cost
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Costo Unitario (S/.)") },
                        placeholder = { Text("Ej. 14.50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Image URL Input Field
                    OutlinedTextField(
                        value = imageUrlText,
                        onValueChange = { imageUrlText = it },
                        label = { Text("URL de la Imagen o Ruta del Archivo (Opcional)") },
                        placeholder = { Text("Copiar URL de internet, subir foto o elegir preestablecido 👇") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Local image picker widget with live thumbnail feedback
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ParadiseGrayLight.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = ParadiseMediumTeal),
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Subir foto",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Subir de Galería o Cámara 📸", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        if (imageUrlText.isNotEmpty()) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AsyncImage(
                                    model = imageUrlText,
                                    contentDescription = "Mini Vista Previa",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Text(
                                    text = "¡Foto Cargada!",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ParadiseMediumTeal
                                )
                            }
                        } else {
                            Text(
                                text = "Sin foto local",
                                fontSize = 10.sp,
                                color = ParadiseGrayDark,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Beautiful high-quality Unsplash image preset triggers
                    Text("PÁNFILO - IMÁGENES SUGERIDAS:", fontSize = 10.sp, color = ParadiseGrayDark, fontWeight = FontWeight.ExtraBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "🧹 Limpieza" to "https://images.unsplash.com/photo-1583947215259-38e31be8751f?w=400",
                            "🧼 Jabón/Higiene" to "https://images.unsplash.com/photo-1607006342411-1a90e37130a1?w=400",
                            "🛏️ Sábanas/Blancos" to "https://images.unsplash.com/photo-1540518614846-7eded433c457?w=400",
                            "☕ Alimentos/Café" to "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=400",
                            "🏨 General Hotel" to "https://images.unsplash.com/photo-1566073771259-6a8506099945?w=400"
                        ).forEach { (label, url) ->
                            val isSelected = imageUrlText == url
                            FilterChip(
                                selected = isSelected,
                                onClick = { imageUrlText = url },
                                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ParadiseMediumTeal,
                                    selectedLabelColor = ParadiseIvory,
                                    containerColor = ParadiseGrayLight,
                                    labelColor = ParadiseCoal
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            val parsedStock = stockText.toDoubleOrNull() ?: 0.0
                            val parsedMin = minText.toDoubleOrNull() ?: 0.0
                            val parsedCost = costText.toDoubleOrNull() ?: 0.0
                            
                            if (name.trim().isNotEmpty() && parsedStock >= 0 && parsedMin >= 0) {
                                onAddProduct(
                                    name.trim(),
                                    category,
                                    parsedStock,
                                    parsedMin,
                                    unit,
                                    imageUrlText.ifEmpty { category.lowercase(Locale.ROOT) },
                                    parsedCost
                                )
                                Toast.makeText(context, "Insumo guardado correctamente", Toast.LENGTH_SHORT).show()
                                
                                // Reset form
                                name = ""
                                stockText = ""
                                minText = ""
                                costText = ""
                                imageUrlText = ""
                            } else {
                                Toast.makeText(context, "Por favor complete los campos obligatorios", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ParadiseMediumTeal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_new_product_button")
                    ) {
                        Text("Registrar Insumo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // WORKERS / USERS MANAGEMENT SECTION (Satisfies "donde esta la opcion de admin para registrar los usuarios que tendran acceso")
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REGISTRAR COLABORADOR (ACCESO)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ParadiseMediumTeal,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Registra los usuarios y colaboradores que están autorizados para registrar consumos de insumos en el hotel.",
                        fontSize = 10.sp,
                        color = ParadiseGrayDark,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var workerNameText by remember { mutableStateOf("") }
                    var workerRoleText by remember { mutableStateOf("Personal Limpieza") }
                    val roles = listOf("Personal Limpieza", "Gobernanta", "Recepcionista", "Mantenimiento", "Administrador")

                    OutlinedTextField(
                        value = workerNameText,
                        onValueChange = { workerNameText = it },
                        label = { Text("Nombre del Colaborador") },
                        placeholder = { Text("Ej. Marta Gómez") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    var roleExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = roleExpanded,
                            onExpandedChange = { roleExpanded = !roleExpanded }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = workerRoleText,
                                onValueChange = {},
                                label = { Text("Rol / Puesto") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                            )
                            ExposedDropdownMenu(
                                expanded = roleExpanded,
                                onDismissRequest = { roleExpanded = false }
                            ) {
                                roles.forEach { selection ->
                                    DropdownMenuItem(
                                        text = { Text(selection) },
                                        onClick = {
                                            workerRoleText = selection
                                            roleExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (workerNameText.trim().isNotEmpty()) {
                                onAddWorker(workerNameText.trim(), workerRoleText)
                                Toast.makeText(context, "Colaborador registrado correctamente", Toast.LENGTH_SHORT).show()
                                workerNameText = ""
                            } else {
                                Toast.makeText(context, "Indica el nombre del colaborador", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ParadiseMediumTeal),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Registrar Colaborador", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List and delete workers
        item {
            Text(
                text = "COLABORADORES CON ACCESO (${workers.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ParadiseCoal,
                letterSpacing = 0.5.sp
            )
        }

        items(workers) { worker ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp).background(ParadiseTealAccent.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(worker.name.take(1).uppercase(), color = ParadiseMediumTeal, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(worker.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ParadiseCoal)
                            Text(worker.role, fontSize = 11.sp, color = ParadiseGrayDark)
                        }
                    }

                    IconButton(onClick = { onDeleteWorker(worker) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar de base de datos",
                            tint = ParadiseRed
                        )
                    }
                }
            }
        }

        // List and delete products
        item {
            Text(
                text = "ADMINISTRAR PRODUCTOS EXISTENTES (${products.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ParadiseCoal,
                letterSpacing = 0.5.sp
            )
        }

        items(products) { product ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ParadiseCoal
                        )
                        Text(
                            text = "Stock: ${product.currentStock} ${product.unit} | Alerta en: ${product.minStockAlert}",
                            fontSize = 11.sp,
                            color = ParadiseGrayDark
                        )
                    }
                    
                    IconButton(onClick = { onDeleteProduct(product) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar de base de datos",
                            tint = ParadiseRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterDiscountDialog(
    product: Product,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, responsible: String, notes: String) -> Unit
) {
    var quantityText by remember { mutableStateOf("1.0") }
    var notes by remember { mutableStateOf("") }
    
    // Quick responsible selection chips
    var responsibleName by remember { mutableStateOf("María López") }
    val workers = listOf("María López", "Carlos Ruiz", "Ana Torres", "José Pérez", "Conserjería")

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = "Registrar Retiro de Insumo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ParadiseMediumTeal
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "¿Cuánto/as ${product.unit} de '${product.name}' se retiran del almacén?",
                    fontSize = 12.sp,
                    color = ParadiseCoal.copy(alpha = 0.7f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = {
                            val current = quantityText.toDoubleOrNull() ?: 1.0
                            if (current > 1) quantityText = String.format(Locale.US, "%.1f", current - 1)
                        },
                        modifier = Modifier.background(ParadiseGrayLight, CircleShape)
                    ) {
                        Text("-", fontWeight = FontWeight.Bold, color = ParadiseMediumTeal, fontSize = 20.sp)
                    }

                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Cantidad a Retirar") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("discount_quantity_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                    )

                    IconButton(
                        onClick = {
                            val current = quantityText.toDoubleOrNull() ?: 0.0
                            quantityText = String.format(Locale.US, "%.1f", current + 1)
                        },
                        modifier = Modifier.background(ParadiseGrayLight, CircleShape)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold, color = ParadiseMediumTeal, fontSize = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Fast selection of responsible employee
                Text(
                    text = "Responsable del Retiro:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ParadiseCoal
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    workers.forEach { worker ->
                        val isSelected = responsibleName == worker
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) ParadiseMediumTeal else ParadiseGrayLight,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { responsibleName = worker }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = worker,
                                color = if (isSelected) ParadiseIvory else ParadiseCoal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // If they want custom type selection
                OutlinedTextField(
                    value = responsibleName,
                    onValueChange = { responsibleName = it },
                    label = { Text("Nombre Responsable") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discount_responsible_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                )

                // Optional notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones / Destino (Opcional)") },
                    placeholder = { Text("Ej. Habitación 203, Cocina...") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ParadiseMediumTeal)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = quantityText.toDoubleOrNull() ?: 1.0
                    if (q > 0) {
                        onConfirm(q, responsibleName.trim(), notes.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ParadiseMediumTeal)
            ) {
                Text("Confirmar Retiro", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancelar", color = ParadiseGrayDark)
            }
        }
    )
}

@Composable
fun LoginScreen(
    workers: List<com.example.data.Worker>,
    onLoginAdminSuccess: () -> Unit,
    onLoginWorkerSuccess: (com.example.data.Worker) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var expandedWorkerDropdown by remember { mutableStateOf(false) }
    var selectedWorkerForLogin by remember { mutableStateOf<com.example.data.Worker?>(null) }
    
    // Set first worker as default if available
    LaunchedEffect(workers) {
        if (selectedWorkerForLogin == null && workers.isNotEmpty()) {
            selectedWorkerForLogin = workers.first()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ParadiseDeepTeal, ParadiseMediumTeal.copy(alpha = 0.8f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Overlay some decorative background waves or geometric shapes for atmosphere
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = android.graphics.Path()
            path.moveTo(0f, size.height * 0.7f)
            path.cubicTo(
                size.width * 0.3f, size.height * 0.65f,
                size.width * 0.7f, size.height * 0.85f,
                size.width, size.height * 0.75f
            )
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()
            drawPath(
                path = path.asComposePath(),
                color = ParadiseTealAccent.copy(alpha = 0.08f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Elegant Visual Icon / Header
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(ParadiseTealAccent.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Hotel Logo",
                    tint = ParadiseTealAccent,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "EL PARAÍSO DE BARRANCA",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = ParadiseIvory,
                letterSpacing = 2.sp
            )
            
            Text(
                text = "Sistema de Gestión e Inventarios de Insumos",
                style = MaterialTheme.typography.bodySmall,
                color = ParadiseTealAccent,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Dynamic error card
            if (errorMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ParadiseRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // --- Card 1: ADMINISTRATIVE ACCESS LOGIN (admin / admin) ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "ACCESO ADMINISTRATIVO (ADMIN)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ParadiseMediumTeal,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { 
                            username = it
                            errorMessage = ""
                        },
                        label = { Text("Usuario admin") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User info") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ParadiseMediumTeal,
                            unfocusedBorderColor = ParadiseGrayDark.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            errorMessage = ""
                        },
                        label = { Text("Contraseña (admin)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password info") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ParadiseMediumTeal,
                            unfocusedBorderColor = ParadiseGrayDark.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (username.trim() == "admin" && password == "admin") {
                                onLoginAdminSuccess()
                            } else {
                                errorMessage = "Credenciales incorrectas. Para administración escribe 'admin' y clave 'admin'."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ParadiseMediumTeal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Ingresar como Administrador 🔑", fontWeight = FontWeight.Bold, color = ParadiseIvory)
                    }
                }
            }

            // --- Card 2: STAFF QUICK ACCESS (EMPLOYEE SHIFT ON-TURNO) ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "ACCESO COLABORADOR DE TURNO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ParadiseGrayDark,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = "Selecciona tu nombre de la lista para registrar consumos hoy de forma rápida.",
                        fontSize = 10.sp,
                        color = ParadiseGrayDark.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (workers.isEmpty()) {
                        Text(
                            text = "No hay colaboradores registrados. Pide al administrador ingresar y registrarlos.",
                            fontSize = 11.sp,
                            color = ParadiseRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        // Dropdown selection of registered employees
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ExposedDropdownMenuBox(
                                expanded = expandedWorkerDropdown,
                                onExpandedChange = { expandedWorkerDropdown = !expandedWorkerDropdown }
                            ) {
                                OutlinedTextField(
                                    readOnly = true,
                                    value = selectedWorkerForLogin?.name ?: "Seleccione...",
                                    onValueChange = {},
                                    label = { Text("Colaborador Activo") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedWorkerDropdown) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ParadiseMediumTeal,
                                        unfocusedBorderColor = ParadiseGrayDark.copy(alpha = 0.5f)
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedWorkerDropdown,
                                    onDismissRequest = { expandedWorkerDropdown = false }
                                ) {
                                    workers.forEach { worker ->
                                        DropdownMenuItem(
                                            text = { Text(worker.name, fontWeight = FontWeight.SemiBold) },
                                            onClick = {
                                                selectedWorkerForLogin = worker
                                                expandedWorkerDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                selectedWorkerForLogin?.let {
                                    onLoginWorkerSuccess(it)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF625B71)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text("Iniciar Turno 👉", fontWeight = FontWeight.Bold, color = ParadiseIvory)
                        }
                    }
                }
            }
        }
    }
}

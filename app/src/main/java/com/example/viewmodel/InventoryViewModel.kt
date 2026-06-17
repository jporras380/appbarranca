package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.ConsumptionLog
import com.example.data.InventoryRepository
import com.example.data.Product
import com.example.data.Worker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            "paraiso_inventory_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    val repository: InventoryRepository by lazy {
        InventoryRepository(database.inventoryDao())
    }

    // List of products
    val products: StateFlow<List<Product>> by lazy {
        repository.allProducts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // List of consumption logs
    val logs: StateFlow<List<ConsumptionLog>> by lazy {
        repository.allLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // List of Workers (Users)
    val workers: StateFlow<List<Worker>> by lazy {
        repository.allWorkers.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // Seed data on startup
    init {
        viewModelScope.launch {
            // Seed products
            val existingProducts = repository.allProducts.first()
            if (existingProducts.isEmpty()) {
                val seed = listOf(
                    Product(name = "Cif Crema Limpiador", category = "Limpieza", currentStock = 12.0, minStockAlert = 3.0, unit = "Unidades", iconName = "https://images.unsplash.com/photo-1583947215259-38e31be8751f?w=200", unitCost = 9.80),
                    Product(name = "Papel Higiénico Premium", category = "Higiene", currentStock = 140.0, minStockAlert = 30.0, unit = "Unidades", iconName = "https://images.unsplash.com/photo-1607006342411-1a90e37130a1?w=200", unitCost = 1.20),
                    Product(name = "Detergente Industrial", category = "Limpieza", currentStock = 30.0, minStockAlert = 10.0, unit = "Kilos", iconName = "cleaning", unitCost = 7.50),
                    Product(name = "Toallas Hotel Blancas", category = "Blanquería", currentStock = 50.0, minStockAlert = 15.0, unit = "Unidades", iconName = "https://images.unsplash.com/photo-1540518614846-7eded433c457?w=200", unitCost = 28.00),
                    Product(name = "Shampoo Individual 20ml", category = "Higiene", currentStock = 250.0, minStockAlert = 50.0, unit = "Unidades", iconName = "hygiene", unitCost = 0.40),
                    Product(name = "Jabón de Tocador Mini", category = "Higiene", currentStock = 180.0, minStockAlert = 40.0, unit = "Unidades", iconName = "hygiene", unitCost = 0.35),
                    Product(name = "Café Selecto Molido", category = "Alimentos", currentStock = 8.5, minStockAlert = 2.0, unit = "Kilos", iconName = "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=200", unitCost = 19.50),
                    Product(name = "Limpiador Piso Pino", category = "Limpieza", currentStock = 5.0, minStockAlert = 2.0, unit = "Litros", iconName = "cleaning", unitCost = 15.00)
                )
                for (p in seed) {
                    repository.insertProduct(p)
                }
            }

            // Seed workers
            val existingWorkers = repository.allWorkers.first()
            if (existingWorkers.isEmpty()) {
                val workerSeed = listOf(
                    Worker(name = "Marta Gómez", role = "Gobernanta / Limpieza"),
                    Worker(name = "Juan Díaz", role = "Recepcionista"),
                    Worker(name = "Carlos Ramos", role = "Mantenimiento"),
                    Worker(name = "Gaby Flores", role = "Administradora")
                )
                for (w in workerSeed) {
                    repository.insertWorker(w)
                }
            }
        }
    }

    // State for selected product for consumption action
    private val _selectedProduct = MutableStateFlow<Product?>(null)
    val selectedProduct: StateFlow<Product?> = _selectedProduct.asStateFlow()

    fun selectProduct(product: Product?) {
        _selectedProduct.value = product
    }

    // Register a consumption discount
    fun registerConsumption(
        product: Product,
        quantity: Double,
        responsible: String,
        notes: String = "",
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.registerConsumption(product, quantity, responsible, notes)
            onComplete(success)
        }
    }

    // Undo a consumption record (restores stock)
    fun undoConsumption(log: ConsumptionLog) {
        viewModelScope.launch {
            repository.deleteLogAndRestoreStock(log)
        }
    }

    // Add a new product manually
    fun addProduct(
        name: String,
        category: String,
        stock: Double,
        minAlert: Double,
        unit: String,
        iconName: String,
        cost: Double
    ) {
        viewModelScope.launch {
            val newProduct = Product(
                name = name,
                category = category,
                currentStock = stock,
                minStockAlert = minAlert,
                unit = unit,
                iconName = iconName,
                unitCost = cost
            )
            repository.insertProduct(newProduct)
        }
    }

    // Delete a product entirely
    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }

    // Add worker (user)
    fun addWorker(name: String, role: String) {
        viewModelScope.launch {
            repository.insertWorker(Worker(name = name, role = role))
        }
    }

    // Delete worker
    fun deleteWorker(worker: Worker) {
        viewModelScope.launch {
            repository.deleteWorker(worker)
        }
    }
}


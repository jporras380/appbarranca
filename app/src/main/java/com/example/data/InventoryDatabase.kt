package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String, // e.g., "Limpieza", "Higiene", "Blanquería", "Alimentos"
    val currentStock: Double,
    val minStockAlert: Double,
    val unit: String, // e.g., "Kilos", "Unidades", "Paquetes", "Litros"
    val iconName: String, // e.g., "cleaning", "hygiene", "bedding", "beverage", or any URL / preset
    val unitCost: Double = 0.0 // Cost per unit in Soles
)

@Entity(tableName = "consumption_logs")
data class ConsumptionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: Int,
    val productName: String,
    val productUnit: String,
    val quantity: Double,
    val responsibleName: String,
    val timestamp: Long,
    val notes: String = ""
)

@Entity(tableName = "workers")
data class Worker(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val role: String // e.g., "Administrador", "Personal Limpieza", "Recepción"
)

@Dao
interface InventoryDao {
    // Products
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Int): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    // Consumption Logs
    @Query("SELECT * FROM consumption_logs ORDER BY timestamp DESC")
    fun getAllConsumptionLogs(): Flow<List<ConsumptionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumptionLog(log: ConsumptionLog)

    @Query("DELETE FROM consumption_logs WHERE id = :id")
    suspend fun deleteConsumptionLogById(id: Int)

    // Workers / Users
    @Query("SELECT * FROM workers ORDER BY name ASC")
    fun getAllWorkers(): Flow<List<Worker>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorker(worker: Worker)

    @Delete
    suspend fun deleteWorker(worker: Worker)
}

@Database(entities = [Product::class, ConsumptionLog::class, Worker::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
}


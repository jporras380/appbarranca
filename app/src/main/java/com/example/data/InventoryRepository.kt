package com.example.data

import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val dao: InventoryDao) {
    val allProducts: Flow<List<Product>> = dao.getAllProducts()
    val allLogs: Flow<List<ConsumptionLog>> = dao.getAllConsumptionLogs()
    val allWorkers: Flow<List<Worker>> = dao.getAllWorkers()

    suspend fun insertProduct(product: Product) = dao.insertProduct(product)
    suspend fun updateProduct(product: Product) = dao.updateProduct(product)
    suspend fun deleteProduct(product: Product) = dao.deleteProduct(product)

    suspend fun registerConsumption(
        product: Product,
        quantity: Double,
        responsible: String,
        notes: String = ""
    ): Boolean {
        if (product.currentStock < quantity) return false
        val updatedProduct = product.copy(currentStock = product.currentStock - quantity)
        dao.updateProduct(updatedProduct)
        
        val log = ConsumptionLog(
            productId = product.id,
            productName = product.name,
            productUnit = product.unit,
            quantity = quantity,
            responsibleName = responsible,
            timestamp = System.currentTimeMillis(),
            notes = notes
        )
        dao.insertConsumptionLog(log)
        return true
    }

    suspend fun deleteLogAndRestoreStock(log: ConsumptionLog) {
        val product = dao.getProductById(log.productId)
        if (product != null) {
            val restoredProduct = product.copy(currentStock = product.currentStock + log.quantity)
            dao.updateProduct(restoredProduct)
        }
        dao.deleteConsumptionLogById(log.id)
    }

    // Worker methods
    suspend fun insertWorker(worker: Worker) = dao.insertWorker(worker)
    suspend fun deleteWorker(worker: Worker) = dao.deleteWorker(worker)
}


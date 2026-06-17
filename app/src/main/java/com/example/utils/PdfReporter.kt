package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.ConsumptionLog
import com.example.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfReporter {
    private suspend fun loadBitmap(context: Context, iconName: String): Bitmap? = withContext(Dispatchers.IO) {
        if (iconName.trim().isEmpty()) return@withContext null
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(iconName)
                .allowHardware(false) // Must be software bitmap to draw to Canvas
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun generateAndShareReport(
        context: Context,
        logs: List<ConsumptionLog>,
        products: List<Product>,
        startDateStr: String,
        endDateStr: String
    ): File? {
        val pdfDocument = PdfDocument()
        
        // A4 page dimensions in points: 595 x 842
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        // Paint configurations
        val titlePaint = Paint().apply {
            color = Color.rgb(31, 58, 105) // Corporate Deep Navy Blue
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        val subtitlePaint = Paint().apply {
            color = Color.rgb(100, 110, 120)
            textSize = 9f
            isAntiAlias = true
        }
        
        val sectionPaint = Paint().apply {
            color = Color.rgb(44, 62, 80)
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8f
            isAntiAlias = true
        }
        
        val boldTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val headerBackgroundPaint = Paint().apply {
            color = Color.rgb(235, 240, 245)
            style = Paint.Style.FILL
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(210, 215, 225)
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        
        val lowStockPaint = Paint().apply {
            color = Color.rgb(219, 68, 85) // Crimson Red for warnings
            textSize = 8f
            isFakeBoldText = true
            isAntiAlias = true
        }

        var currentY = 45f
        
        // Header Decoration (Hotel Branding)
        val brandBarPaint = Paint().apply {
            color = Color.rgb(31, 58, 105)
            style = Paint.Style.FILL
        }
        canvas.drawRect(40f, currentY - 15f, 555f, currentY - 10f, brandBarPaint)
        
        // Draw Header Titles
        canvas.drawText("HOTEL APART EL PARAÍSO DE BARRANCA", 40f, currentY + 10f, titlePaint)
        currentY += 24f
        canvas.drawText("Sistema de Gestión de Insumos - Reporte de Consumo y Stock", 40f, currentY, subtitlePaint)
        currentY += 12f
        canvas.drawText("Rango: $startDateStr - $endDateStr | Generado: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, currentY, subtitlePaint)
        
        currentY += 28f
        
        // --- SECTION 1: CONSUMPTION LOGS ---
        canvas.drawText("1. HISTORIAL DE CONSUMOS / EGRESOS DEL PERÍODO", 40f, currentY, sectionPaint)
        currentY += 14f
        
        // Table Header
        canvas.drawRect(40f, currentY, 555f, currentY + 16f, headerBackgroundPaint)
        canvas.drawText("Insumo", 58f, currentY + 11f, boldTextPaint)
        canvas.drawText("Cant.", 180f, currentY + 11f, boldTextPaint)
        canvas.drawText("Unidad", 220f, currentY + 11f, boldTextPaint)
        canvas.drawText("Responsable", 285f, currentY + 11f, boldTextPaint)
        canvas.drawText("Fecha", 410f, currentY + 11f, boldTextPaint)
        canvas.drawText("Costo Est.", 485f, currentY + 11f, boldTextPaint)
        
        currentY += 16f
        
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        var totalSpent = 0.0
        
        if (logs.isEmpty()) {
            canvas.drawText("No se registraron consumos en este período.", 58f, currentY + 12f, textPaint)
            canvas.drawLine(40f, currentY + 16f, 555f, currentY + 16f, borderPaint)
            currentY += 24f
        } else {
            for (log in logs) {
                // Try to load product image first!
                val matchedProduct = products.find { it.id == log.productId }
                val bitmap = matchedProduct?.let { loadBitmap(context, it.iconName) }
                
                if (bitmap != null) {
                    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                    val destRect = RectF(43f, currentY + 3f, 53f, currentY + 13f)
                    canvas.drawBitmap(bitmap, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                } else {
                    // Circular color badge to represent "imagen pequeña al costado"
                    val badgeColor = when (log.productUnit.lowercase()) {
                        "kilos", "kg" -> Color.rgb(230, 126, 34) // Orange
                        "litros", "l" -> Color.rgb(52, 152, 219) // Blue
                        else -> Color.rgb(46, 204, 113) // Green
                    }
                    
                    val badgePaint = Paint().apply {
                        color = badgeColor
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawCircle(48f, currentY + 8f, 4.5f, badgePaint)
                }
                
                val shortName = if (log.productName.length > 25) log.productName.substring(0, 22) + "..." else log.productName
                canvas.drawText(shortName, 58f, currentY + 11f, textPaint)
                canvas.drawText(String.format(Locale.US, "%.1f", log.quantity), 180f, currentY + 11f, textPaint)
                canvas.drawText(log.productUnit, 220f, currentY + 11f, textPaint)
                
                val shortResp = if (log.responsibleName.length > 20) log.responsibleName.substring(0, 18) + "..." else log.responsibleName
                canvas.drawText(shortResp, 285f, currentY + 11f, textPaint)
                canvas.drawText(sdf.format(Date(log.timestamp)), 410f, currentY + 11f, textPaint)
                
                val cost = (matchedProduct?.unitCost ?: 0.0) * log.quantity
                totalSpent += cost
                canvas.drawText(String.format(Locale.US, "S/. %.2f", cost), 485f, currentY + 11f, textPaint)
                
                canvas.drawLine(40f, currentY + 15f, 555f, currentY + 15f, borderPaint)
                currentY += 16f
                
                // Keep layout safe within limits
                if (currentY > 440f) {
                    canvas.drawText("... lista parcial, ver el historial completo en el app ...", 58f, currentY + 11f, subtitlePaint)
                    currentY += 16f
                    break
                }
            }
            
            // Subtotal row
            canvas.drawText(String.format(Locale.US, "Gasto Total en Insumos en este Período: S/. %.2f", totalSpent), 310f, currentY + 10f, boldTextPaint)
            currentY += 28f
        }
        
        // --- SECTION 2: INVENTORY & REORDER SUGGESTIONS ---
        canvas.drawText("2. ESTADO ACTUAL DE INVENTARIO Y REPOSICIÓN RECOMENDADA", 40f, currentY, sectionPaint)
        currentY += 14f
        
        // Table Header
        canvas.drawRect(40f, currentY, 555f, currentY + 16f, headerBackgroundPaint)
        canvas.drawText("Insumo", 58f, currentY + 11f, boldTextPaint)
        canvas.drawText("Categoría", 185f, currentY + 11f, boldTextPaint)
        canvas.drawText("Stock Actual", 285f, currentY + 11f, boldTextPaint)
        canvas.drawText("Stock Mínimo", 385f, currentY + 11f, boldTextPaint)
        canvas.drawText("Estado de Alerta", 465f, currentY + 11f, boldTextPaint)
        
        currentY += 16f
        
        for (prod in products) {
            val isLow = prod.currentStock <= prod.minStockAlert
            
            // Try to load product image first!
            val bitmap = loadBitmap(context, prod.iconName)
            if (bitmap != null) {
                val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                val destRect = RectF(43f, currentY + 3f, 53f, currentY + 13f)
                canvas.drawBitmap(bitmap, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                // Mini status indicator circle
                val indicatorPaint = Paint().apply {
                    color = if (isLow) Color.rgb(231, 76, 60) else Color.rgb(46, 204, 113)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(48f, currentY + 8f, 4.5f, indicatorPaint)
            }
            
            val shortName = if (prod.name.length > 25) prod.name.substring(0, 22) + "..." else prod.name
            canvas.drawText(shortName, 58f, currentY + 11f, if (isLow) lowStockPaint else textPaint)
            canvas.drawText(prod.category, 185f, currentY + 11f, textPaint)
            canvas.drawText(String.format(Locale.US, "%.1f %s", prod.currentStock, prod.unit), 285f, currentY + 11f, if (isLow) lowStockPaint else textPaint)
            canvas.drawText(String.format(Locale.US, "%.1f %s", prod.minStockAlert, prod.unit), 385f, currentY + 11f, textPaint)
            
            val statusStr = if (isLow) "COMPRA CRÍTICA" else "Suficiente"
            canvas.drawText(statusStr, 465f, currentY + 11f, if (isLow) lowStockPaint else textPaint)
            
            canvas.drawLine(40f, currentY + 15f, 555f, currentY + 15f, borderPaint)
            currentY += 16f
            
            if (currentY > 750f) {
                canvas.drawText("... lista continua en el panel de inventario ...", 58f, currentY + 11f, subtitlePaint)
                break
            }
        }
        
        // Page Footer
        currentY = 780f
        canvas.drawLine(40f, currentY, 555f, currentY, borderPaint)
        canvas.drawText("Hotel Apart El Paraíso de Barranca - Gestión Administrativa Interna", 40f, currentY + 15f, subtitlePaint)
        
        val dateAndLoc = "Barranca, Lima, Perú - Generado el " + SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        canvas.drawText(dateAndLoc, 350f, currentY + 15f, subtitlePaint)
        
        pdfDocument.finishPage(page)
        
        // Save the file in the cache directory
        val fileName = "Reporte_Paraiso_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)
        
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            return null
        }
    }
}

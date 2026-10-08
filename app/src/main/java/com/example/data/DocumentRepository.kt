package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.PaperSize
import com.example.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DocumentRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("scanned_docs_pref", Context.MODE_PRIVATE)

    private val _documents = MutableStateFlow<List<ScannedDocument>>(emptyList())
    val documents: StateFlow<List<ScannedDocument>> = _documents.asStateFlow()

    suspend fun loadDocuments() = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString("docs_json", "[]") ?: "[]"
        val list = mutableListOf<ScannedDocument>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val filePath = obj.getString("filePath")
                val file = File(filePath)
                // Only keep documents that still exist on storage
                if (file.exists()) {
                    val paperSizeStr = obj.optString("paperSize", "A4")
                    val paperSize = try {
                        PaperSize.valueOf(paperSizeStr)
                    } catch (e: Exception) {
                        PaperSize.A4
                    }
                    val doc = ScannedDocument(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        filePath = filePath,
                        fileSizeBytes = file.length(),
                        createdAtMillis = obj.optLong("createdAtMillis", file.lastModified()),
                        paperSize = paperSize,
                        pageCount = obj.optInt("pageCount", 1),
                        thumbnailPath = obj.optString("thumbnailPath").takeIf { it.isNotBlank() }
                    )
                    list.add(doc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _documents.value = list.sortedByDescending { it.createdAtMillis }
    }

    suspend fun addDocument(doc: ScannedDocument) = withContext(Dispatchers.IO) {
        val current = _documents.value.toMutableList()
        current.removeAll { it.id == doc.id || it.filePath == doc.filePath }
        current.add(0, doc)
        saveList(current)
        _documents.value = current
    }

    suspend fun deleteDocument(doc: ScannedDocument) = withContext(Dispatchers.IO) {
        try {
            File(doc.filePath).delete()
            doc.thumbnailPath?.let { File(it).delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val updated = _documents.value.filter { it.id != doc.id }
        saveList(updated)
        _documents.value = updated
    }

    private fun saveList(list: List<ScannedDocument>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("filePath", item.filePath)
                put("createdAtMillis", item.createdAtMillis)
                put("paperSize", item.paperSize.name)
                put("pageCount", item.pageCount)
                put("thumbnailPath", item.thumbnailPath ?: "")
            }
            array.put(obj)
        }
        prefs.edit().putString("docs_json", array.toString()).apply()
    }
}

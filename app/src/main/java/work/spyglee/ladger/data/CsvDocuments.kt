package work.spyglee.ladger.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.spyglee.ladger.domain.csv.CsvFile
import work.spyglee.ladger.domain.csv.CsvImportException
import work.spyglee.ladger.domain.csv.CsvImporter
import work.spyglee.ladger.domain.csv.CsvText

/** Reads and writes the CSV files the user picks with the system file picker. */
class CsvDocuments(private val resolver: ContentResolver) {
    suspend fun read(uris: List<Uri>): List<CsvImporter.Input> = withContext(Dispatchers.IO) {
        uris.map { uri ->
            val name = displayName(uri)
            val bytes = try {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
            CsvImporter.Input(name, CsvText.decode(bytes ?: throw CsvImportException(CsvImportException.Reason.UNREADABLE, name)))
        }
    }

    /**
     * Writes [files] as UTF-8 into the folder at [treeUri] (from the folder picker). Files with the
     * same name are overwritten, so exporting again into the same folder doesn't leave
     * "expenses (1).csv" copies behind.
     */
    suspend fun writeToFolder(treeUri: Uri, files: List<CsvFile>) = withContext(Dispatchers.IO) {
        val folderId = DocumentsContract.getTreeDocumentId(treeUri)
        val folder = DocumentsContract.buildDocumentUriUsingTree(treeUri, folderId)
        val existing = childrenByName(treeUri, folderId)
        for (file in files) {
            val target = existing[file.fileName]
                ?: DocumentsContract.createDocument(resolver, folder, CSV_MIME_TYPE, file.fileName)
                ?: throw IOException("Couldn't create ${file.fileName}")
            val output = resolver.openOutputStream(target, "wt") ?: throw IOException("Couldn't write ${file.fileName}")
            output.use { it.write(file.text.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun childrenByName(treeUri: Uri, folderId: String): Map<String, Uri> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val result = mutableMapOf<String, Uri>()
        resolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                result[name] = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
            }
        }
        return result
    }

    private fun displayName(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    companion object {
        const val CSV_MIME_TYPE = "text/csv"

        /** What the import picker offers. Some file managers label .csv files as Excel or plain text. */
        val IMPORT_MIME_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel")
    }
}

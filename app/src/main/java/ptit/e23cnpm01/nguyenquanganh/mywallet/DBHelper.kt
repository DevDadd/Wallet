package ptit.e23cnpm01.nguyenquanganh.mywallet

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.FileOutputStream

class DBHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    private val appContext = context.applicationContext

    init {
        copyDatabaseFromAssetsIfNeeded()
        ensureSettingsTableExists()
    }

    override fun onCreate(db: SQLiteDatabase) = Unit

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun getTotalIncomeByDate(date: String): Double = getTotalAmountByType(date, TYPE_INCOME)

    fun getTotalExpenseByDate(date: String): Double = getTotalAmountByType(date, TYPE_EXPENSE)

    fun getTransactionsByDate(date: String): List<WalletTransaction> {
        val sql = """
            SELECT tr.id, tr.date, tr.amount, tr.note, tr.idCategory AS category_id,
                   c.name AS category_name,
                   ty.id AS type_id, ty.name AS type_name
            FROM tblTransaction AS tr
            INNER JOIN tblCategory AS c ON c.id = tr.idCategory
            INNER JOIN tblType AS ty ON ty.id = c.idType
            WHERE tr.date = ?
            ORDER BY tr.id DESC
        """.trimIndent()

        return readableDatabase.rawQuery(sql, arrayOf(date)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        WalletTransaction(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
                            categoryName = cursor.getString(
                                cursor.getColumnIndexOrThrow("category_name")
                            ),
                            amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount")),
                            note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                            categoryId = cursor.getLong(cursor.getColumnIndexOrThrow("category_id")),
                            typeId = cursor.getInt(cursor.getColumnIndexOrThrow("type_id")),
                            typeName = cursor.getString(cursor.getColumnIndexOrThrow("type_name"))
                        )
                    )
                }
            }
        }
    }

    fun getTransactionById(transactionId: Long): WalletTransaction? {
        val sql = """
            SELECT tr.id, tr.date, tr.amount, tr.note, tr.idCategory AS category_id,
                   c.name AS category_name, ty.id AS type_id, ty.name AS type_name
            FROM tblTransaction AS tr
            INNER JOIN tblCategory AS c ON c.id = tr.idCategory
            INNER JOIN tblType AS ty ON ty.id = c.idType
            WHERE tr.id = ?
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(transactionId.toString())).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            WalletTransaction(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
                categoryName = cursor.getString(cursor.getColumnIndexOrThrow("category_name")),
                amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount")),
                note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                categoryId = cursor.getLong(cursor.getColumnIndexOrThrow("category_id")),
                typeId = cursor.getInt(cursor.getColumnIndexOrThrow("type_id")),
                typeName = cursor.getString(cursor.getColumnIndexOrThrow("type_name"))
            )
        }
    }

    fun getCategoriesByType(typeId: Int): List<WalletCategory> =
        queryCategories("WHERE idType = ?", arrayOf(typeId.toString()))

    fun getParentCategories(typeId: Int? = null): List<WalletCategory> =
        if (typeId == null) {
            queryCategories("WHERE idParent IS NULL")
        } else {
            queryCategories("WHERE idParent IS NULL AND idType = ?", arrayOf(typeId.toString()))
        }

    fun getCategoryById(categoryId: Long): WalletCategory? =
        queryCategories("WHERE id = ?", arrayOf(categoryId.toString())).firstOrNull()

    private fun queryCategories(
        whereClause: String,
        args: Array<String>? = null
    ): List<WalletCategory> {
        val sql = """
            SELECT id, name, icon, note, idParent, idType
            FROM tblCategory
            $whereClause
            ORDER BY id
        """.trimIndent()

        return readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val parentColumn = cursor.getColumnIndexOrThrow("idParent")
                    add(
                        WalletCategory(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            iconName = cursor.getString(cursor.getColumnIndexOrThrow("icon")),
                            note = cursor.getStringOrNull("note"),
                            parentId = if (cursor.isNull(parentColumn)) null else cursor.getLong(parentColumn),
                            typeId = cursor.getInt(cursor.getColumnIndexOrThrow("idType"))
                        )
                    )
                }
            }
        }
    }

    fun getCategoryAmountsIncludingChildrenByDate(date: String): Map<Long, Double> {
        val sql = """
            WITH RECURSIVE category_tree(root_id, category_id) AS (
                SELECT id, id FROM tblCategory
                UNION ALL
                SELECT category_tree.root_id, child.id
                FROM category_tree
                INNER JOIN tblCategory AS child ON child.idParent = category_tree.category_id
            )
            SELECT category_tree.root_id AS category_id,
                   COALESCE(SUM(tr.amount), 0) AS total
            FROM category_tree
            LEFT JOIN tblTransaction AS tr
                ON tr.idCategory = category_tree.category_id AND tr.date = ?
            GROUP BY category_tree.root_id
        """.trimIndent()

        return readableDatabase.rawQuery(sql, arrayOf(date)).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getLong(cursor.getColumnIndexOrThrow("category_id")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("total"))
                    )
                }
            }
        }
    }

    @Deprecated("Use getCategoryAmountsIncludingChildrenByDate so parent totals include children.")
    fun getCategoryAmountsByDate(date: String): Map<Long, Double> =
        getCategoryAmountsIncludingChildrenByDate(date)

    fun getCategoryUsageCounts(): Map<Long, Int> {
        val sql = """
            SELECT idCategory, COUNT(*) AS usage_count
            FROM tblTransaction
            GROUP BY idCategory
        """.trimIndent()

        return readableDatabase.rawQuery(sql, null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getLong(cursor.getColumnIndexOrThrow("idCategory")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("usage_count"))
                    )
                }
            }
        }
    }

    fun getParentCategoryIds(categoryId: Long): List<Long> {
        val parentIds = mutableListOf<Long>()
        var currentId = categoryId

        while (true) {
            val parentId = readableDatabase.rawQuery(
                "SELECT idParent FROM tblCategory WHERE id = ?",
                arrayOf(currentId.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
            } ?: break

            parentIds.add(parentId)
            currentId = parentId
        }
        return parentIds
    }

    fun getDailyTotalsBetween(startDate: String, endDate: String): Map<String, DailyTotal> {
        val sql = """
            SELECT tr.date AS date,
                   COALESCE(SUM(CASE WHEN c.idType = ? THEN tr.amount END), 0) AS income,
                   COALESCE(SUM(CASE WHEN c.idType = ? THEN tr.amount END), 0) AS expense
            FROM tblTransaction AS tr
            INNER JOIN tblCategory AS c ON c.id = tr.idCategory
            WHERE tr.date BETWEEN ? AND ?
            GROUP BY tr.date
        """.trimIndent()
        val args = arrayOf(TYPE_INCOME.toString(), TYPE_EXPENSE.toString(), startDate, endDate)

        return readableDatabase.rawQuery(sql, args).use { cursor ->
            val totals = mutableMapOf<String, DailyTotal>()
            while (cursor.moveToNext()) {
                totals[cursor.getString(cursor.getColumnIndexOrThrow("date"))] = DailyTotal(
                    income = cursor.getDouble(cursor.getColumnIndexOrThrow("income")),
                    expense = cursor.getDouble(cursor.getColumnIndexOrThrow("expense"))
                )
            }
            totals
        }
    }

    data class DailyTotal(val income: Double, val expense: Double)

    private fun getTotalAmountByType(date: String, typeId: Int): Double {
        val sql = """
            SELECT COALESCE(SUM(tr.amount), 0) AS total
            FROM tblTransaction AS tr
            INNER JOIN tblCategory AS c ON c.id = tr.idCategory
            WHERE tr.date = ? AND c.idType = ?
        """.trimIndent()

        return readableDatabase.rawQuery(sql, arrayOf(date, typeId.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getDouble(cursor.getColumnIndexOrThrow("total")) else 0.0
        }
    }

    fun addTransaction(
        amount: Double,
        note: String?,
        date: String,
        idCategory: Long
    ): Long = writableDatabase.insertOrThrow(
        "tblTransaction",
        null,
        ContentValues().apply {
            put("date", date)
            put("amount", amount)
            put("note", note)
            put("idCategory", idCategory)
        }
    )

    fun addCategory(
        name: String,
        iconName: String,
        note: String?,
        parentId: Long?,
        typeId: Int
    ): Long = writableDatabase.insertOrThrow(
        "tblCategory",
        null,
        ContentValues().apply {
            put("name", name)
            put("icon", iconName)
            put("note", note)
            if (parentId == null) putNull("idParent") else put("idParent", parentId)
            put("idType", typeId)
        }
    )

    fun updateCategory(
        categoryId: Long,
        name: String,
        iconName: String,
        note: String?,
        parentId: Long?,
        typeId: Int
    ): Int {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val updated = db.update(
                "tblCategory",
                ContentValues().apply {
                    put("name", name)
                    put("icon", iconName)
                    put("note", note)
                    if (parentId == null) putNull("idParent") else put("idParent", parentId)
                    put("idType", typeId)
                },
                "id = ?",
                arrayOf(categoryId.toString())
            )
            if (updated > 0) {
                db.execSQL(
                    """
                    WITH RECURSIVE descendants(id) AS (
                        SELECT id FROM tblCategory WHERE idParent = ?
                        UNION ALL
                        SELECT c.id FROM tblCategory AS c
                        INNER JOIN descendants AS d ON c.idParent = d.id
                    )
                    UPDATE tblCategory SET idType = ? WHERE id IN (SELECT id FROM descendants)
                    """.trimIndent(),
                    arrayOf(categoryId, typeId)
                )
                db.setTransactionSuccessful()
            }
            updated
        } finally {
            db.endTransaction()
        }
    }

    fun updateTransaction(
        transactionId: Long,
        amount: Double,
        note: String?,
        date: String,
        idCategory: Long
    ): Int = writableDatabase.update(
        "tblTransaction",
        ContentValues().apply {
            put("date", date)
            put("amount", amount)
            put("note", note)
            put("idCategory", idCategory)
        },
        "id = ?",
        arrayOf(transactionId.toString())
    )

    fun deleteCategory(categoryId: Long): Int {
        val sql = """
            WITH RECURSIVE category_tree(id) AS (
                SELECT id FROM tblCategory WHERE id = ?
                UNION ALL
                SELECT child.id
                FROM tblCategory AS child
                INNER JOIN category_tree ON child.idParent = category_tree.id
            )
            SELECT id FROM category_tree
        """.trimIndent()

        val db = writableDatabase
        val categoryIds = db.rawQuery(sql, arrayOf(categoryId.toString())).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0).toString()) }
        }
        if (categoryIds.isEmpty()) return 0

        val placeholders = categoryIds.joinToString(",") { "?" }
        val args = categoryIds.toTypedArray()
        db.beginTransaction()
        try {
            db.delete("tblTransaction", "idCategory IN ($placeholders)", args)
            val deletedCategories = db.delete("tblCategory", "id IN ($placeholders)", args)
            db.setTransactionSuccessful()
            return deletedCategories
        } finally {
            db.endTransaction()
        }
    }

    fun deleteTransaction(transactionId: Long): Int = writableDatabase.delete(
        "tblTransaction",
        "id = ?",
        arrayOf(transactionId.toString())
    )

    private fun copyDatabaseFromAssetsIfNeeded() {
        val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
        if (databaseFile.exists() && databaseFile.length() > 0L) return

        databaseFile.parentFile?.mkdirs()
        appContext.assets.open(DATABASE_NAME).use { input ->
            FileOutputStream(databaseFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun ensureSettingsTableExists() {
        writableDatabase.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tblSetting (
                settingKey TEXT PRIMARY KEY,
                settingValue TEXT
            )
            """.trimIndent()
        )
    }

    fun getSetting(key: String): String? =
        readableDatabase.rawQuery(
            "SELECT settingValue FROM tblSetting WHERE settingKey = ?",
            arrayOf(key)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun setSetting(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "tblSetting",
            null,
            ContentValues().apply {
                put("settingKey", key)
                put("settingValue", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }
    fun setDisplayedDate(date: String) = setSetting(SETTING_DISPLAYED_DATE, date)

    fun getHasOpenedCategoryList(): Boolean = getSetting(SETTING_HAS_OPENED_CATEGORY_LIST) == "1"

    fun setHasOpenedCategoryList(value: Boolean) =
        setSetting(SETTING_HAS_OPENED_CATEGORY_LIST, if (value) "1" else "0")

    companion object {
        private const val DATABASE_NAME = "mywallet.db"
        private const val DATABASE_VERSION = 1

        private const val SETTING_DISPLAYED_DATE = "displayed_date"
        private const val SETTING_HAS_OPENED_CATEGORY_LIST = "has_opened_category_list"

        const val TYPE_INCOME = 1
        const val TYPE_EXPENSE = 2
    }
}

private fun android.database.Cursor.getStringOrNull(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

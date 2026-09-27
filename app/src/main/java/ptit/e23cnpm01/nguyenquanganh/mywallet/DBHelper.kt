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
    }

    override fun onCreate(db: SQLiteDatabase) = Unit

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun getTotalIncomeByDate(date: String): Double = getTotalAmountByType(date, TYPE_INCOME)

    fun getTotalExpenseByDate(date: String): Double = getTotalAmountByType(date, TYPE_EXPENSE)

    fun getTotalIncome(date: String): Double = getTotalIncomeByDate(date)

    fun getTotalExpense(date: String): Double = getTotalExpenseByDate(date)

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

    fun getTransactionItemsByDate(date: String): List<WalletTransaction> =
        getTransactionsByDate(date)

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

    fun getCategoriesByType(typeId: Int): List<WalletCategory> {
        val sql = """
            SELECT id, name, icon, idParent, idType
            FROM tblCategory
            WHERE idType = ?
            ORDER BY id
        """.trimIndent()

        return readableDatabase.rawQuery(sql, arrayOf(typeId.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val parentColumn = cursor.getColumnIndexOrThrow("idParent")
                    add(
                        WalletCategory(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            iconName = cursor.getString(cursor.getColumnIndexOrThrow("icon")),
                            parentId = if (cursor.isNull(parentColumn)) null else cursor.getLong(parentColumn),
                            typeId = cursor.getInt(cursor.getColumnIndexOrThrow("idType"))
                        )
                    )
                }
            }
        }
    }

    /**
     * Tổng tiền của từng danh mục, đã gồm toàn bộ danh mục con ở mọi cấp.
     * Tính trong SQLite để số ở mục cha luôn nhất quán với tổng Chi/Thu.
     */
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

    /** Ngày có giao dịch gần nhất, định dạng yyyy-MM-dd. */
    fun getLatestTransactionDate(): String? =
        readableDatabase.rawQuery("SELECT MAX(date) AS latest_date FROM tblTransaction", null)
            .use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }

    /** Các id danh mục cha, từ cha gần nhất đến danh mục gốc. */
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

    companion object {
        private const val DATABASE_NAME = "mywallet.db"
        private const val DATABASE_VERSION = 1

        const val TYPE_INCOME = 1
        const val TYPE_EXPENSE = 2
    }
}

data class WalletTransaction(
    val id: Long,
    val date: String,
    val categoryName: String,
    val amount: Double,
    val note: String?,
    val categoryId: Long,
    val typeId: Int,
    val typeName: String
)

/** Một danh mục có thể là danh mục cha hoặc danh mục con qua [parentId]. */
data class WalletCategory(
    val id: Long,
    val name: String,
    val iconName: String,
    val parentId: Long?,
    val typeId: Int
)

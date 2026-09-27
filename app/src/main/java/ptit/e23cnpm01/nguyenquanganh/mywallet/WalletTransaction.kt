package ptit.e23cnpm01.nguyenquanganh.mywallet

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

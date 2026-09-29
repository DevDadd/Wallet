package ptit.e23cnpm01.nguyenquanganh.mywallet

data class WalletCategory(
    val id: Long,
    val name: String,
    val iconName: String,
    val note: String?,
    val parentId: Long?,
    val typeId: Int
)

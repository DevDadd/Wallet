package ptit.e23cnpm01.nguyenquanganh.mywallet

/** Một danh mục có thể là danh mục cha hoặc danh mục con qua [parentId]. */
data class WalletCategory(
    val id: Long,
    val name: String,
    val iconName: String,
    val note: String?,
    val parentId: Long?,
    val typeId: Int
)

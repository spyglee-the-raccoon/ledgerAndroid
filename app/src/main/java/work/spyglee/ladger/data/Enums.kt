package work.spyglee.ladger.data

/** Stored by [raw] value, matching the iOS app's raw values so data stays portable. */
enum class TransactionKind(val raw: String) {
    INCOME("income"),
    EXPENSE("expense"),
    TRANSFER("transfer");

    companion object {
        fun fromRaw(raw: String): TransactionKind = entries.first { it.raw == raw }
    }
}

enum class CategoryKind(val raw: String) {
    INCOME("income"),
    EXPENSE("expense");

    companion object {
        fun fromRaw(raw: String): CategoryKind = entries.first { it.raw == raw }
    }
}

/** The 14 colors a category or source can use. [PRIMARY] follows the theme's foreground color. */
enum class CategoryColor(val raw: String) {
    PRIMARY("primary"),
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    GREEN("green"),
    MINT("mint"),
    TEAL("teal"),
    CYAN("cyan"),
    BLUE("blue"),
    INDIGO("indigo"),
    PURPLE("purple"),
    PINK("pink"),
    BROWN("brown"),
    GRAY("gray");

    companion object {
        fun fromRaw(raw: String): CategoryColor = entries.firstOrNull { it.raw == raw } ?: PRIMARY
    }
}

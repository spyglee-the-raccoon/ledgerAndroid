package work.spyglee.ladger.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test
import work.spyglee.ladger.data.Source

class BalancesTest {
    private val cash = Source(id = 1, name = "Cash", initValue = BigDecimal("100"))
    private val bank = Source(id = 2, name = "Bank", initValue = BigDecimal("50"))

    private val transactions = listOf(
        expense("20", sourceId = 1),
        income("200", destinationId = 2),
        transfer("30", sourceId = 1, destinationId = 2),
        expense("5"),
        income("10"),
    )

    @Test
    fun `total adds starting values and incomes, subtracts expenses, ignores transfers`() {
        assertAmount("335", Balances.total(listOf(cash, bank), transactions))
    }

    @Test
    fun `source balance counts transfers in both directions`() {
        assertAmount("50", Balances.forSource(cash.id, cash.initValue, transactions))
        assertAmount("280", Balances.forSource(bank.id, bank.initValue, transactions))
    }

    @Test
    fun `null source collects transactions without a source or destination`() {
        assertAmount("5", Balances.forSource(null, BigDecimal.ZERO, transactions))
    }

    @Test
    fun `per-source balances add up to the total`() {
        val sum = Balances.forSource(cash.id, cash.initValue, transactions) +
            Balances.forSource(bank.id, bank.initValue, transactions) +
            Balances.forSource(null, BigDecimal.ZERO, transactions)
        assertAmount(Balances.total(listOf(cash, bank), transactions).toPlainString(), sum)
    }

    @Test
    fun `net is incomes minus expenses`() {
        assertAmount("185", Balances.net(transactions))
    }

    @Test
    fun `unassigned means missing the source or destination the kind needs`() {
        assertEquals(listOf(false, false, false, true, true), transactions.map(Balances::isUnassigned))
        assertEquals(true, Balances.isUnassigned(transfer("1", sourceId = 1, destinationId = null)))
    }
}

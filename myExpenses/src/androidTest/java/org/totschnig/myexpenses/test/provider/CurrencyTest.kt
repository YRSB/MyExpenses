package org.totschnig.myexpenses.test.provider

import junit.framework.TestCase
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.provider.AccountInfo
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.insert
import org.totschnig.myexpenses.testutils.BaseDbTest
import org.totschnig.myexpenses.testutils.CurrencyInfo
import java.lang.IllegalArgumentException

class CurrencyTest : BaseDbTest() {
    private val testCurrency = CurrencyInfo("Bitcoin", "BTC")
    private val testAccount = AccountInfo("Account 0", AccountType.CASH, 0, testCurrency.code)

    fun testShouldNotDeleteFrameworkCurrency() {
        try {
            getMockContentResolver().delete(
                TransactionProvider.CURRENCIES_URI.buildUpon().appendPath("EUR").build(), null, null
            )
            fail("Expected deletion of framework currency to fail")
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    fun testShouldDeleteUnUsedCurrency() {
        mDb
            .insert(
                DatabaseConstants.TABLE_CURRENCIES,
                testCurrency.contentValues
            )
        val result = getMockContentResolver().delete(
            TransactionProvider.CURRENCIES_URI.buildUpon().appendPath(testCurrency.code).build(),
            null,
            null
        )
        TestCase.assertEquals(1, result)
    }

    fun testShouldNotDeleteUsedCurrency() {
        mDb
            .insert(
                DatabaseConstants.TABLE_CURRENCIES,
                testCurrency.contentValues
            )
        mDb
            .insert(
                DatabaseConstants.TABLE_ACCOUNTS,
                testAccount.contentValues
            )
        val result = getMockContentResolver().delete(
            TransactionProvider.CURRENCIES_URI.buildUpon().appendPath(testCurrency.code).build(),
            null,
            null
        )
        TestCase.assertEquals(0, result)
    }
}

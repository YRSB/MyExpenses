package org.totschnig.myexpenses.retrofit

import androidx.annotation.Keep
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDate

interface CoinApi {

    @GET("v1/exchangerate/{base}?invert=true")
    fun getAllCurrent(
        @Path("base") base: String,
        @Query("filter_asset_id") symbols: String,
        @Header("X-CoinAPI-Key") apiKey: String
    ): Call<AllCurrentResult>

    @GET("v1/exchangerate/{base}/{symbol}")
    fun getExchangeRate(
        @Path("base") base: String,
        @Path("symbol") symbol: String,
        @Query("time") time: LocalDate?,
        @Header("X-CoinAPI-Key") apiKey: String
    ): Call<ExchangeRateResult>

    @GET("v1/exchangerate/{base}/{symbol}/history?period_id=1DAY")
    fun getHistory(
        @Path("base") base: String,
        @Path("symbol") symbol: String,
        @Query("time_start") start: LocalDate,
        @Query("time_end") end: LocalDate,
        @Header("X-CoinAPI-Key") apiKey: String
    ): Call<List<HistoryResult>>

    @Keep
    data class AllCurrentResult(val rates: List<ExchangeRateResult>)

    @Keep
    data class ExchangeRateResult(val time: String, val rate: Double, val asset_id_quote: String)

    @Keep
    data class HistoryResult(val rate_open: Double, val rate_high: Double, val rate_low: Double, val rate_close: Double)
}
package com.godzilla.app.di

import com.godzilla.app.data.remote.*
import com.godzilla.app.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("BinanceSpot")
    fun provideBinanceSpotRetrofit(): Retrofit = createRetrofit("https://api.binance.com/")

    @Provides
    @Singleton
    @Named("BinanceFutures")
    fun provideBinanceFuturesRetrofit(): Retrofit = createRetrofit("https://fapi.binance.com/")

    @Provides
    @Singleton
    @Named("Bybit")
    fun provideBybitRetrofit(): Retrofit = createRetrofit("https://api.bybit.com/")

    @Provides
    @Singleton
    @Named("OKX")
    fun provideOkxRetrofit(): Retrofit = createRetrofit("https://www.okx.com/")

    @Provides
    @Singleton
    @Named("Bitget")
    fun provideBitgetRetrofit(): Retrofit = createRetrofit("https://api.bitget.com/")

    @Provides
    @Singleton
    @Named("KuCoinSpot")
    fun provideKuCoinSpotRetrofit(): Retrofit = createRetrofit("https://api.kucoin.com/")

    @Provides
    @Singleton
    @Named("KuCoinFutures")
    fun provideKuCoinFuturesRetrofit(): Retrofit = createRetrofit("https://api-futures.kucoin.com/")

    @Provides
    @Singleton
    @Named("Coinbase")
    fun provideCoinbaseRetrofit(): Retrofit = createRetrofit("https://api.coinbase.com/")

    @Provides
    @Singleton
    @Named("MexcSpot")
    fun provideMexcSpotRetrofit(): Retrofit = createRetrofit("https://api.mexc.com/")

    @Provides
    @Singleton
    @Named("MexcFutures")
    fun provideMexcFuturesRetrofit(): Retrofit = createRetrofit("https://contract.mexc.com/")

    @Provides
    @Singleton
    @Named("BingX")
    fun provideBingXRetrofit(): Retrofit = createRetrofit("https://open-api.bingx.com/")

    @Provides
    @Singleton
    @Named("Toobit")
    fun provideToobitRetrofit(): Retrofit = createRetrofit("https://api.toobit.com/")

    @Provides
    @Singleton
    @Named("GateIo")
    fun provideGateIoRetrofit(): Retrofit = createRetrofit("https://api.gateio.ws/")

    @Provides
    @Singleton
    @Named("BitMart")
    fun provideBitMartRetrofit(): Retrofit = createRetrofit("https://api-cloud.bitmart.com/")

    @Provides
    @Singleton
    @Named("YahooFinance")
    fun provideYahooFinanceRetrofit(): Retrofit = createRetrofit("https://query1.finance.yahoo.com/")

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideBinanceSpotApi(@Named("BinanceSpot") retrofit: Retrofit): BinanceSpotApi = retrofit.create(BinanceSpotApi::class.java)

    @Provides
    @Singleton
    fun provideBinanceFuturesApi(@Named("BinanceFutures") retrofit: Retrofit): BinanceFuturesApi = retrofit.create(BinanceFuturesApi::class.java)

    @Provides
    @Singleton
    fun provideBybitApi(@Named("Bybit") retrofit: Retrofit): BybitApi = retrofit.create(BybitApi::class.java)

    @Provides
    @Singleton
    fun provideOkxApi(@Named("OKX") retrofit: Retrofit): OkxApi = retrofit.create(OkxApi::class.java)

    @Provides
    @Singleton
    fun provideBitgetApi(@Named("Bitget") retrofit: Retrofit): BitgetApi = retrofit.create(BitgetApi::class.java)

    @Provides
    @Singleton
    fun provideKuCoinSpotApi(@Named("KuCoinSpot") retrofit: Retrofit): KuCoinSpotApi = retrofit.create(KuCoinSpotApi::class.java)

    @Provides
    @Singleton
    fun provideKuCoinFuturesApi(@Named("KuCoinFutures") retrofit: Retrofit): KuCoinFuturesApi = retrofit.create(KuCoinFuturesApi::class.java)

    @Provides
    @Singleton
    fun provideCoinbaseApi(@Named("Coinbase") retrofit: Retrofit): CoinbaseApi = retrofit.create(CoinbaseApi::class.java)

    @Provides
    @Singleton
    fun provideMexcSpotApi(@Named("MexcSpot") retrofit: Retrofit): MexcSpotApi = retrofit.create(MexcSpotApi::class.java)

    @Provides
    @Singleton
    fun provideMexcFuturesApi(@Named("MexcFutures") retrofit: Retrofit): MexcFuturesApi = retrofit.create(MexcFuturesApi::class.java)

    @Provides
    @Singleton
    fun provideBingXApi(@Named("BingX") retrofit: Retrofit): BingXApi = retrofit.create(BingXApi::class.java)

    @Provides
    @Singleton
    fun provideToobitApi(@Named("Toobit") retrofit: Retrofit): ToobitApi = retrofit.create(ToobitApi::class.java)

    @Provides
    @Singleton
    fun provideGateIoApi(@Named("GateIo") retrofit: Retrofit): GateIoApi = retrofit.create(GateIoApi::class.java)

    @Provides
    @Singleton
    fun provideBitMartApi(@Named("BitMart") retrofit: Retrofit): BitMartApi = retrofit.create(BitMartApi::class.java)

    @Provides
    @Singleton
    fun provideYahooFinanceApi(@Named("YahooFinance") retrofit: Retrofit): YahooFinanceApi = retrofit.create(YahooFinanceApi::class.java)

    @Provides
    @Singleton
    fun provideRepositories(
        binance: BinanceRepository,
        bybit: BybitRepository,
        okx: OkxRepository,
        bitget: BitgetRepository,
        kucoin: KuCoinRepository,
        coinbase: CoinbaseRepository,
        mexc: MexcRepository,
        bingx: BingXRepository,
        toobit: ToobitRepository,
        gateio: GateIoRepository,
        bitmart: BitMartRepository
    ): List<ExchangeRepository> {
        return listOf(binance, bybit, okx, bitget, kucoin, coinbase, mexc, bingx, toobit, gateio, bitmart)
    }
}

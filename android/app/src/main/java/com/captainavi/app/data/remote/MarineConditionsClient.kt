package com.captainavi.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Current marine and atmospheric model conditions at the vessel position.
 * These values are forecasts, not observations, and are never used as a
 * replacement for an official chart, coastal tide table, or navigation warning.
 */
@Serializable
data class MarineConditions(
    val latitude: Double,
    val longitude: Double,
    val forecastTime: String,
    val fetchedAtMillis: Long,
    val waveHeightMeters: Double? = null,
    val waveDirectionDegrees: Double? = null,
    val wavePeriodSeconds: Double? = null,
    val swellHeightMeters: Double? = null,
    val swellDirectionDegrees: Double? = null,
    val swellPeriodSeconds: Double? = null,
    val seaSurfaceTemperatureCelsius: Double? = null,
    val oceanCurrentKnots: Double? = null,
    val oceanCurrentDirectionDegrees: Double? = null,
    val seaLevelHeightMslMeters: Double? = null,
    val airTemperatureCelsius: Double? = null,
    val precipitationMillimeters: Double? = null,
    val weatherCode: Int? = null,
    val cloudCoverPercent: Int? = null,
    val pressureMslHpa: Double? = null,
    val windSpeedKnots: Double? = null,
    val windDirectionDegrees: Double? = null,
    val windGustKnots: Double? = null,
    val visibilityMeters: Double? = null,
    val hourlyForecast: List<MarineForecastHour> = emptyList(),
    val dailyForecast: List<MarineForecastDay> = emptyList(),
)

/** A joined atmospheric and marine forecast for one local Maldives hour. */
@Serializable
data class MarineForecastHour(
    val time: String,
    val airTemperatureCelsius: Double? = null,
    val precipitationProbabilityPercent: Int? = null,
    val precipitationMillimeters: Double? = null,
    val weatherCode: Int? = null,
    val cloudCoverPercent: Int? = null,
    val pressureMslHpa: Double? = null,
    val windSpeedKnots: Double? = null,
    val windDirectionDegrees: Double? = null,
    val windGustKnots: Double? = null,
    val visibilityMeters: Double? = null,
    val waveHeightMeters: Double? = null,
    val waveDirectionDegrees: Double? = null,
    val wavePeriodSeconds: Double? = null,
    val swellHeightMeters: Double? = null,
    val swellDirectionDegrees: Double? = null,
    val swellPeriodSeconds: Double? = null,
    val seaSurfaceTemperatureCelsius: Double? = null,
    val oceanCurrentKnots: Double? = null,
    val oceanCurrentDirectionDegrees: Double? = null,
    val seaLevelHeightMslMeters: Double? = null,
)

@Serializable
data class MarineForecastDay(
    val date: String,
    val sunrise: String? = null,
    val sunset: String? = null,
)

class MarineConditionsClient {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        engine {
            config {
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(10, TimeUnit.SECONDS)
            }
        }
    }

    suspend fun fetch(latitude: Double, longitude: Double): MarineConditions = coroutineScope {
        val marineDeferred = async { fetchMarine(latitude, longitude) }
        val weatherDeferred = async { fetchWeather(latitude, longitude) }
        val marine = marineDeferred.await()
        val weather = weatherDeferred.await()
        val forecastTime = listOfNotNull(marine.current?.time, weather.current?.time).maxOrNull().orEmpty()
        val hourlyForecast = joinHourlyForecasts(marine.hourly, weather.hourly)
        val dailyForecast = weather.daily?.time.orEmpty().mapIndexed { index, date ->
            MarineForecastDay(
                date = date,
                sunrise = weather.daily?.sunrise?.getOrNull(index),
                sunset = weather.daily?.sunset?.getOrNull(index),
            )
        }

        MarineConditions(
            latitude = latitude,
            longitude = longitude,
            forecastTime = forecastTime,
            fetchedAtMillis = System.currentTimeMillis(),
            waveHeightMeters = marine.current?.waveHeight,
            waveDirectionDegrees = marine.current?.waveDirection,
            wavePeriodSeconds = marine.current?.wavePeriod,
            swellHeightMeters = marine.current?.swellWaveHeight,
            swellDirectionDegrees = marine.current?.swellWaveDirection,
            swellPeriodSeconds = marine.current?.swellWavePeriod,
            seaSurfaceTemperatureCelsius = marine.current?.seaSurfaceTemperature,
            oceanCurrentKnots = marine.current?.oceanCurrentVelocity,
            oceanCurrentDirectionDegrees = marine.current?.oceanCurrentDirection,
            seaLevelHeightMslMeters = marine.current?.seaLevelHeightMsl,
            airTemperatureCelsius = weather.current?.temperature,
            precipitationMillimeters = weather.current?.precipitation,
            weatherCode = weather.current?.weatherCode,
            cloudCoverPercent = weather.current?.cloudCover,
            pressureMslHpa = weather.current?.pressureMsl,
            windSpeedKnots = weather.current?.windSpeed,
            windDirectionDegrees = weather.current?.windDirection,
            windGustKnots = weather.current?.windGusts,
            visibilityMeters = weather.current?.visibility,
            hourlyForecast = hourlyForecast,
            dailyForecast = dailyForecast,
        )
    }

    private suspend fun fetchMarine(latitude: Double, longitude: Double): MarineApiResponse =
        client.get(MARINE_ENDPOINT) {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("current", MARINE_FIELDS)
            parameter("hourly", MARINE_FIELDS)
            parameter("forecast_days", FORECAST_DAYS)
            parameter("wind_speed_unit", "kn")
            parameter("timezone", "auto")
        }.body()

    private suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherApiResponse =
        client.get(WEATHER_ENDPOINT) {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("current", WEATHER_FIELDS)
            parameter("hourly", WEATHER_HOURLY_FIELDS)
            parameter("daily", "sunrise,sunset")
            parameter("forecast_days", FORECAST_DAYS)
            parameter("wind_speed_unit", "kn")
            parameter("timezone", "auto")
        }.body()

    companion object {
        private const val MARINE_ENDPOINT = "https://marine-api.open-meteo.com/v1/marine"
        private const val WEATHER_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        private const val MARINE_FIELDS =
            "wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_direction," +
                "swell_wave_period,sea_surface_temperature,ocean_current_velocity," +
                "ocean_current_direction,sea_level_height_msl"
        private const val WEATHER_FIELDS =
            "temperature_2m,precipitation,weather_code,cloud_cover,pressure_msl," +
                "wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility"
        private const val WEATHER_HOURLY_FIELDS =
            "temperature_2m,precipitation_probability,precipitation,weather_code,cloud_cover," +
                "pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility"
        private const val FORECAST_DAYS = 7
    }
}

private fun joinHourlyForecasts(
    marine: MarineHourly?,
    weather: WeatherHourly?,
): List<MarineForecastHour> {
    val marineIndices = marine?.time.orEmpty().withIndex().associate { it.value to it.index }
    val weatherIndices = weather?.time.orEmpty().withIndex().associate { it.value to it.index }
    return (marineIndices.keys + weatherIndices.keys)
        .distinct()
        .sorted()
        .map { time ->
            val marineIndex = marineIndices[time]
            val weatherIndex = weatherIndices[time]
            MarineForecastHour(
                time = time,
                airTemperatureCelsius = weatherIndex?.let { weather?.temperature?.getOrNull(it) },
                precipitationProbabilityPercent = weatherIndex?.let { weather?.precipitationProbability?.getOrNull(it) },
                precipitationMillimeters = weatherIndex?.let { weather?.precipitation?.getOrNull(it) },
                weatherCode = weatherIndex?.let { weather?.weatherCode?.getOrNull(it) },
                cloudCoverPercent = weatherIndex?.let { weather?.cloudCover?.getOrNull(it) },
                pressureMslHpa = weatherIndex?.let { weather?.pressureMsl?.getOrNull(it) },
                windSpeedKnots = weatherIndex?.let { weather?.windSpeed?.getOrNull(it) },
                windDirectionDegrees = weatherIndex?.let { weather?.windDirection?.getOrNull(it) },
                windGustKnots = weatherIndex?.let { weather?.windGusts?.getOrNull(it) },
                visibilityMeters = weatherIndex?.let { weather?.visibility?.getOrNull(it) },
                waveHeightMeters = marineIndex?.let { marine?.waveHeight?.getOrNull(it) },
                waveDirectionDegrees = marineIndex?.let { marine?.waveDirection?.getOrNull(it) },
                wavePeriodSeconds = marineIndex?.let { marine?.wavePeriod?.getOrNull(it) },
                swellHeightMeters = marineIndex?.let { marine?.swellWaveHeight?.getOrNull(it) },
                swellDirectionDegrees = marineIndex?.let { marine?.swellWaveDirection?.getOrNull(it) },
                swellPeriodSeconds = marineIndex?.let { marine?.swellWavePeriod?.getOrNull(it) },
                seaSurfaceTemperatureCelsius = marineIndex?.let { marine?.seaSurfaceTemperature?.getOrNull(it) },
                oceanCurrentKnots = marineIndex?.let { marine?.oceanCurrentVelocity?.getOrNull(it) },
                oceanCurrentDirectionDegrees = marineIndex?.let { marine?.oceanCurrentDirection?.getOrNull(it) },
                seaLevelHeightMslMeters = marineIndex?.let { marine?.seaLevelHeightMsl?.getOrNull(it) },
            )
        }
}

@Serializable
private data class MarineApiResponse(
    val current: MarineCurrent? = null,
    val hourly: MarineHourly? = null,
)

@Serializable
private data class MarineCurrent(
    val time: String? = null,
    @SerialName("wave_height") val waveHeight: Double? = null,
    @SerialName("wave_direction") val waveDirection: Double? = null,
    @SerialName("wave_period") val wavePeriod: Double? = null,
    @SerialName("swell_wave_height") val swellWaveHeight: Double? = null,
    @SerialName("swell_wave_direction") val swellWaveDirection: Double? = null,
    @SerialName("swell_wave_period") val swellWavePeriod: Double? = null,
    @SerialName("sea_surface_temperature") val seaSurfaceTemperature: Double? = null,
    @SerialName("ocean_current_velocity") val oceanCurrentVelocity: Double? = null,
    @SerialName("ocean_current_direction") val oceanCurrentDirection: Double? = null,
    @SerialName("sea_level_height_msl") val seaLevelHeightMsl: Double? = null,
)

@Serializable
private data class MarineHourly(
    val time: List<String> = emptyList(),
    @SerialName("wave_height") val waveHeight: List<Double?> = emptyList(),
    @SerialName("wave_direction") val waveDirection: List<Double?> = emptyList(),
    @SerialName("wave_period") val wavePeriod: List<Double?> = emptyList(),
    @SerialName("swell_wave_height") val swellWaveHeight: List<Double?> = emptyList(),
    @SerialName("swell_wave_direction") val swellWaveDirection: List<Double?> = emptyList(),
    @SerialName("swell_wave_period") val swellWavePeriod: List<Double?> = emptyList(),
    @SerialName("sea_surface_temperature") val seaSurfaceTemperature: List<Double?> = emptyList(),
    @SerialName("ocean_current_velocity") val oceanCurrentVelocity: List<Double?> = emptyList(),
    @SerialName("ocean_current_direction") val oceanCurrentDirection: List<Double?> = emptyList(),
    @SerialName("sea_level_height_msl") val seaLevelHeightMsl: List<Double?> = emptyList(),
)

@Serializable
private data class WeatherApiResponse(
    val current: WeatherCurrent? = null,
    val hourly: WeatherHourly? = null,
    val daily: WeatherDaily? = null,
)

@Serializable
private data class WeatherCurrent(
    val time: String? = null,
    @SerialName("temperature_2m") val temperature: Double? = null,
    val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Double? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    val visibility: Double? = null,
)

@Serializable
private data class WeatherHourly(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Int?> = emptyList(),
    @SerialName("pressure_msl") val pressureMsl: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    val visibility: List<Double?> = emptyList(),
)

@Serializable
private data class WeatherDaily(
    val time: List<String> = emptyList(),
    val sunrise: List<String?> = emptyList(),
    val sunset: List<String?> = emptyList(),
)

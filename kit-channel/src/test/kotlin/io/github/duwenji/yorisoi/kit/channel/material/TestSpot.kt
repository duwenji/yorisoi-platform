package io.github.duwenji.yorisoi.kit.channel.material

/** A spot as the app finds it (kit-geo `Poi`), for the tests. */
data class TestSpot(
    override val id: String,
    override val name: String,
    override val lat: Double,
    override val lon: Double,
    override val category: String,
    override val distanceM: Int? = 30,
) : Subject.Spot

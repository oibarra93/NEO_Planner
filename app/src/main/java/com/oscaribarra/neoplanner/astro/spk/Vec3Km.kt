package com.oscaribarra.neoplanner.astro.spk

data class Vec3Km(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3Km) = Vec3Km(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3Km) = Vec3Km(x - o.x, y - o.y, z - o.z)
}

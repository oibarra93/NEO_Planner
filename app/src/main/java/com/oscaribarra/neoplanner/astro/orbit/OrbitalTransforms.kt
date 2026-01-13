package com.oscaribarra.neoplanner.astro.orbit

import com.oscaribarra.neoplanner.astro.spk.Vec3Km
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object OrbitalTransforms {

    data class Vec3Au(val x: Double, val y: Double, val z: Double) {
        operator fun plus(o: Vec3Au) = Vec3Au(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: Vec3Au) = Vec3Au(x - o.x, y - o.y, z - o.z)
    }

    fun auToKm(v: Vec3Au): Vec3Km =
        Vec3Km(v.x * AstroConstants.AU_KM, v.y * AstroConstants.AU_KM, v.z * AstroConstants.AU_KM)

    /**
     * Compute heliocentric position in the ORBITAL PLANE (perifocal), units AU.
     * Elliptical only.
     */
    fun perifocalPositionAu(aAu: Double, e: Double, E: Double): Vec3Au {
        val cosE = cos(E)
        val sinE = sin(E)
        val x = aAu * (cosE - e)
        val y = aAu * (sqrt(1.0 - e * e) * sinE)
        return Vec3Au(x, y, 0.0)
    }

    /**
     * Rotate perifocal -> ecliptic J2000 using Ω, i, ω (radians).
     * r = Rz(Ω) * Rx(i) * Rz(ω) * r_pf
     */
    fun perifocalToEclipticJ2000(rPf: Vec3Au, raan: Double, inc: Double, argPeri: Double): Vec3Au {
        val cO = cos(raan); val sO = sin(raan)
        val ci = cos(inc);  val si = sin(inc)
        val cw = cos(argPeri); val sw = sin(argPeri)

        // Combined rotation matrix elements (classic orbital elements transform)
        val m11 = cO * cw - sO * sw * ci
        val m12 = -cO * sw - sO * cw * ci
        val m13 = sO * si

        val m21 = sO * cw + cO * sw * ci
        val m22 = -sO * sw + cO * cw * ci
        val m23 = -cO * si

        val m31 = sw * si
        val m32 = cw * si
        val m33 = ci

        return Vec3Au(
            x = m11 * rPf.x + m12 * rPf.y + m13 * rPf.z,
            y = m21 * rPf.x + m22 * rPf.y + m23 * rPf.z,
            z = m31 * rPf.x + m32 * rPf.y + m33 * rPf.z
        )
    }

    /**
     * Ecliptic J2000 -> Equatorial J2000: rotate about X by +epsilon.
     */
    fun eclipticToEquatorialJ2000(rEcl: Vec3Au): Vec3Au {
        val eps = AngleUtil.degToRad(AstroConstants.EPSILON_J2000_DEG)
        val c = cos(eps)
        val s = sin(eps)

        val x = rEcl.x
        val y = c * rEcl.y - s * rEcl.z
        val z = s * rEcl.y + c * rEcl.z

        return Vec3Au(x, y, z)
    }
}

package com.omnibuds.core.firmware

/**
 * Strongly-typed firmware version representation.
 *
 * Separates firmware identity from protocol versions, SDK versions, application
 * versions, and metadata schema versions.
 *
 * Real-world earbud firmware uses diverse schemes:
 * - Semantic versions (e.g. "1.2.3")
 * - Incremental build numbers / integer revisions (e.g. 1024 or "build-42")
 * - Date-based versions (e.g. "2023.11.05" or "20231105")
 * - Apple-style build strings or alphanumeric codes (e.g. "4E71", "5B58", "6A301")
 * - Opaque vendor tokens or checksums
 *
 * Never defaults to "0.0.0" or arbitrary numbers. Unobserved or unparsed firmware
 * is explicitly represented as [Unknown] or preserved as [Opaque] with raw text.
 */
sealed interface FirmwareVersion : Comparable<FirmwareVersion> {

    /** The raw, unmanipulated string representation as read from the device. */
    val rawValue: String

    /** The scheme characterizing this version. */
    val scheme: FirmwareScheme

    /**
     * Standard Semantic Versioning (major.minor.patch).
     */
    data class Semantic(
        override val rawValue: String,
        val major: Int,
        val minor: Int,
        val patch: Int = 0,
        val preRelease: String? = null,
    ) : FirmwareVersion {
        init {
            require(major >= 0) { "major version must be non-negative" }
            require(minor >= 0) { "minor version must be non-negative" }
            require(patch >= 0) { "patch version must be non-negative" }
            require(rawValue.isNotBlank()) { "raw value must not be blank" }
        }

        override val scheme: FirmwareScheme get() = FirmwareScheme.SEMANTIC

        override fun compareTo(other: FirmwareVersion): Int {
            if (other !is Semantic) return this.scheme.compareTo(other.scheme)
            val maj = major.compareTo(other.major)
            if (maj != 0) return maj
            val min = minor.compareTo(other.minor)
            if (min != 0) return min
            val pat = patch.compareTo(other.patch)
            if (pat != 0) return pat
            return when {
                preRelease == null && other.preRelease == null -> 0
                preRelease == null -> 1 // normal release is higher than pre-release
                other.preRelease == null -> -1
                else -> preRelease.compareTo(other.preRelease)
            }
        }

        override fun toString(): String = rawValue
    }

    /**
     * Monotonic build number or integer revision (e.g. "104", "5023").
     */
    data class BuildNumber(
        override val rawValue: String,
        val build: Long,
    ) : FirmwareVersion {
        init {
            require(build >= 0) { "build number must be non-negative" }
            require(rawValue.isNotBlank()) { "raw value must not be blank" }
        }

        override val scheme: FirmwareScheme get() = FirmwareScheme.BUILD_NUMBER

        override fun compareTo(other: FirmwareVersion): Int {
            if (other !is BuildNumber) return this.scheme.compareTo(other.scheme)
            return build.compareTo(other.build)
        }

        override fun toString(): String = rawValue
    }

    /**
     * Date-based versioning (e.g. YYYYMMDD or YYYY.MM.DD).
     */
    data class DateBased(
        override val rawValue: String,
        val year: Int,
        val month: Int,
        val day: Int,
        val revision: Int = 0,
    ) : FirmwareVersion {
        init {
            require(year in 2000..2099) { "year out of valid range" }
            require(month in 1..12) { "month out of valid range" }
            require(day in 1..31) { "day out of valid range" }
            require(revision >= 0) { "revision must be non-negative" }
            require(rawValue.isNotBlank()) { "raw value must not be blank" }
        }

        override val scheme: FirmwareScheme get() = FirmwareScheme.DATE_BASED

        override fun compareTo(other: FirmwareVersion): Int {
            if (other !is DateBased) return this.scheme.compareTo(other.scheme)
            val y = year.compareTo(other.year)
            if (y != 0) return y
            val m = month.compareTo(other.month)
            if (m != 0) return m
            val d = day.compareTo(other.day)
            if (d != 0) return d
            return revision.compareTo(other.revision)
        }

        override fun toString(): String = rawValue
    }

    /**
     * Alphanumeric vendor build identifier (e.g. Apple AirPods "4E71", "5B58", "6A301").
     *
     * These identifiers often follow vendor-internal progressions (e.g. major digit + letter + build),
     * but without official ordering specifications, lexical order is preserved only when explicitly safe.
     */
    data class AlphanumericBuild(
        override val rawValue: String,
        val generation: Int? = null,
        val trainLetter: Char? = null,
        val sequence: Int? = null,
    ) : FirmwareVersion {
        init {
            require(rawValue.isNotBlank()) { "raw value must not be blank" }
        }

        override val scheme: FirmwareScheme get() = FirmwareScheme.ALPHANUMERIC_BUILD

        override fun compareTo(other: FirmwareVersion): Int {
            if (other !is AlphanumericBuild) return this.scheme.compareTo(other.scheme)
            // If both have structured parts, order by generation, trainLetter, sequence
            if (generation != null && other.generation != null &&
                trainLetter != null && other.trainLetter != null &&
                sequence != null && other.sequence != null) {
                val g = generation.compareTo(other.generation)
                if (g != 0) return g
                val t = trainLetter.compareTo(other.trainLetter)
                if (t != 0) return t
                return sequence.compareTo(other.sequence)
            }
            return rawValue.compareTo(other.rawValue)
        }

        override fun toString(): String = rawValue
    }

    /**
     * Opaque or vendor-proprietary firmware string that cannot be safely structured or ordered.
     * Equality is strictly exact string equality.
     */
    data class Opaque(
        override val rawValue: String,
    ) : FirmwareVersion {
        init {
            require(rawValue.isNotBlank()) { "raw value must not be blank" }
        }

        override val scheme: FirmwareScheme get() = FirmwareScheme.OPAQUE

        override fun compareTo(other: FirmwareVersion): Int {
            if (other !is Opaque) return this.scheme.compareTo(other.scheme)
            return rawValue.compareTo(other.rawValue)
        }

        override fun toString(): String = rawValue
    }

    /**
     * Sentinel indicating firmware version was not observed, could not be read,
     * or is completely unavailable.
     */
    data object Unknown : FirmwareVersion {
        override val rawValue: String = "unknown"
        override val scheme: FirmwareScheme get() = FirmwareScheme.UNKNOWN

        override fun compareTo(other: FirmwareVersion): Int {
            if (other is Unknown) return 0
            return this.scheme.compareTo(other.scheme)
        }

        override fun toString(): String = "unknown"
    }

    companion object {
        private const val MAX_RAW_LENGTH = 128

        /**
         * Safely parse an arbitrary firmware string into a typed [FirmwareVersion].
         * Bounds input size to prevent denial-of-service / memory abuse.
         */
        fun parse(raw: String?, preferredScheme: FirmwareScheme? = null): FirmwareVersion {
            if (raw.isNullOrBlank()) return Unknown
            val trimmed = raw.trim()
            if (trimmed.length > MAX_RAW_LENGTH) {
                return Opaque(trimmed.take(MAX_RAW_LENGTH))
            }
            if (trimmed.equals("unknown", ignoreCase = true) || trimmed == "0.0.0") {
                // If device literally sends 0.0.0 or "unknown", treat as Unknown
                return Unknown
            }

            if (preferredScheme != null) {
                when (preferredScheme) {
                    FirmwareScheme.SEMANTIC -> parseSemantic(trimmed)?.let { return it }
                    FirmwareScheme.BUILD_NUMBER -> parseBuildNumber(trimmed)?.let { return it }
                    FirmwareScheme.DATE_BASED -> parseDateBased(trimmed)?.let { return it }
                    FirmwareScheme.ALPHANUMERIC_BUILD -> parseAlphanumericBuild(trimmed)?.let { return it }
                    FirmwareScheme.OPAQUE -> return Opaque(trimmed)
                    FirmwareScheme.UNKNOWN -> return Unknown
                }
            }

            // General heuristic parsing in descending specificity
            parseDateBased(trimmed)?.let { return it }
            parseSemantic(trimmed)?.let { return it }
            parseAlphanumericBuild(trimmed)?.let { return it }
            parseBuildNumber(trimmed)?.let { return it }

            return Opaque(trimmed)
        }

        /**
         * Parses semantic versions e.g. "1.2.3", "v2.0", "1.0.4-rc1".
         */
        fun parseSemantic(raw: String): Semantic? {
            val stripped = raw.removePrefix("v").removePrefix("V").trim()
            val dashIndex = stripped.indexOf('-')
            val versionCore = if (dashIndex >= 0) stripped.substring(0, dashIndex) else stripped
            val preRelease = if (dashIndex >= 0) stripped.substring(dashIndex + 1) else null

            val parts = versionCore.split(".")
            if (parts.size !in 2..3) return null

            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = if (parts.size == 3) parts[2].toIntOrNull() ?: return null else 0

            // Disallow calendar years (>= 2000) as semantic major versions
            if (major >= 2000 && parts.size >= 3) return null

            if (major < 0 || minor < 0 || patch < 0) return null
            return Semantic(raw, major, minor, patch, preRelease)
        }

        /**
         * Parses integer build numbers e.g. "1024", "rev-55", "b109", "build-1050".
         */
        fun parseBuildNumber(raw: String): BuildNumber? {
            val clean = raw.removePrefix("build-")
                .removePrefix("rev-")
                .removePrefix("r")
                .removePrefix("b")
                .trim()
            val num = clean.toLongOrNull() ?: return null
            if (num < 0) return null
            return BuildNumber(raw, num)
        }

        /**
         * Parses date-based versions e.g. "2023.11.05", "2024-02-18", "20231105".
         */
        fun parseDateBased(raw: String): DateBased? {
            val clean = raw.trim()
            // YYYY.MM.DD or YYYY-MM-DD
            val sepParts = clean.split('.', '-')
            if (sepParts.size in 3..4) {
                val y = sepParts[0].toIntOrNull() ?: return null
                val m = sepParts[1].toIntOrNull() ?: return null
                val d = sepParts[2].toIntOrNull() ?: return null
                val rev = if (sepParts.size == 4) sepParts[3].toIntOrNull() ?: 0 else 0
                if (y in 2000..2099 && m in 1..12 && d in 1..31 && rev >= 0) {
                    return DateBased(raw, y, m, d, rev)
                }
            }
            // 8 digits: YYYYMMDD
            if (clean.length == 8 && clean.all { it.isDigit() }) {
                val y = clean.substring(0, 4).toIntOrNull() ?: return null
                val m = clean.substring(4, 6).toIntOrNull() ?: return null
                val d = clean.substring(6, 8).toIntOrNull() ?: return null
                if (y in 2000..2099 && m in 1..12 && d in 1..31) {
                    return DateBased(raw, y, m, d)
                }
            }
            return null
        }

        /**
         * Parses Apple/AirPods style alphanumeric builds e.g. "4E71", "5B58", "6A301", "7A294".
         * Format typically: [1-2 digits][A-Z][1-4 digits]
         */
        fun parseAlphanumericBuild(raw: String): AlphanumericBuild? {
            val clean = raw.trim()
            if (clean.length !in 4..8) return null
            val regex = Regex("^([0-9]{1,2})([A-Za-z])([0-9]{1,4})$")
            val match = regex.matchEntire(clean) ?: return null
            val gen = match.groupValues[1].toIntOrNull() ?: return null
            val letter = match.groupValues[2].first().uppercaseChar()
            val seq = match.groupValues[3].toIntOrNull() ?: return null
            return AlphanumericBuild(raw, gen, letter, seq)
        }
    }
}

/**
 * Enumeration of recognized firmware versioning schemes.
 */
enum class FirmwareScheme {
    SEMANTIC,
    BUILD_NUMBER,
    DATE_BASED,
    ALPHANUMERIC_BUILD,
    OPAQUE,
    UNKNOWN,
}

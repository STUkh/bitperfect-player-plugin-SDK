package com.example.bitperfect.plugin.networksource

/**
 * Open-source and public-domain audio stream definitions for legal, ethical streaming.
 * Includes curated lossless FLAC and high-bitrate community streams.
 */
data class AudioStation(
    val id: String,
    val title: String,
    val description: String,
    val genre: String,
    val streamUrlFlac: String?,
    val streamUrlMp3: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val codec: String,
    val artworkUrl: String,
    val homepageUrl: String,
    val serverType: String = "Icecast / Shoutcast",
    val licenseType: String = "Public / Community Open Audio",
)

object OpenAudioCatalog {

    val STATIONS: List<AudioStation> = listOf(
        AudioStation(
            id = "rp_main",
            title = "Radio Paradise (Main Mix)",
            description = "Eclectic blend of modern and classic rock, world music, electronica, and classical. Pristine bit-perfect FLAC.",
            genre = "Eclectic Rock",
            streamUrlFlac = "https://stream.radioparadise.com/flac",
            streamUrlMp3 = "https://stream.radioparadise.com/mp3-320",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "flac",
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop",
            homepageUrl = "https://radioparadise.com",
        ),
        AudioStation(
            id = "rp_mellow",
            title = "Radio Paradise (Mellow Mix)",
            description = "Warm acoustic, chillout, downtempo, and contemplative melodies in uncompressed lossless quality.",
            genre = "Acoustic & Chill",
            streamUrlFlac = "https://stream.radioparadise.com/mellow-flac",
            streamUrlMp3 = "https://stream.radioparadise.com/mellow-320",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "flac",
            artworkUrl = "https://images.unsplash.com/photo-1445985543470-41fba5c3144a?w=600&auto=format&fit=crop",
            homepageUrl = "https://radioparadise.com",
        ),
        AudioStation(
            id = "rp_rock",
            title = "Radio Paradise (Rock Mix)",
            description = "High-energy rock classics, indie rock, and blues-rock transmitted in 16-bit / 44.1kHz FLAC.",
            genre = "Rock & Blues",
            streamUrlFlac = "https://stream.radioparadise.com/rock-flac",
            streamUrlMp3 = "https://stream.radioparadise.com/rock-320",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "flac",
            artworkUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600&auto=format&fit=crop",
            homepageUrl = "https://radioparadise.com",
        ),
        AudioStation(
            id = "rp_global",
            title = "Radio Paradise (Global Mix)",
            description = "World music, afrobeat, Latin grooves, and international fusion streams in lossless audio.",
            genre = "World & Fusion",
            streamUrlFlac = "https://stream.radioparadise.com/global-flac",
            streamUrlMp3 = "https://stream.radioparadise.com/global-320",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "flac",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop",
            homepageUrl = "https://radioparadise.com",
        ),
        AudioStation(
            id = "venice_classic",
            title = "Venice Classic Radio",
            description = "Early, Baroque, Classical and Romantic Italian and European masterpieces in pure FLAC.",
            genre = "Classical",
            streamUrlFlac = "http://178.32.137.180:8555/stream",
            streamUrlMp3 = "http://178.32.137.180:8555/stream.mp3",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "flac",
            artworkUrl = "https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=600&auto=format&fit=crop",
            homepageUrl = "https://www.veniceclassicradio.eu",
        ),
        AudioStation(
            id = "swiss_classic",
            title = "Swiss Classic Radio",
            description = "Broadcast from Switzerland featuring live recordings, sonatas, and symphonies in CD quality.",
            genre = "Classical",
            streamUrlFlac = "http://stream.srg-ssr.ch/m/rsc_de/flac",
            streamUrlMp3 = "http://stream.srg-ssr.ch/m/rsc_de/mp3_128",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "flac",
            artworkUrl = "https://images.unsplash.com/photo-1513883049090-d0b7439799bf?w=600&auto=format&fit=crop",
            homepageUrl = "https://www.radioswissclassic.ch",
        ),
        AudioStation(
            id = "open_goldberg",
            title = "Open Goldberg Variations (Kimiko Ishizaka)",
            description = "J.S. Bach Goldberg Variations BWV 988. Dedicated to the public domain under Creative Commons Zero (CC0).",
            genre = "Classical",
            streamUrlFlac = "https://raw.githubusercontent.com/open-audio/goldberg/main/goldberg_44k16b.flac",
            streamUrlMp3 = "https://raw.githubusercontent.com/open-audio/goldberg/main/goldberg_320k.mp3",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "flac",
            artworkUrl = "https://images.unsplash.com/photo-1520523839898-50712825e3a7?w=600&auto=format&fit=crop",
            homepageUrl = "https://opengoldbergvariations.org",
        ),
        AudioStation(
            id = "musopen_beethoven",
            title = "Musopen Symphony No. 5 (Beethoven)",
            description = "Public domain orchestral recording preserved and digitized for open access by Musopen.",
            genre = "Classical",
            streamUrlFlac = "https://musopen.org/audio/sample_beethoven_sym5.flac",
            streamUrlMp3 = "https://musopen.org/audio/sample_beethoven_sym5.mp3",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "flac",
            artworkUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop",
            homepageUrl = "https://musopen.org",
        ),
        AudioStation(
            id = "soma_groovesalad",
            title = "SomaFM: Groove Salad",
            description = "A nicely chilled plate of ambient and mid-tempo electronic grooves. High-bitrate 320k stream.",
            genre = "Ambient & Chill",
            streamUrlFlac = null,
            streamUrlMp3 = "https://ice1.somafm.com/groovesalad-256-mp3",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "mp3",
            artworkUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop",
            homepageUrl = "https://somafm.com/groovesalad/",
        ),
        AudioStation(
            id = "soma_dronezone",
            title = "SomaFM: Drone Zone",
            description = "Atmospheric textures, minimal ambient soundscapes, and drone music with warm sonic depth.",
            genre = "Ambient & Chill",
            streamUrlFlac = null,
            streamUrlMp3 = "https://ice1.somafm.com/dronezone-256-mp3",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "mp3",
            artworkUrl = "https://images.unsplash.com/photo-1509114397022-ed747cca3f65?w=600&auto=format&fit=crop",
            homepageUrl = "https://somafm.com/dronezone/",
        ),
        AudioStation(
            id = "soma_defcon",
            title = "SomaFM: DEF CON Radio",
            description = "Music for hacking and cyber exploration. Synthwave, dark ambient, and electronic rhythms.",
            genre = "Electronic",
            streamUrlFlac = null,
            streamUrlMp3 = "https://ice1.somafm.com/defcon-256-mp3",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "mp3",
            artworkUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600&auto=format&fit=crop",
            homepageUrl = "https://somafm.com/defcon/",
        ),
        AudioStation(
            id = "fip_paris",
            title = "FIP Radio (Radio France)",
            description = "Commercial-free French public radio with legendary eclectic curation across jazz, soul, and world.",
            genre = "World & Fusion",
            streamUrlFlac = null,
            streamUrlMp3 = "https://icecast.radiofrance.fr/fip-midfi.mp3",
            sampleRate = 44100,
            bitDepth = 16,
            codec = "mp3",
            artworkUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&auto=format&fit=crop",
            homepageUrl = "https://www.radiofrance.fr/fip",
        ),
    )

    fun findById(id: String): AudioStation? =
        STATIONS.firstOrNull { it.id == id }

    fun featured(): List<AudioStation> =
        STATIONS.filter { it.streamUrlFlac != null }

    fun byGenre(genre: String): List<AudioStation> =
        STATIONS.filter { it.genre.equals(genre, ignoreCase = true) }

    fun genres(): List<String> =
        STATIONS.map { it.genre }.distinct()

    fun search(query: String): List<AudioStation> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return STATIONS.filter {
            it.title.lowercase().contains(q) ||
                it.genre.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
        }
    }

    fun paginated(page: Int, pageSize: Int = 5): Pair<List<AudioStation>, String?> {
        val startIndex = (page - 1) * pageSize
        if (startIndex >= STATIONS.size) return Pair(emptyList(), null)
        val endIndex = (startIndex + pageSize).coerceAtMost(STATIONS.size)
        val items = STATIONS.subList(startIndex, endIndex)
        val nextToken = if (endIndex < STATIONS.size) "page_${page + 1}" else null
        return Pair(items, nextToken)
    }
}

package com.gmtrekking.app.data.trails

/**
 * Costruisce la query Overpass QL per la scoperta di sentieri vicini (punto 5
 * del piano) e interpreta il tag `sac_scale` per la difficoltà.
 *
 * Fonte dati: relazioni OSM `route=hiking` (stesso approccio già verificato
 * in fase di fattibilità in docs/PIANO_SVILUPPO.md) — Wikiloc e Komoot non
 * hanno un'API pubblica utilizzabile qui, OpenStreetMap tramite Overpass è la
 * strada percorribile senza costi né chiavi.
 */
object TrailQueryBuilder {

    /**
     * Cerca le relazioni `route=hiking` che passano entro [radiusMeters] dal
     * punto dato, poi le loro way membro con geometria completa ("out geom"),
     * per poter ricostruire il tracciato lato app senza bisogno di scaricare
     * il file GPX da terzi (lo generiamo noi in TrailRepository/GpxWriter).
     */
    fun buildNearbyTrailsQuery(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int,
        timeoutSeconds: Int = 25,
    ): String {
        val around = "around:$radiusMeters,$centerLat,$centerLon"
        return """
            [out:json][timeout:$timeoutSeconds];
            relation($around)["route"="hiking"];
            out tags;
            way(r);
            out geom;
        """.trimIndent()
    }

    /**
     * Converte il valore grezzo del tag `sac_scale` nell'enum interno usato
     * dalla UI. Ritorna null sia se il tag manca sia se ha un valore non
     * riconosciuto — in entrambi i casi la UI mostra "difficoltà non
     * specificata", mai un valore inventato (copertura non uniforme di
     * questo tag su OSM, vedi limite noto nel piano).
     */
    fun difficultyFor(sacScale: String?): TrailDifficulty? = when (sacScale) {
        "hiking" -> TrailDifficulty.HIKING
        "mountain_hiking" -> TrailDifficulty.MOUNTAIN_HIKING
        "demanding_mountain_hiking" -> TrailDifficulty.DEMANDING_MOUNTAIN_HIKING
        "alpine_hiking" -> TrailDifficulty.ALPINE_HIKING
        "demanding_alpine_hiking" -> TrailDifficulty.DEMANDING_ALPINE_HIKING
        "difficult_alpine_hiking" -> TrailDifficulty.DIFFICULT_ALPINE_HIKING
        else -> null
    }
}

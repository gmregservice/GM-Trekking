package com.gmtrekking.app.data.poi

/**
 * Costruisce query Overpass QL a partire dalle categorie scelte dall'utente
 * nel filtro (PlaceCategory) e da un centro + raggio di ricerca.
 *
 * Nota sui dati: OpenStreetMap non distingue formalmente "trattoria" da
 * "ristorante" (nessun tag dedicato) — per ora TRATTORIA usa lo stesso tag
 * amenity=restaurant di RESTAURANT. È un limite noto dei dati sorgente,
 * documentato anche nell'analisi di fattibilità: si può affinare in futuro
 * con euristiche sul tag "cuisine" o sul nome del locale.
 */
object OverpassQueryBuilder {

    /** Coppie (chiave, valore) di tag OSM associate a ciascuna categoria (ALL escluso). */
    private val categoryTags: Map<PlaceCategory, List<Pair<String, String>>> = mapOf(
        PlaceCategory.RESTAURANT to listOf("amenity" to "restaurant"),
        PlaceCategory.TRATTORIA to listOf("amenity" to "restaurant"),
        PlaceCategory.BAR to listOf("amenity" to "bar"),
        PlaceCategory.HOTEL to listOf("tourism" to "hotel"),
        PlaceCategory.HOSTEL to listOf("tourism" to "hostel"),
        PlaceCategory.ALPINE_HUT to listOf("tourism" to "alpine_hut"),
        PlaceCategory.CAMP_SITE to listOf("tourism" to "camp_site"),
        PlaceCategory.GUEST_HOUSE to listOf("tourism" to "guest_house", "tourism" to "bed_and_breakfast"),
    )

    fun tagsFor(category: PlaceCategory): List<Pair<String, String>> =
        categoryTags[category] ?: emptyList()

    /**
     * Query per cercare, intorno a un punto (lat/lon) entro un raggio in metri,
     * tutti i luoghi utili delle categorie richieste. Se [categories] è vuoto o
     * contiene ALL, cerca tutte le categorie note.
     */
    fun buildAroundPointQuery(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int,
        categories: List<PlaceCategory>,
        timeoutSeconds: Int = 25,
    ): String {
        val effectiveCategories = if (categories.isEmpty() || categories.contains(PlaceCategory.ALL)) {
            categoryTags.keys.toList()
        } else {
            categories
        }

        val tagPairs = effectiveCategories.flatMap { tagsFor(it) }.distinct()
        val around = "around:$radiusMeters,$centerLat,$centerLon"

        // Raggruppate per chiave OSM (es. tutte le "tourism=..." insieme) e
        // unite in un solo filtro con espressione regolare sui valori, invece
        // di ripetere il filtro spaziale "around" una volta per ogni singola
        // coppia chiave/valore: con la categoria "Tutti" erano fino a 16
        // filtri around separati (8 coppie x node/way) — la parte più lenta
        // della query, essendo una ricerca spaziale — ridotti a 4 (2 chiavi
        // x node/way). Stesso risultato, query più veloce: mitiga gli HTTP
        // 504 Gateway Timeout osservati su query con tutte le categorie
        // (bug reale riscontrato su dispositivo, agosto 2026). I valori dei
        // tag sono stringhe semplici (lettere/underscore), sicure da unire
        // in una regex senza escaping.
        val filters = tagPairs
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .entries
            .joinToString(separator = "\n") { (key, values) ->
                val valuePattern = values.distinct().joinToString("|")
                "  node[\"$key\"~\"^($valuePattern)$\"]($around);\n  way[\"$key\"~\"^($valuePattern)$\"]($around);"
            }

        return """
            [out:json][timeout:$timeoutSeconds];
            (
            $filters
            );
            out center tags;
        """.trimIndent()
    }

    /**
     * Deduce la categoria di un elemento Overpass dai suoi tag OSM.
     * Ritorna null se non corrisponde a nessuna categoria nota (l'elemento va scartato).
     */
    fun classify(tags: Map<String, String>): PlaceCategory? {
        for ((category, pairs) in categoryTags) {
            for ((key, value) in pairs) {
                if (tags[key] == value) return category
            }
        }
        return null
    }
}

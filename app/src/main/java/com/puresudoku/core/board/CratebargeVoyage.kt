package com.puresudoku.core.board

import org.json.JSONArray
import org.json.JSONObject

enum class CrateSupply(val title: String, val cost: Int, val description: String) {
    BARREL("Bomb Barrel", 24, "Fire at a tile and up to two adjacent unknown tiles."),
    TELESCOPE("Telescope", 16, "Reveal a tile without firing or spending a turn."),
    PLANKS("Patchwork Planks", 18, "Repair one hit section of your fleet."),
    DECOY("Decoy Buoy", 22, "Lure the next enemy shot into open water.")
}

data class CrateContract(val title: String, val enemy: String, val limit: Int, val reward: Int, val supplies: Map<CrateSupply, Int>, val description: String)

val crateContracts = listOf(
    CrateContract("Normal Voyage", "Cratebarge Training Crew", 65, 8, emptyMap(), "Classic fleet drill."),
    CrateContract("Fog Corsair Contract", "Admiral Mire Voss", 58, 10, mapOf(CrateSupply.TELESCOPE to 1), "Balanced fog duel; one telescope issued."),
    CrateContract("Reefbreaker Raid", "Brakka Reefmaul", 42, 14, mapOf(CrateSupply.BARREL to 1, CrateSupply.TELESCOPE to 1), "Short raid; a barrel and telescope issued."),
    CrateContract("Iron Toll Blockade", "Commodore Harl Strake", 36, 16, mapOf(CrateSupply.PLANKS to 1), "A tight blockade; one repair issued."),
    CrateContract("Storm Lantern Rush", "Captain Selka Morn", 28, 20, mapOf(CrateSupply.TELESCOPE to 2, CrateSupply.DECOY to 1), "A fast strike; two telescopes and a decoy issued."),
    CrateContract("Crownless Warden", "Warden Nhal", 24, 26, mapOf(CrateSupply.BARREL to 1, CrateSupply.PLANKS to 1, CrateSupply.DECOY to 1), "An elite trial; barrel, repair and decoy issued.")
)

/** Cratebarge-only wallet. It is stored together with the active save after each transaction. */
class CrateProfile(var dice: Int = 0, var unlocked: Int = 0, var introSeen: Boolean = false,
                   val stock: IntArray = IntArray(CrateSupply.entries.size)) {
    fun buy(s: CrateSupply): Boolean {
        if (dice < s.cost) return false
        dice -= s.cost; stock[s.ordinal]++; return true
    }
    fun toJson(): JSONObject = JSONObject().put("dice", dice).put("unlocked", unlocked).put("introSeen", introSeen)
        .put("stock", JSONArray().also { a -> stock.forEach { a.put(it) } })
    companion object {
        fun fromJson(o: JSONObject?): CrateProfile {
            if (o == null) return CrateProfile()
            val a = o.optJSONArray("stock")
            return CrateProfile(o.optInt("dice").coerceAtLeast(0), o.optInt("unlocked").coerceIn(0, crateContracts.lastIndex),
                o.optBoolean("introSeen"), IntArray(CrateSupply.entries.size) { a?.optInt(it, 0)?.coerceAtLeast(0) ?: 0 })
        }
    }
}

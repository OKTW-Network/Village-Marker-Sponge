package one.oktw.villagemarkersponge

import net.minecraft.world.DimensionType
import net.minecraft.world.World
import java.util.Arrays.asList

class VillageHelper {
    companion object {
        private var id: Int = -1

        fun nextId(): Int {
            id = if (id + 1 > 999) {
                0
            } else {
                id + 1
            }

            return id
        }

        fun getFakeDimension(world: org.spongepowered.api.world.World): Int {
            return when (world.dimension.type) {
                DimensionType.NETHER -> -1
                DimensionType.OVERWORLD -> 0
                DimensionType.THE_END -> 1
                else -> 0
            }
        }

        fun getWorldStringList(id: Int, world: org.spongepowered.api.world.World, maxLength: Int): List<String> {
            val dimensionId = getFakeDimension(world)

            return splitString(id, createWorldString(world), dimensionId, maxLength)
        }

        private fun splitString(id: Int, str: String, dimension: Int, maxLength: Int): List<String> {
            if (str.length < maxLength) {
                return asList("$id<$dimension:1:1>$str")
            } else {
                val list = ArrayList<String>()
                val parts = (str.length - 1) / maxLength + 1

                for (i in 0 until parts) {
                    if (i + 1 == parts) {
                        list.add("$id<$dimension:${i + 1}:$parts>${str.substring(maxLength * i, str.length)}")
                    } else {
                        list.add("$id<$dimension:${i + 1}:$parts>${str.substring(maxLength * i, maxLength * (i + 1))}")
                    }
                }

                return list
            }
        }

        private fun createWorldString(world: org.spongepowered.api.world.World): String {
            val nativeWorld = world as World
//            val dimensionId = nativeWorld.provider.dimension
            val dimensionId = getFakeDimension(world)

            try {
                val builder = StringBuilder()
                val villages = nativeWorld.villageCollection.villageList

                builder.run {
                    append(dimensionId.toString())

                    for (village in villages) {
                        val center = village.center
                        append(":${village.villageRadius}")
                        append(center.run { ";$x,$y,$z" })

                        for (door in village.villageDoorInfoList) {
                            val position = door.doorBlockPos
                            append(position.run { ";$x,$y,$z" })
                        }
                    }
                }

                return builder.toString()
            } catch (e: Throwable) {
                return dimensionId.toString()
            }
        }
    }
}
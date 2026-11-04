// Apply on server-side joins, including existing Endermen loaded from saves.
// Set the base rather than adding a modifier, so repeated joins cannot stack armour.
var BcEndermanTypes = Java.loadClass('net.minecraft.world.entity.EntityType')
var BcEndermanAttributes = Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes')

ForgeEvents.onEvent('net.minecraftforge.event.entity.EntityJoinLevelEvent', function (event) {
    if (event.getLevel().isClientSide()) return
    var entity = event.getEntity()
    if (!BcEndermanTypes.ENDERMAN.equals(entity.getType())) return
    entity.getAttribute(BcEndermanAttributes.ARMOR).setBaseValue(4.0)
})

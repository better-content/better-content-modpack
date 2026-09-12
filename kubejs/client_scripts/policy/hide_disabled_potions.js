// Vanilla effect-bearing potion delivery is disabled. Plain water bottles stay
// visible because thirst and several retained water-transfer recipes use them.
var BcPotionRegistries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries')
var BcPotionUtils = Java.loadClass('net.minecraft.world.item.alchemy.PotionUtils')
var BcPotions = Java.loadClass('net.minecraft.world.item.alchemy.Potions')
var BcPotionItems = Java.loadClass('net.minecraft.world.item.Items')
var BcPotionItemStack = Java.loadClass('net.minecraft.world.item.ItemStack')

function bcDisabledDrinkablePotionStacks() {
    var hidden = []
    var iterator = BcPotionRegistries.POTION.iterator()
    while (iterator.hasNext()) {
        var potion = iterator.next()
        if (!potion.equals(BcPotions.WATER)) {
            hidden.push(BcPotionUtils.setPotion(new BcPotionItemStack(BcPotionItems.POTION), potion))
        }
    }
    return hidden
}

function bcHideDisabledPotionItems(event) {
    bcDisabledDrinkablePotionStacks().forEach(function (stack) { event.hide(stack) })
    event.hide('minecraft:splash_potion')
    event.hide('minecraft:lingering_potion')
    event.hide('minecraft:tipped_arrow')
    event.hide('minecraft:brewing_stand')
    event.hide('ars_nouveau:alchemical_sourcelink')
    event.hide('supplementaries:bamboo_spikes_tipped')
}

JEIEvents.hideItems(function (event) {
    bcHideDisabledPotionItems(event)
})

if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideItems(function (event) {
        bcHideDisabledPotionItems(event)
    })
}

JEIEvents.hideFluids(function (event) {
    event.hide('tconstruct:potion')
})

if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideFluids(function (event) {
        event.hide('tconstruct:potion')
    })
}

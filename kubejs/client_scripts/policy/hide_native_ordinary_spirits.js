// The seven ordinary Malum shard IDs remain registered for binary compatibility.
// Better Content's physical spirits are the pack's visible and usable items.
var BC_NATIVE_ORDINARY_SPIRITS = [
    'malum:sacred_spirit', 'malum:wicked_spirit', 'malum:arcane_spirit',
    'malum:aerial_spirit', 'malum:aqueous_spirit', 'malum:earthen_spirit',
    'malum:infernal_spirit'
]

function bcHideNativeOrdinarySpirits(event) {
    BC_NATIVE_ORDINARY_SPIRITS.forEach(function (id) { event.hide(id) })
}

JEIEvents.hideItems(function (event) { bcHideNativeOrdinarySpirits(event) })
if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideItems(function (event) { bcHideNativeOrdinarySpirits(event) })
}

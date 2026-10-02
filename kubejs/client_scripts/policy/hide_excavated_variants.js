// Excavated Variants ores remain obtainable and keep their recipes, but the
// hundreds of host-stone permutations add no useful entries to recipe viewers.
function bcHideExcavatedVariants(event) {
    event.hide('@excavated_variants')
}

JEIEvents.hideItems(function (event) {
    bcHideExcavatedVariants(event)
})

if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideItems(function (event) {
        bcHideExcavatedVariants(event)
    })
}

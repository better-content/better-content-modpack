// With EMI, Better Ore Geology removes these from the item INDEX only.
// Do not disable the stacks here: hosted-ore conversion recipes and R/U lookup
// must remain available. Retain the old hiding policy for JEI-only clients.
function bcHideExcavatedVariants(event) {
    event.hide('@excavated_variants')
}

if (!Platform.isLoaded('emi')) {
    JEIEvents.hideItems(function (event) {
        bcHideExcavatedVariants(event)
    })
}

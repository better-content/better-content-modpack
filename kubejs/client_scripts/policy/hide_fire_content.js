// Generated burnt and smoldering block states remain available to the
// world simulation, but do not need standalone entries in recipe viewers.
function bcHideFireContent(event) {
    event.hide('@better_wildfire')
}

JEIEvents.hideItems(function (event) {
    bcHideFireContent(event)
})

if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideItems(function (event) {
        bcHideFireContent(event)
    })
}

// Burnt's many transient and damaged block states remain available to its
// world simulation, but do not need standalone entries in recipe viewers.
function bcHideBurntContent(event) {
    event.hide('@burnt')
}

JEIEvents.hideItems(function (event) {
    bcHideBurntContent(event)
})

if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideItems(function (event) {
        bcHideBurntContent(event)
    })
}

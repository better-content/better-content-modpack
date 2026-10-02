// STORE-01: Sophisticated Backpacks jukebox upgrades are disabled. The
// sophisticatedcore enabledItems list hides and unregisters both upgrade
// items; removing their recipes closes the remaining acquisition surface.
// Vanilla jukebox blocks and Sophisticated Storage jukebox settings remain
// untouched.
ServerEvents.recipes(function (event) {
    event.remove({ id: 'sophisticatedbackpacks:jukebox_upgrade' })
    event.remove({ id: 'sophisticatedbackpacks:advanced_jukebox_upgrade' })
})

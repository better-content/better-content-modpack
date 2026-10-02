// These selected components use meteor Certus Quartz in their existing quartz
// slots. Keep each provider's native recipe, shape, and other ingredients.
ServerEvents.recipes(function (event) {
    var certus = 'ae2:certus_quartz_crystal'
    var directQuartz = [
        'minecraft:daylight_detector',
        'minecraft:comparator',
        'minecraft:observer',
        'powergrid:crafting/barretter_tube'
    ]
    var taggedQuartz = [
        'pneumaticcraft:dispenser_upgrade',
        'rats:upgrades/elite_energy_upgrade',
        'oc2r:floppy',
        'oc2r:floppy_modern',
        'create:crafting/materials/rose_quartz'
    ]

    directQuartz.forEach(function (id) {
        event.replaceInput({ id: id }, 'minecraft:quartz', certus)
    })
    taggedQuartz.forEach(function (id) {
        event.replaceInput({ id: id }, '#forge:gems/quartz', certus)
    })
})

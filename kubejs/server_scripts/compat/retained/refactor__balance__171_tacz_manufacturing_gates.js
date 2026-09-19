// TaCZ is a factory-era weapon surface. Gate the shared benches and each
// Armorer pack at the technology milestone represented by that pack.
// Keep explicit NBT results: TaCZ uses one workbench item for several blocks.

function bcTaczWorkbench(event, nativeId, recipeId, item, blockId, pattern, key) {
    // Armorer namespaces come from TaCZ gun-pack ZIPs, not Forge mod IDs.
    if (!Platform.isLoaded('tacz')) return
    event.remove({ id: nativeId })
    event.custom({
        type: 'minecraft:crafting_shaped',
        pattern: pattern,
        key: global.bcRecipeKey(key),
        result: {
            item: item,
            nbt: { BlockId: blockId }
        }
    }).id(recipeId)
}

ServerEvents.recipes(function (event) {
    // The general gunsmith is the entry gate for TaCZ's bundled gun catalog.
    event.remove({ id: 'tacz:gun_smith_table' })
    global.bcFactoryCrafting(event, 'kubejs:tacz/gun_smith_table_factory_gate', 'tacz:gun_smith_table', 1, [
        'PPP',
        'TCT',
        'I I'
    ], {
        P: '#minecraft:planks',
        T: '#forge:plates/iron',
        C: 'create:brass_casing',
        I: '#forge:ingots/iron'
    }, true)

    bcTaczWorkbench(event, 'tacz:ammo_workbench', 'kubejs:tacz/ammo_workbench_factory_gate',
        'tacz:workbench_a', 'tacz:ammo_workbench', [
            'CBC',
            'PAP',
            ' I '
        ], {
            C: '#forge:plates/copper',
            B: 'tacz:ammo_box',
            P: '#forge:plates/iron',
            A: 'kubejs:brass_utility_assembly',
            I: 'tacz:gun_smith_table'
        })

    bcTaczWorkbench(event, 'tacz:attachment_workbench', 'kubejs:tacz/attachment_workbench_factory_gate',
        'tacz:workbench_c', 'tacz:attachment_workbench', [
            'GPG',
            'ACA',
            ' I '
        ], {
            G: '#forge:glass_panes',
            P: 'create:precision_mechanism',
            A: 'kubejs:brass_utility_assembly',
            C: 'create:brass_casing',
            I: 'tacz:gun_smith_table'
        })

    bcTaczWorkbench(event, 'create_armorer:create_workbench', 'kubejs:tacz/create_armorer_workbench_gate',
        'tacz:workbench_b', 'create_armorer:create_workbench', [
            'PMP',
            'BCB',
            ' I '
        ], {
            P: '#forge:plates/brass',
            M: 'create:precision_mechanism',
            B: 'create:brass_casing',
            C: 'create:brass_casing',
            I: 'tacz:gun_smith_table'
        })

    bcTaczWorkbench(event, 'applied_armorer:worckbench_applied_armorer', 'kubejs:tacz/applied_armorer_workbench_gate',
        'tacz:workbench_c', 'applied_armorer:worckbench_applied_armorer', [
            'SLS',
            'AIA',
            ' C '
        ], {
            S: 'kubejs:sky_steel_sheet',
            L: 'kubejs:ae_logic_package',
            A: 'ae2:engineering_processor',
            I: 'ae2:engineering_processor',
            C: 'ae2:controller'
        })

    bcTaczWorkbench(event, 'immersive_armorer:workbench', 'kubejs:tacz/immersive_armorer_workbench_gate',
        'tacz:workbench_b', 'immersive_armorer:workbench', [
            'PMP',
            'ECE',
            ' I '
        ], {
            P: '#forge:plates/iron',
            M: 'powergrid:electric_motor',
            E: 'kubejs:electrical_instrumentation_module',
            C: 'powergrid:conductive_casing',
            I: 'tacz:gun_smith_table'
        })
})

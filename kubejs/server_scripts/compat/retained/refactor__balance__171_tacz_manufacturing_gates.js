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
    if (!Platform.isLoaded('tacz')) return
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

    // The active TaCZ ZIPs use the tacz:gun_smith_table_crafting data type.
    // These exact IDs are the default pack's representative gun, ammunition,
    // and optic surfaces; manufactured components carry the stage into the
    // workbench instead of turning the bench into a universal hand-grid gate.
    event.remove({ id: 'tacz:gun/deagle' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:electrical_instrumentation_module' }, count: 1 },
            { item: { item: 'kubejs:brass_utility_assembly' }, count: 2 },
            { item: { tag: 'forge:ingots/steel' }, count: 6 },
            { item: { item: 'ae2:engineering_processor' }, count: 1 }
        ],
        result: { type: 'gun', id: 'tacz:deagle' }
    }).id('kubejs:tacz/gun/deagle_manufactured_frame')

    event.remove({ id: 'tacz:ammo/338' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:brass_utility_assembly' }, count: 1 },
            { item: { tag: 'forge:plates/copper' }, count: 8 },
            { item: { tag: 'forge:gunpowder' }, count: 8 },
            { item: { item: 'powergrid:integrated_circuit' }, count: 1 }
        ],
        result: { type: 'ammo', group: 'lc_specialized', id: 'tacz:338', count: 18 }
    }).id('kubejs:tacz/ammo/338_electrical_loading')

    event.remove({ id: 'tacz:attachments/scope_vudu' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:ae_logic_package' }, count: 1 },
            { item: { item: 'kubejs:mountain_beryl_lens' }, count: 1 },
            { item: { item: 'powergrid:integrated_circuit' }, count: 1 },
            { item: { tag: 'forge:dusts/glowstone' }, count: 4 }
        ],
        result: { type: 'attachment', id: 'tacz:scope_vudu' }
    }).id('kubejs:tacz/attachments/scope_vudu_ae_optics')

    // Explicit external-pack overrides. Their ZIP JSON uses nested `materials`
    // entries, so each override names the exact recipe and keeps the native
    // TaCZ result type/count while introducing manufactured stage components.
    event.remove({ id: 'create_armorer:ammo/40mmhe' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:electrical_instrumentation_module' }, count: 1 },
            { item: { tag: 'forge:ingots/copper' }, count: 8 },
            { item: { tag: 'forge:gunpowder' }, count: 12 },
            { item: { item: 'powergrid:integrated_circuit' }, count: 1 }
        ],
        result: { type: 'ammo', id: 'create_armorer:40mmhe', count: 12 }
    }).id('kubejs:tacz/create_armorer/40mmhe_manufactured')

    event.remove({ id: 'applied_armorer:ammo/fluix_battery' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:ae_logic_package' }, count: 1 },
            { item: { tag: 'forge:dusts/redstone' }, count: 16 }
        ],
        result: { type: 'ammo', id: 'applied_armorer:fluix_battery', count: 4 }
    }).id('kubejs:tacz/applied_armorer/fluix_battery_ae_package')

    event.remove({ id: 'immersive_armorer:ammo/burst_capacitor' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { tag: 'forge:glass' }, count: 4 },
            { item: { item: 'powergrid:integrated_circuit' }, count: 1 },
            { item: { tag: 'forge:ingots/copper' }, count: 3 }
        ],
        result: { type: 'ammo', id: 'immersive_armorer:burst_capacitor', count: 5 }
    }).id('kubejs:tacz/immersive_armorer/burst_capacitor_control')

    event.remove({ id: 'create_armorer:gun/pistol_auto_stress' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:brass_utility_assembly' }, count: 2 },
            { item: { item: 'create:precision_mechanism' }, count: 1 },
            { item: { tag: 'forge:ingots/copper' }, count: 12 },
            { item: { tag: 'minecraft:logs' }, count: 5 }
        ],
        result: { type: 'gun', id: 'create_armorer:pistol_auto_stress' }
    }).id('kubejs:tacz/create_armorer/pistol_auto_stress_precision_frame')

    event.remove({ id: 'applied_armorer:ammo/cluster_quartz_bullet' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:ae_logic_package' }, count: 1 },
            { item: { tag: 'forge:ingots/copper' }, count: 12 },
            { item: { tag: 'forge:gems/lapis' }, count: 2 },
            { item: { tag: 'forge:gunpowder' }, count: 4 }
        ],
        result: { type: 'ammo', id: 'applied_armorer:cluster_quartz_bullet', count: 24 }
    }).id('kubejs:tacz/applied_armorer/cluster_quartz_bullet_ae_loading')

    event.remove({ id: 'immersive_armorer:attachments/muzzle_refit_electromagnetic_accelerator' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:electrical_instrumentation_module' }, count: 1 },
            { item: { item: 'powergrid:electric_motor' }, count: 1 },
            { item: { tag: 'forge:ingots/iron' }, count: 4 },
            { item: { tag: 'forge:ingots/gold' }, count: 8 }
        ],
        result: { type: 'attachment', id: 'immersive_armorer:muzzle_refit_electromagnetic_accelerator' }
    }).id('kubejs:tacz/immersive_armorer/electromagnetic_accelerator_powergrid')

    // These two visible ZIP-indexed muzzles have real combat modifiers and are
    // allowlisted on authored guns, but the pinned Armorer packs ship no recipe.
    event.remove({ id: 'applied_armorer:attachments/muzzle_commander' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:ae_logic_package' }, count: 1 },
            { item: { item: 'ae2:engineering_processor' }, count: 1 },
            { item: { tag: 'forge:ingots/gold' }, count: 8 },
            { item: { tag: 'forge:gems/amethyst' }, count: 4 }
        ],
        result: { type: 'attachment', id: 'applied_armorer:muzzle_commander' }
    }).id('kubejs:tacz/applied_armorer/muzzle_commander_ae_control')

    // Both visible Armorer melee guns declare these ammo IDs in their gun data.
    // TaCZ consumes them on its shoot path even though the separate melee action
    // does not require ammunition.
    event.remove({ id: 'applied_armorer:ammo/melee' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:ae_logic_package' }, count: 1 },
            { item: { tag: 'forge:ingots/iron' }, count: 2 },
            { item: { tag: 'forge:gems/amethyst' }, count: 1 }
        ],
        result: { type: 'ammo', id: 'applied_armorer:melee', count: 1 }
    }).id('kubejs:tacz/applied_armorer/melee_ammo_ae_package')

    event.remove({ id: 'create_armorer:attachments/muzzle_refit_ap_grenade' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:electrical_instrumentation_module' }, count: 1 },
            { item: { item: 'kubejs:brass_utility_assembly' }, count: 1 },
            { item: { item: 'create:precision_mechanism' }, count: 2 },
            { item: { tag: 'forge:ingots/iron' }, count: 8 },
            { item: { tag: 'forge:gunpowder' }, count: 8 },
            { item: { item: 'minecraft:netherite_scrap' }, count: 1 }
        ],
        result: { type: 'attachment', id: 'create_armorer:muzzle_refit_ap_grenade' }
    }).id('kubejs:tacz/create_armorer/muzzle_refit_ap_grenade_electrical')

    event.remove({ id: 'create_armorer:ammo/melee_weapon' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:brass_utility_assembly' }, count: 1 },
            { item: { tag: 'forge:ingots/iron' }, count: 2 },
            { item: { item: 'create:andesite_alloy' }, count: 1 }
        ],
        result: { type: 'ammo', id: 'create_armorer:melee_weapon', count: 1 }
    }).id('kubejs:tacz/create_armorer/melee_weapon_ammo_assembly')


    event.remove({ id: 'create_armorer:ammo/gas_pistol_ammo' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:brass_utility_assembly' }, count: 1 },
            { item: { tag: 'forge:ingots/copper' }, count: 8 },
            { item: { tag: 'forge:gunpowder' }, count: 2 }
        ],
        result: { type: 'ammo', id: 'create_armorer:gas_pistol_ammo', count: 60 }
    }).id('kubejs:tacz/create_armorer/gas_pistol_ammo_brass_loading')

    event.remove({ id: 'applied_armorer:ammo/etched_quartz_bullet' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:ae_logic_package' }, count: 1 },
            { item: { tag: 'forge:ingots/copper' }, count: 8 },
            { item: { tag: 'forge:gems/lapis' }, count: 2 },
            { item: { tag: 'forge:gunpowder' }, count: 2 }
        ],
        result: { type: 'ammo', id: 'applied_armorer:etched_quartz_bullet', count: 60 }
    }).id('kubejs:tacz/applied_armorer/etched_quartz_bullet_ae_loading')

    event.remove({ id: 'immersive_armorer:ammo/20mm' })
    event.custom({
        type: 'tacz:gun_smith_table_crafting',
        materials: [
            { item: { item: 'kubejs:electrical_instrumentation_module' }, count: 1 },
            { item: { item: 'powergrid:integrated_circuit' }, count: 1 },
            { item: { tag: 'forge:ingots/copper' }, count: 24 },
            { item: { tag: 'forge:ingots/gold' }, count: 8 },
            { item: { tag: 'forge:gunpowder' }, count: 12 },
            { item: { item: 'minecraft:netherite_scrap' }, count: 1 }
        ],
        result: { type: 'ammo', id: 'immersive_armorer:20mm', count: 24 }
    }).id('kubejs:tacz/immersive_armorer/20mm_electrical_loading')


    event.remove({ id: 'applied_armorer:gun/moritz_gernade_gl3' })
    event.custom({ type: 'tacz:gun_smith_table_crafting', materials: [
        { item: { item: 'kubejs:ae_logic_package' }, count: 1 },
        { item: { tag: 'forge:ingots/iron' }, count: 48 },
        { item: { tag: 'forge:gems/quartz' }, count: 16 },
        { item: { tag: 'forge:gems/diamond' }, count: 2 }
    ], result: { type: 'gun', id: 'applied_armorer:moritz_gernade_gl3' } }).id('kubejs:tacz/applied_armorer/moritz_gernade_gl3_ae_frame')

    event.remove({ id: 'create_armorer:gun/gl_revolver_devastator' })
    event.custom({ type: 'tacz:gun_smith_table_crafting', materials: [
        { item: { item: 'kubejs:brass_utility_assembly' }, count: 2 },
        { item: { item: 'create:precision_mechanism' }, count: 1 },
        { item: { tag: 'forge:ingots/iron' }, count: 36 },
        { item: { tag: 'forge:ingots/copper' }, count: 12 },
        { item: { tag: 'minecraft:logs' }, count: 8 }
    ], result: { type: 'gun', id: 'create_armorer:gl_revolver_devastator' } }).id('kubejs:tacz/create_armorer/gl_revolver_devastator_precision_frame')

    event.remove({ id: 'immersive_armorer:gun/assult_rifle' })
    event.custom({ type: 'tacz:gun_smith_table_crafting', materials: [
        { item: { item: 'kubejs:electrical_instrumentation_module' }, count: 1 },
        { item: { item: 'powergrid:integrated_circuit' }, count: 1 },
        { item: { tag: 'forge:ingots/iron' }, count: 48 },
        { item: { tag: 'forge:ingots/copper' }, count: 16 },
        { item: { tag: 'minecraft:planks' }, count: 16 }
    ], result: { type: 'gun', id: 'immersive_armorer:assult_rifle' } }).id('kubejs:tacz/immersive_armorer/assult_rifle_electrical_frame')

})

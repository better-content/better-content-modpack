// Mobility and autonomous helper tools are powerful route-editing utilities.
// Keep them off the hand grid; tier hooks and Create Stuff & Additions drones
// through mechanical assembly. The Soap on a Rope prototype is supplied only as early loot.

function bcMobilityExists(id) {
    try { return Item.exists(id) } catch (e) { return false }
}

function bcMobilityMechanical(event, output, pattern, key, recipeId) {
    if (!bcMobilityExists(output)) return
    event.remove({ output: output })
    global.bcFactoryCrafting(event, recipeId, output, 1, pattern, key, true)
}

ServerEvents.recipes(function (event) {
     bcMobilityMechanical(event, 'rehooked:wood_chain', [
        ' SS',
        'SCS',
        'SS '
    ], {
        S: '#forge:rods/wooden',
        C: 'tconstruct:seared_bricks'
    }, 'kubejs:rehooked/wood_chain_post_seared')

     if (bcMobilityExists('rehooked:wood_hook')) event.remove({ output: 'rehooked:wood_hook' })

     bcMobilityMechanical(event, 'rehooked:iron_hook', [
        'IIC',
        ' HI',
        'L I'
    ], {
        I: '#forge:plates/iron',
        C: 'create:andesite_casing',
        H: 'better_content_fixes:soap_on_a_rope',
        L: 'minecraft:chain'
    }, 'kubejs:rehooked/iron_hook_post_create')

     bcMobilityMechanical(event, 'rehooked:diamond_chain', [
        ' DD',
        'DCD',
        'DD '
    ], {
        D: '#forge:gems/diamond',
        C: 'create:brass_casing'
    }, 'kubejs:rehooked/diamond_chain_post_brass')

     bcMobilityMechanical(event, 'rehooked:diamond_hook', [
        'DDC',
        ' HI',
        'L I'
    ], {
        D: '#forge:gems/diamond',
        C: 'create:brass_casing',
        H: 'rehooked:iron_hook',
        I: '#forge:plates/iron',
        L: 'rehooked:diamond_chain'
    }, 'kubejs:rehooked/diamond_hook_post_brass')

     bcMobilityMechanical(event, 'rehooked:blaze_hook', [
        ' H ',
        'BPC',
        ' H '
    ], {
        H: 'minecraft:blaze_rod',
        B: 'rehooked:diamond_hook',
        P: 'powergrid:conductive_casing',
        C: 'heat_sync:heat_pipe'
    }, 'kubejs:rehooked/blaze_hook_post_electricity')

     bcMobilityMechanical(event, 'rehooked:ender_hook', [
        ' E ',
        'HSH',
        ' E '
    ], {
        E: 'minecraft:ender_pearl',
        H: 'rehooked:blaze_hook',
        S: 'creatingspace:rocket_casing'
    }, 'kubejs:rehooked/ender_hook_post_space')

     bcMobilityMechanical(event, 'rehooked:red_hook', [
        'QAQ',
        'HRH',
        'QAQ'
    ], {
        Q: 'kubejs:sky_steel_sheet',
        A: 'ae2:engineering_processor',
        H: 'rehooked:ender_hook',
        R: 'kubejs:ae_logic_package'
    }, 'kubejs:rehooked/red_hook_post_ae2')

     bcMobilityMechanical(event, 'create_sa:brass_drone', [
        'QPQ',
        'DAD',
        'QSQ'
    ], {
        Q: 'kubejs:sky_steel_sheet',
        P: 'create:precision_mechanism',
        D: 'create:brass_sheet',
        A: 'ae2:engineering_processor',
        S: 'create_sa:zinc_handle'
    }, 'kubejs:create_sa/brass_drone_post_ae2')
})

// Exact Ratlantis-only entries with no retained acquisition route. Ordinary
// rats, cheese, upgrades, taming supplies and the replacement archaeology
// recipes remain visible.
var BC_RETIRED_RATLANTIS_ITEMS = [
    'rats:chunky_cheese_token', 'rats:ratlantis_portal',
    'rats:gem_of_ratlantis', 'rats:idol_of_ratlantis',
    'rats:oratchalcum_ore', 'rats:oratchalcum_ingot',
    'rats:oratchalcum_nugget', 'rats:oratchalcum_block',
    'rats:ghost_pirat_ectoplasm', 'rats:ghost_pirat_cutlass',
    'rats:ghost_pirat_hat', 'rats:pirat_cutlass', 'rats:pirat_hat',
    'rats:ratglove_flower', 'rats:ratglove_petals',
    'rats:ratlantis_axe', 'rats:ratlantis_bow',
    'rats:ratlantis_boots', 'rats:ratlantis_chestplate',
    'rats:ratlantis_helmet', 'rats:ratlantis_hoe',
    'rats:ratlantis_leggings', 'rats:ratlantis_pickaxe',
    'rats:ratlantis_reactor', 'rats:ratlantis_shovel',
    'rats:ratlantis_sword', 'rats:ratlantis_upgrade_block',
    'rats:ratlantis_rat_skull', 'rats:pirat_sapling',
    'rats:pirat_log', 'rats:pirat_wood', 'rats:pirat_planks'
]

function bcHideRetiredRatlantis(event) {
    BC_RETIRED_RATLANTIS_ITEMS.forEach(function (id) {
        if (Item.exists(id)) event.hide(id)
    })
}

JEIEvents.hideItems(function (event) { bcHideRetiredRatlantis(event) })
if (Platform.isLoaded('emi') && typeof EMIEvents !== 'undefined') {
    EMIEvents.hideItems(function (event) { bcHideRetiredRatlantis(event) })
}

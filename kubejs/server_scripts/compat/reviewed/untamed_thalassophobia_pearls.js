// Keep the live Thalassophobia clam as the natural pearl source for Untamed recipes.
ServerEvents.recipes(event => {
    event.shapeless('untamedwilds:material_pearl', ['thalassophobia:pearl'])
        .id('kubejs:untamed_pearl_from_thalassophobia')
    event.shapeless('untamedwilds:material_giant_pearl', ['thalassophobia:black_pearl'])
        .id('kubejs:untamed_giant_pearl_from_thalassophobia')
})

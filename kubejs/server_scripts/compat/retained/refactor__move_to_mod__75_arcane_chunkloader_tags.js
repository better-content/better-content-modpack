// Interchangeable early magical proof for the pack-owned chunk-anchor family.
ServerEvents.tags('item', function (event) {
    event.add('better_magic_chunk_anchors:magic_catalysts', [
        'ars_nouveau:source_gem',
        'bloodmagic:blankslate',
        'hexerei:blood_sigil',
        'occultism:spirit_attuned_gem',
        'malum:processed_soulstone',
        'goety:magic_emerald'
    ])
})

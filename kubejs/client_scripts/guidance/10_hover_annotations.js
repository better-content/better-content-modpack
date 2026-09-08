// Better Content's single item-local annotation surface. EMI uses the normal
// tooltip pipeline, so the same concise facts appear there and in inventories.

var BC_HOVER_DATA = JsonIO.read('kubejs/config/hover_annotations.json') || {}
var BC_FORMAL_DATA = JsonIO.read('kubejs/config/formal_magic_domains.json') || {}
var BC_COMBAT_DATA = JsonIO.read('kubejs/config/tcon_edps_catalogue.json') || {}

var BC_HOVER_CATEGORIES = {
    correction: true,
    lifecycle_state: true,
    hidden_composition: true,
    general_uses: true,
    capability_root: true,
    process_authority: true,
    operation_contract: true,
    requirement_limit: true,
    provenance: true,
    scope_boundary: true,
    persistence_consequence: true,
    economy_semantics: true,
    combat_handling: true
}

function bcHoverWarn(message) {
    console.warn('[hover-annotations] ' + message)
}

function bcHoverWords(lines) {
    var joined = lines.join(' ').trim()
    return joined.length === 0 ? 0 : joined.split(/\s+/).length
}

function bcHoverValidLines(lines, label) {
    if (!Array.isArray(lines) || lines.length < 1 || lines.length > 2) {
        bcHoverWarn(label + ' must define one or two lines')
        return false
    }
    for (var i = 0; i < lines.length; i++) {
        if (typeof lines[i] !== 'string' || lines[i].trim().length === 0) {
            bcHoverWarn(label + ' contains an empty or non-text line')
            return false
        }
    }
    if (bcHoverWords(lines) > 24) {
        bcHoverWarn(label + ' exceeds the 24-word stack limit')
        return false
    }
    return true
}

function bcHoverTextLines(rawLines) {
    if (!Array.isArray(rawLines)) return null
    var lines = []
    for (var i = 0; i < rawLines.length; i++) lines.push(String(rawLines[i]))
    return lines
}

function bcHoverStyle(line, tone) {
    return tone === 'warning' ? Text.red(line) : Text.darkGray(line)
}

var BC_HOVER_STATIC = []
var BC_HOVER_EXACT_TARGETS = {}

function bcHoverAddExact(target, lines, tone, conceptId, label) {
    if (BC_HOVER_EXACT_TARGETS[target]) {
        bcHoverWarn(label + ' duplicates target ' + target)
        return
    }
    BC_HOVER_EXACT_TARGETS[target] = true
    BC_HOVER_STATIC.push({ target: target, lines: lines, tone: tone, conceptId: conceptId })
}

function bcHoverCompileStatic() {
    if (String(BC_HOVER_DATA.schema || '') !== 'bc.hover_annotations.v2') {
        bcHoverWarn('unsupported or missing registry schema')
        return
    }
    var annotations = BC_HOVER_DATA.annotations
    if (!Array.isArray(annotations)) {
        bcHoverWarn('registry annotations must be an array')
        return
    }
    for (var i = 0; i < annotations.length; i++) {
        var row = annotations[i] || {}
        var label = 'static entry ' + i
        var category = String(row.category || '')
        var conceptId = String(row.concept_id || '')
        var domain = String(row.domain || '')
        var owner = String(row.owner || '')
        if (!BC_HOVER_CATEGORIES[category] || !/^[a-z0-9_.]{3,96}$/.test(conceptId) || !domain || !owner) {
            bcHoverWarn(label + ' has invalid authoring metadata')
            continue
        }
        var lines = bcHoverTextLines(row.lines)
        if (!bcHoverValidLines(lines, label)) continue
        if (row.required_mod && !Platform.isLoaded(String(row.required_mod))) continue

        var selector = row.selector || {}
        var kinds = (selector.item ? 1 : 0) + (selector.items ? 1 : 0) + (selector.tag ? 1 : 0)
        if (kinds !== 1) {
            bcHoverWarn(label + ' must define exactly one selector kind')
            continue
        }
        if (selector.item) {
            bcHoverAddExact(String(selector.item), lines, String(row.tone || ''), conceptId, label)
        } else if (Array.isArray(selector.items) && selector.items.length > 0) {
            for (var j = 0; j < selector.items.length; j++) {
                bcHoverAddExact(String(selector.items[j]), lines, String(row.tone || ''), conceptId, label)
            }
        } else if (selector.tag) {
            BC_HOVER_STATIC.push({ target: '#' + String(selector.tag), lines: lines, tone: String(row.tone || ''), conceptId: conceptId })
        } else {
            bcHoverWarn(label + ' has an empty selector')
        }
    }
}

function bcFormalGlyphId(rawName) {
    var value = String(rawName)
    return value.indexOf(':') >= 0 ? value : 'ars_nouveau:glyph_' + value
}

function bcFormalGlyphKey(itemId) {
    return itemId.substring(itemId.indexOf(':') + 1).replace(/^glyph_/, '')
}

function bcFormalOriginName(origin) {
    var key = String(origin)
    var names = {
        core: 'Ars grammar',
        hexerei: 'Hexerei',
        occultism: 'Occultism',
        blood_magic: 'Blood Magic',
        malum: 'Malum',
        goety: 'Goety'
    }
    return names[key] || key
}

var BC_HOVER_FORMAL = []

function bcHoverCompileFormal() {
    if (String(BC_FORMAL_DATA.schema || '') !== 'bc.formal_magic_domains.v3') {
        bcHoverWarn('unsupported formal-magic schema')
        return
    }
    var quarantined = {}
    var quarantinedRows = BC_FORMAL_DATA.quarantined_glyphs || []
    for (var q = 0; q < quarantinedRows.length; q++) quarantined[String(quarantinedRows[q].id)] = true

    var overrides = BC_FORMAL_DATA.origin_overrides || {}
    var glyphs = (BC_FORMAL_DATA.glyphs || []).concat(BC_FORMAL_DATA.addon_glyphs || [])
    for (var i = 0; i < glyphs.length; i++) {
        var spec = glyphs[i]
        if (!Array.isArray(spec) || spec.length < 3) {
            bcHoverWarn('formal glyph entry ' + i + ' is malformed')
            continue
        }
        var itemId = bcFormalGlyphId(spec[0])
        if (quarantined[itemId]) continue
        if (BC_HOVER_EXACT_TARGETS[itemId]) {
            bcHoverWarn('formal glyph duplicates static target ' + itemId)
            continue
        }
        var origins = overrides[bcFormalGlyphKey(itemId)] || [spec[2]]
        var labels = []
        for (var o = 0; o < origins.length; o++) labels.push(bcFormalOriginName(origins[o]))
        var line = (labels.length === 1 ? 'Origin: ' : 'Origins: ') + labels.join(' · ')
        if (!bcHoverValidLines([line], 'formal glyph ' + itemId)) continue
        BC_HOVER_EXACT_TARGETS[itemId] = true
        BC_HOVER_FORMAL.push({ target: itemId, line: line })
    }
}

var BC_COMBAT_TYPES = {
    'epicfight:axe': 'Axe',
    'epicfight:fist': 'Fist',
    'epicfight:greatsword': 'Greatsword',
    'epicfight:pickaxe': 'Pickaxe',
    'epicfight:shovel': 'Shovel',
    'epicfight:sword': 'Sword',
    'epicfight:hoe': 'Sickle',
    'epicfight:bow': 'Bow',
    'epicfight:crossbow': 'Crossbow',
    'epicfight:dagger': 'Dagger',
    'epicfight:longsword': 'Longsword',
    'epicfight:spear': 'Spear',
    'epicfight:tachi': 'Katana',
    'epicfight:trident': 'Trident',
    'epicfighttinkercompat:longbow': 'Longbow',
    'epicfighttinkercompat:crossbow': 'Heavy crossbow',
    'epicfighttinkercompat:swasher': 'Light crossbow'
}

function bcCombatLabel(type, preset) {
    if (type === 'epicfight:tachi' && preset === 'twin_blade') return 'Twin blade'
    if (type === 'epicfight:tachi' && preset === 'coral_blade') return 'Curved blade'
    return BC_COMBAT_TYPES[type]
}

function bcCombatAlternate(mode) {
    var labels = {
        'war_charge projectile': 'charge projectile',
        'charged Bonk': 'charged Bonk',
        'returning throw': 'returning throw',
        throw: 'throw'
    }
    return labels[mode] || String(mode).replace(/_/g, ' ')
}

var BC_HOVER_COMBAT = []

function bcHoverCompileCombatGroup(rows, workFirst) {
    for (var i = 0; i < rows.length; i++) {
        var row = rows[i] || {}
        var itemId = String(row.item || '')
        if (!itemId) continue
        var split = itemId.split(':')
        var capability = JsonIO.read('kubejs/data/' + split[0] + '/capabilities/weapons/' + split[1] + '.json') || {}
        var label = bcCombatLabel(String(capability.type || ''), String(row.preset || ''))
        if (!label) {
            bcHoverWarn('combat item ' + itemId + ' has no player-facing handling form')
            continue
        }
        if (BC_HOVER_EXACT_TARGETS[itemId]) {
            bcHoverWarn('combat item duplicates target ' + itemId)
            continue
        }
        var line = 'Combat handling: ' + label
        if (row.alternate && row.alternate.mode) line += ' · alternate: ' + bcCombatAlternate(row.alternate.mode)
        line += workFirst ? '. Work-first tool.' : '.'
        if (!bcHoverValidLines([line], 'combat item ' + itemId)) continue
        BC_HOVER_EXACT_TARGETS[itemId] = true
        BC_HOVER_COMBAT.push({ target: itemId, line: line })
    }
}

function bcHoverCompileCombat() {
    if (String(BC_COMBAT_DATA.schema || '') !== 'bc.tcon_edps_catalogue.v1') {
        bcHoverWarn('unsupported combat catalogue schema')
        return
    }
    bcHoverCompileCombatGroup(BC_COMBAT_DATA.tools || [], true)
    bcHoverCompileCombatGroup(BC_COMBAT_DATA.weapons || [], false)
}

bcHoverCompileStatic()
bcHoverCompileFormal()
bcHoverCompileCombat()
console.info('[hover-annotations] compiled ' + BC_HOVER_STATIC.length + ' static selectors, '
    + BC_HOVER_FORMAL.length + ' formal glyphs, and ' + BC_HOVER_COMBAT.length + ' combat items')

ItemEvents.tooltip(function (event) {
    for (var i = 0; i < BC_HOVER_STATIC.length; i++) {
        var row = BC_HOVER_STATIC[i]
        for (var l = 0; l < row.lines.length; l++) event.add(row.target, bcHoverStyle(row.lines[l], row.tone))
    }
    for (var f = 0; f < BC_HOVER_FORMAL.length; f++) {
        event.add(BC_HOVER_FORMAL[f].target, Text.darkGray(BC_HOVER_FORMAL[f].line))
    }
    for (var c = 0; c < BC_HOVER_COMBAT.length; c++) {
        event.add(BC_HOVER_COMBAT[c].target, Text.darkGray(BC_HOVER_COMBAT[c].line))
    }
})

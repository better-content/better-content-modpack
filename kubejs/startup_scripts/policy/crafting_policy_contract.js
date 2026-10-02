var BC_CRAFTING_POLICY = JsonIO.read('kubejs/config/crafting_policy.json')
if (!BC_CRAFTING_POLICY || BC_CRAFTING_POLICY.schema !== 'bc.crafting_policy.v1') {
    throw new Error('[crafting-policy] missing or invalid bc.crafting_policy.v1 contract')
}

var bcLoadedNamespaces = Platform.getList().toArray()
var bcUnknownNamespaces = []
for (var i = 0; i < bcLoadedNamespaces.length; i++) {
    var namespace = String(bcLoadedNamespaces[i])
    var classification = BC_CRAFTING_POLICY.namespaces[namespace]
    if (!classification || !classification.primary_role || !classification.support_state) {
        bcUnknownNamespaces.push(namespace)
    }
}
if (bcUnknownNamespaces.length > 0) {
    bcUnknownNamespaces.sort()
    throw new Error('[crafting-policy] unclassified loaded namespaces: ' + bcUnknownNamespaces.join(', '))
}
console.info('[crafting-policy] classified all ' + bcLoadedNamespaces.length + ' loaded namespaces')

#!/usr/bin/env python3
"""Author source-level promise/oracle review draft; NEVER run tests or infer packaged source equality."""
import argparse
import hashlib
import json
from pathlib import Path

# Manually bounded corrections to lexical source candidates. Retired tests and dormant gameplay
# are not promoted to active native acceptance merely because their source annotations exist.
OVERRIDES = {
    'better-abyssal-city': ('src/main/java/com/bettercontent/abyssal/MetropolisGameTests.java', 'metropolisGenerationContract',
                            'Real v12 metropolis dimension/generator missing', 'actual registered v12 GameTest; old StructuralGameTests retired'),
    'better-cooking': ('src/test/java/com/bettercontent/bettercooking/policy/CulinaryPolicyTest.java',
                       'incompleteBundledCatalogueLoadsWithoutClaimingGameplayCoverage', 'qualified()',
                       'incomplete catalogue pass-through; native placed-food test is dormant policy, not shipped food gameplay'),
    'better-mining-lamp': ('src/main/java/com/bettercontent/bettermininglamp/MiningHelmetGameTests.java',
                           'registryDurabilityRepairAndRecipeAreLive', 'getRecipeManager().byKey',
                           'native registered helmet and recipe presence; natural craft/Curios wear still separately required'),
    'better-runtime-diagnostics': ('src/test/java/com/bettercontent/betterruntimediagnostics/diagnostics/PlaytestLogContractTest.java',
                                    'globalBudgetSummariesAndTeardownDoNotRearmAllowance', 'burst is not twenty',
                                    'bounded pure sink/clock; native server lifecycle and log impacts separate'),
    'better-spaceflight': ('src/test/java/com/bettercontent/spaceflight/vs/SectionLayoutTest.java',
                            'rejectsNetherHeightBeforeNativeCopy', 'requireCompatible',
                            'production version-pinned geometry guard only; probe survival flight is nonshipping'),
    'better-tinkers-loot-affixes': ('src/main/kotlin/com/bettercontent/bettertinkerslootaffixes/TConAffixGameTests.kt',
                                   'cacheUseProducesOneStampedPartAndConsumesOneCache', 'A successful cache use must consume exactly one cache',
                                   'native fixture grants cache directly; not natural acquisition proof'),
}
SUMMARIES = {
'better-abyssal-city':'The active v12 test rejects a missing metropolis dimension/generator; retired v11 structural tests do not establish v12.',
'better-airtight-machines':'An upgrade installs and a sneaking removal consumes exactly one owned upgrade.',
'better-arena-trials':'Native arena containment uses 3-D terrain range, not horizontal-only distance.',
'better-block-placement-preview':'Invalid vanilla placement stays red regardless of structural verdict.',
'better-bumblezone-crops':'Tall flower halves and sniffer loot do not produce off-origin seeds.',
'better-buried-encounters':'A sealed block opened by TNT releases its mob once rather than leaving the sealed block.',
'better-cave-encounters':'Saved director lineage round-trips versioned policy and credits without dropping family identity.',
'better-celestial-events':'Required event entity types appear in native Forge registry.',
'better-chemlib-hazards':'Rejected thermal transfer emits no accepted heat event.',
'better-compat-fixes':'Pinned optional Curios hook remains version-scoped and does not intercept unrelated removals.',
'better-cooking':'Incomplete culinary catalogue remains unqualified/pass-through; dormant native serving is not shipped gameplay.',
'better-craftable-bouquets':'Potted bouquet recipe resolves and preserves bouquet data on placement.',
'better-create-kinetic-loss':'Config changes invalidate cached kinetic loss instead of reusing stale snapshot.',
'better-create-train-fuel':'Train speed converts blocks/tick to blocks/second at the exact factor.',
'better-deaths-door':'Five native entry losses reach a bounded health floor without a duplicate final debit.',
'better-dimension-fonts':'Shared Font run remains until the last participant returns; smoke command alone is narrow evidence.',
'better-discovery-guides':'A prompt does not remain visible when no discovery remains unread.',
'better-drinking-water':'Tank transfer conserves exactly the extracted volume and purity metadata.',
'better-electrochemistry':'Native low-voltage/idle batch does not claim product before an energized productive batch.',
'better-exploration-load-control':'The control must never override a user-disabled setting.',
'better-gameplay-notices':'A visible prompt follows the authored reminder interval only after display.',
'better-hexerei-dynamic-trees':'Native replacement cancels static Hexerei feature when valid species exists.',
'better-industrial-heat':'Create tank construction does not probe uninitialized PneumaticCraft heat capability.',
'better-journal-inventory':'Journal book panel bounds use native origin rather than Curios main-frame offsets.',
'better-magic-chunk-anchors':'Ticket state follows actual power, redstone and removal transitions.',
'better-malum-dynamic-trees':'Soulwood growth delegates through the native Malum blight generator.',
'better-manufacturing':'Native settings extraction preserves distinct limb pull and ejector velocity values.',
'better-mining-lamp':'The registered helmet retains its own durability and effective recipe in native registry.',
'better-oc2r-create-controls':'Stress updates emit only when values move.',
'better-oc2r-wireless-messaging':'Wireless poll clamps max items and leaves unread messages for next poll.',
'better-ore-geology':'Disabled placed feature resources are actually present for static validation.',
'better-pillager-campaigns':'Terrain atlas retains sampled cells after actual chunk unload without forcing reload.',
'better-player-traces':'Deferred annotation edits coalesce and atomically yield a slot to removal.',
'better-rail-beetle':'Planner returns seven bounded, diverse cave-floor routes from the fixture.',
'better-regolith-farming':'Farmland block variants each have an owned block-item drop definition.',
'better-rehooked-grappling':'Grapnel edge forgiveness is bounded, not an unlimited catch.',
'better-rpg-progression':'Death clone retains preferences while wiping per-life state on the replacement.',
'better-runtime-diagnostics':'Pure logging sink enforces a 20-record burst and does not rearm from reset.',
'better-seasonal-daylight':'Full equinox cycle advances exactly the configured simulation ticks without rewinding day time.',
'better-seasonal-trees':'Evergreen and out-of-biome trees remain on native growth path.',
'better-settlement-roads':'Grassy road surface selects dirt path rather than generic stone.',
'better-spaceflight':'Adapter rejects incompatible Nether-height section layout before native copy; no playable flight claim.',
'better-spawns':'Absent World Management forces spawn-only selection without class or Embark grants.',
'better-spicy-farmers':'Prepared ingredient forms conserve a canonical cooking batch.',
'better-spirit-commerce':'Credited kill releases mapped Malum spirit at victim immediately.',
'better-survival-hud':'Selected hotbar slot and offhand remain visible while hidden slots do not.',
'better-survival-physiology':'Optional metabolic groups are explicitly enumerated, not generic effect guesses.',
'better-tinkers-loot-affixes':'Native cache use consumes one cache and yields one stamped part (fixture-given cache).',
'better-village-fortifications':'Biome style priority selects expected wall style and falls back to default.',
'better-weathering-offline-progress':'Icicle sampler refuses boundary sampling when neighbor chunk is unloaded.',
'better-wild-fruit-trees':'Edible fruit products remain registered and mature coconut harvest produces falling product.',
'better-wildfire':'Absent blockstate never triggers broad texture scan.',
'scalable-tnt':'Persisted TNT strength clamps at configured boundary and corrupt values fall back to vanilla.',
'better-content-modpack':'Pack static KubeJS contract generates exactly ten distinct-family grout recipes and rejects retired intermediate routes; installed recipe manager and acquisition remain unverified.',
}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--coverage', required=True, type=Path)
    parser.add_argument('--sources-root', required=True, type=Path)
    parser.add_argument('--pack-root', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    coverage = json.loads(args.coverage.read_text())
    assert coverage['schema'] == 'bc.playtest_reference.v1' and len(coverage['sources']) == 53
    assert set(SUMMARIES) == {row['repository'] for row in coverage['sources']} | {'better-content-modpack'}
    records = []
    for row in coverage['sources']:
        repo = row['repository']
        candidate = row['promise_oracle_candidates'][0]
        if repo in OVERRIDES:
            relative, method, needle, boundary = OVERRIDES[repo]
            path = args.sources_root / repo / relative
            lines = path.read_text(encoding='utf-8').splitlines()
            declaration = [i for i, line in enumerate(lines) if method + '(' in line]
            assert declaration, f'missing actual method: {repo}/{method}'
            nearby = [(i, lines[i]) for i in range(declaration[0], min(declaration[0] + 85, len(lines))) if needle in lines[i]]
            assert nearby, f'missing named oracle: {repo}/{needle}'
            oracle_line, expression = nearby[0]
            test = dict(candidate['test'], source=relative, method=method, line=declaration[0] + 1)
            oracle = dict(path=relative, line=oracle_line + 1, expression=expression.strip())
        else:
            test = candidate['test']
            relative = test['source']
            path = args.sources_root / repo / relative
            lines = path.read_text(encoding='utf-8').splitlines()
            assert test['method'] in '\n'.join(lines[max(0, test['line'] - 3):test['line'] + 2])
            oracle_line = candidate['oracle_source_line']
            assert 1 <= oracle_line <= len(lines)
            oracle = dict(path=relative, line=oracle_line, expression=lines[oracle_line - 1].strip())
            boundary = 'narrow source contract; full-pack capability, registration, acquisition and scale are separate'
        records.append(dict(repository=repo, assessment=row['assessment'], source_sha256=digest(path),
            shipped_promise_excerpt=row['assessment_promise_excerpt'], reviewed_oracle_summary=SUMMARIES[repo],
            test=test, oracle=oracle, layer=test['kind'], boundary=boundary,
            state='source-level mapping only; not executed or source-to-build-71-attested'))
    pack_test = args.pack_root / 'src/test/kotlin/com/bettercontent/tests/GroutRecipeContractTest.kt'
    pack_lines = pack_test.read_text().splitlines()
    method = 'distinctOverworldRockPairsProduceOrdinaryGrout'
    invocation = next(i for i, line in enumerate(pack_lines) if method in line)
    oracle = next(i for i, line in enumerate(pack_lines) if 'unexpected grout recipe count' in line)
    records.append(dict(repository='better-content-modpack', assessment=None,
        source_sha256=digest(pack_test), shipped_promise_excerpt='Active pack Hand Workshop grout route static policy.',
        reviewed_oracle_summary=SUMMARIES['better-content-modpack'],
        test=dict(source=str(pack_test.relative_to(args.pack_root)), method=method, line=invocation + 1, kind='unit'),
        oracle=dict(path=str(pack_test.relative_to(args.pack_root)), line=oracle + 1, expression=pack_lines[oracle].strip()),
        layer='unit', boundary='Node KubeJS mock; effective installed recipe and natural acquisition not proved',
        state='source-level mapping only; not executed or source-to-build-71-attested'))
    assert len(records) == 54 and len({row['repository'] for row in records}) == 54
    document = dict(schema='bc.playtest_repository_oracle_review.v1', status='source-review-only',
                    reference=dict(path=str(args.coverage.resolve()), sha256=digest(args.coverage)),
                    reviewed_narrow_mappings=54, complete_packaged_promises=0, executed_candidate_bound_assertions=0,
                    records=records)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x') as output:
        json.dump(document, output, indent=2); output.write('\n')
    print(f'Authored {len(records)} narrow source-method/oracle mappings; zero test runs or packaged promises.')

if __name__ == '__main__': main()

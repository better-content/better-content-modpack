#!/usr/bin/env python3
"""Inert admission fault tests. Synthetic parser fixtures never become native evidence."""
import copy
import hashlib
import json
import pathlib
import struct
import tempfile
import unittest
import zlib
from contextlib import contextmanager
from unittest.mock import patch
import debug_coverage as gate
import debug_source_registration as registration

ROOT = pathlib.Path(__file__).resolve().parents[1]


@contextmanager
def scratch():
    (ROOT / 'build/tmp').mkdir(parents=True, exist_ok=True)
    yield pathlib.Path(tempfile.mkdtemp(prefix='coverage-parser-', dir=ROOT / 'build/tmp'))
    # Registered build outputs are retained; task closure is not a purge.


def png(width=800, height=600):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes((width * 4 + 1) * height))) + chunk(b'IEND', b''))


class CoverageTest(unittest.TestCase):
    def test_active_manifest_inventory_is_total_and_unique(self):
        doc = gate.manifest(ROOT)
        self.assertGreater(len(doc['boundaries']), len(doc['scenarios']))
        self.assertTrue(any(e['coverage'] == 'native' for e in doc['boundaries']))
        self.assertTrue(any(e['coverage'] == 'source' for e in doc['boundaries']))

    def test_inventory_rows_are_not_executable_owner_coverage(self):
        with scratch() as root:
            (root / 'gradle').mkdir()
            doc = gate.manifest(ROOT)
            keep = doc['boundaries'][0]['owners'][0]
            doc['boundaries'] = [b for b in doc['boundaries'] if keep not in b['owners']]
            (root / 'gradle/debug-interactions.json').write_text(json.dumps(doc))
            (root / 'gradle/active-custom-mods.json').write_bytes((ROOT / 'gradle/active-custom-mods.json').read_bytes())
            with self.assertRaisesRegex(ValueError, 'concrete boundary inventory'):
                gate.manifest(root)

    def test_duplicate_keys_rejected(self):
        with scratch() as root:
            path = root / 'duplicate.json'
            path.write_text('{"schema":1,"schema":2}')
            with self.assertRaisesRegex(ValueError, 'duplicate'):
                gate.load(path)

    def test_empty_run_and_uncovered_boundary_never_pass(self):
        with scratch() as root:
            (root / 'gradle').mkdir()
            doc = gate.manifest(ROOT)
            # Explicit test-only gap, regardless of the production inventory's completion.
            doc['boundaries'][0].update(coverage='uncovered', scenarios=[])
            (root / 'gradle/debug-interactions.json').write_text(json.dumps(doc))
            (root / 'gradle/active-custom-mods.json').write_bytes((ROOT / 'gradle/active-custom-mods.json').read_bytes())
            result = gate.verify(root, '20261020T120000Z-123')
            self.assertEqual(result['status'], 'failed')
            self.assertTrue(any('missing required native scenario' in f for f in result['failures']))
            self.assertTrue(any('uncovered high-risk' in f for f in result['failures']))

    def test_selection_drift_and_bad_digest_rejected(self):
        rows = [dict(type='candidate_selected', client_sha256='a' * 64, server_sha256='b' * 64)]
        self.assertEqual(gate.binding(rows)['client_sha256'], 'a' * 64)
        rows.append(dict(rows[0], client_sha256='c' * 64))
        with self.assertRaisesRegex(ValueError, 'changed'):
            gate.binding(rows)
        rows[0]['client_sha256'] = 'not-a-hash'
        with self.assertRaises(ValueError):
            gate.binding(rows[:1])

    def test_canonical_complete_reuse_is_not_cold_baseline_requirement(self):
        with scratch() as root:
            archive = root / '.worklane/testing-sessions/completed'
            archive.mkdir(parents=True)
            mods = {'a': dict(verification_id='origin-a', sha256='a' * 64, support_hashes={'fixture.jar':'b' * 64}),
                    'b': dict(verification_id='origin-b', sha256='c' * 64, support_hashes={})}
            receipt = dict(schema='bc.testing_session.v2', active_run=None, mods=mods, checkpoints={'a':mods['a']})
            path = archive / 'attempt.json'
            path.write_text(json.dumps(receipt))
            observed = dict(affected_providers=[], verification_origins={k:v['verification_id'] for k,v in mods.items()})
            with patch.object(gate.testing_session, 'inspect_live', return_value=observed):
                admitted = gate.canonical_source_attempt(root, mods)
                self.assertEqual(admitted['verification_origins']['b'], 'origin-b')
                for field, value in [('verification_id','stale'), ('sha256','d' * 64), ('support_hashes',{})]:
                    stale = copy.deepcopy(receipt)
                    stale['mods']['a'][field] = value
                    path.write_text(json.dumps(stale))
                    with self.assertRaisesRegex(ValueError, 'no immutable canonical'):
                        gate.canonical_source_attempt(root, mods)
                path.write_text(json.dumps(receipt))
            for stale in [dict(affected_providers=['a'],verification_origins={}), dict(affected_providers=[],verification_origins={'a':'wrong','b':'origin-b'})]:
                with patch.object(gate.testing_session,'inspect_live',return_value=stale):
                    with self.assertRaisesRegex(ValueError,'no immutable canonical'):
                        gate.canonical_source_attempt(root,mods)

    def test_review_faults_and_exact_case_binding(self):
        with scratch() as run:
            image, observation = run / 'unit.png', run / 'unit.json'
            image.write_bytes(png())
            binding = dict(client_sha256='a' * 64, server_sha256='b' * 64, support_sha256='c' * 64)
            obs = dict(run=run.name, label='paired', framebuffer=image.name, screen_class='NativeScreen', menu_class='NativeMenu',
                       candidate_binding=binding, framebuffer_width=800, framebuffer_height=600, gui_scale=2)
            observation.write_text(json.dumps(obs))
            review = dict(schema='bc.native_visual_review.v1',run_id=run.name,binding=binding,
                reviewer=dict(kind='llm',identity='SYNTHETIC UNIT TEST'),images=[dict(case='paired',path=str(image),sha256=gate.sha(image),
                dimensions=[800,600],native_screen='NativeScreen',native_menu='NativeMenu',gui_scale=2,capture_observation=str(observation),
                findings='Synthetic parser fixture, never runtime acceptance',criteria={'labels':dict(verdict='passed',finding='unit test')},verdict='passed')])
            gate.validate_review(review,run,binding,{'paired':['labels']})
            mutations = [lambda r:r.update(run_id='stale'),lambda r:r.update(binding={}),lambda r:r.update(images=[]),
                lambda r:r['images'][0].update(sha256='d' * 64),lambda r:r['images'][0].update(native_menu='Other'),
                lambda r:r['images'][0].update(criteria={}),lambda r:r['images'][0].update(gui_scale=3),lambda r:r['images'][0].update(verdict='failed')]
            for mutation in mutations:
                changed = copy.deepcopy(review); mutation(changed)
                with self.assertRaises(ValueError): gate.validate_review(changed,run,binding,{'paired':['labels']})
            obs['label']='other-case';observation.write_text(json.dumps(obs))
            with self.assertRaisesRegex(ValueError,'run/case'): gate.validate_review(review,run,binding,{'paired':['labels']})
            obs['label']='paired';observation.write_text(json.dumps(obs))
            image.write_bytes(png()[:24]); review['images'][0]['sha256']=gate.sha(image)
            with self.assertRaisesRegex(ValueError,'truncated'): gate.validate_review(review,run,binding,{'paired':['labels']})

    def test_real_annotation_and_default_namespace_are_mandatory(self):
        with scratch() as repo:
            path=repo/'src/main/java/NativeTest.java';path.parent.mkdir(parents=True)
            native='@net.minecraft.gametest.framework.GameTest'
            holder='@net.minecraftforge.gametest.GameTestHolder("unit")'
            source=holder+' public class NativeTest { '+native+' public static void law(GameTestHelper h) { h.succeed(); } }'
            build='runs { create("gameTestServer") { property("forge.enabledGameTestNamespaces", "unit"); mods { source(sourceSets.main.get()) } } }'
            (repo/'build.gradle.kts').write_text(build);path.write_text(source)
            test=dict(path=str(path.relative_to(repo)),method='law')
            log='> Task :runGameTestServer\nAll 1 required tests passed\n'
            self.assertEqual(registration.inspect(repo,test,'unit',log)['namespace'],'unit')
            for changed in [source.replace(native+' public','public'),source.replace(native+' public',native+'(required=false) public'),
                            source.replace('public class','@org.junit.jupiter.api.Disabled public class'),source.replace(holder,''),
                            source.replace('h.succeed();','assumeTrue(false); h.succeed();')]:
                path.write_text(changed)
                with self.assertRaises(ValueError): registration.inspect(repo,test,'unit',log)
            path.write_text(source)
            (repo/'build.gradle.kts').write_text(build.replace('"unit"','"disabled_namespace"'))
            with self.assertRaises(ValueError): registration.inspect(repo,test,'unit',log)
            (repo/'build.gradle.kts').write_text(build)
            with self.assertRaises(ValueError): registration.inspect(repo,test,'unit','> Task :runGameTestServer SKIPPED\nAll 1 required tests passed\n')

    def test_conditional_junit_or_unannotated_helper_never_qualifies(self):
        with scratch() as repo:
            path=repo/'src/test/java/UnitTest.java';path.parent.mkdir(parents=True)
            source='import org.junit.jupiter.api.Test;\npublic class UnitTest { @Test void law() { assertTrue(true); } }'
            path.write_text(source);(repo/'build.gradle.kts').write_text('tasks.test { useJUnitPlatform() }')
            test=dict(path=str(path.relative_to(repo)),method='law')
            self.assertEqual(registration.inspect(repo,test,'unit','> Task :test\n')['kind'],'junit')
            for changed in [source.replace('@Test',''),source.replace('@Test','@EnabledIfEnvironmentVariable @Test'),
                            source.replace('assertTrue(true);','assumeTrue(false);'),source.replace('assertTrue(true);','String fake="@Test void other()";') .replace('@Test void law','void law')]:
                path.write_text(changed)
                with self.assertRaises(ValueError): registration.inspect(repo,test,'unit','> Task :test\n')

    def test_finite_generator_requires_reviewed_body_enum_and_native_count(self):
        with scratch() as repo:
            path=repo/'src/main/java/NativeTest.java';path.parent.mkdir(parents=True)
            body='{ return java.util.List.of(new TestFunction("batch","law","unit:blank",100,0,true,h -> h.succeed())); }'
            source='@net.minecraftforge.gametest.GameTestHolder("unit") public class NativeTest { @net.minecraft.gametest.framework.GameTestGenerator public static Collection<TestFunction> laws() '+body+' }'
            path.write_text(source);enum=path.with_name('Cases.java');enum.write_text('enum Cases { LAW }')
            (repo/'build.gradle.kts').write_text('runs { create("gameTestServer") { property("forge.enabledGameTestNamespaces", "unit"); mods { source(sourceSets.main.get()) } } }')
            generator=dict(body_sha256=hashlib.sha256(registration.block(body,0)[0].encode()).hexdigest(),enum_path=str(enum.relative_to(repo)),enum_sha256=gate.sha(enum),required_function_ids=['law'],required_namespace_minimum=1)
            test=dict(path=str(path.relative_to(repo)),method='laws',generator=generator);log='> Task :runGameTestServer\nAll 1 required tests passed\n'
            self.assertEqual(registration.inspect(repo,test,'unit',log)['kind'],'forge-generator')
            for changed in [source.replace(',true,',',false,'),source.replace('@net.minecraft.gametest.framework.GameTestGenerator','')]:
                path.write_text(changed)
                with self.assertRaises(ValueError): registration.inspect(repo,test,'unit',log)
            path.write_text(source);enum.write_text('enum Cases { LAW, UNREVIEWED }')
            with self.assertRaises(ValueError): registration.inspect(repo,test,'unit',log)
            enum.write_text('enum Cases { LAW }');generator['required_namespace_minimum']=2
            with self.assertRaises(ValueError): registration.inspect(repo,test,'unit',log)

    def test_kotlin_alias_needs_actual_framework_mapping(self):
        with scratch() as repo:
            path=repo/'src/test/kotlin/UnitTest.kt';path.parent.mkdir(parents=True)
            path.write_text('import kotlin.test.Test\nclass UnitTest { @Test fun law() { assertTrue(true) } }')
            build='plugins { kotlin("jvm") version "1.9.22" }; dependencies { testImplementation(kotlin("test")); testImplementation("org.junit.jupiter:junit-jupiter:5.10.2") }; tasks.test { useJUnitPlatform() }'
            (repo/'build.gradle.kts').write_text(build);test=dict(path=str(path.relative_to(repo)),method='law')
            self.assertEqual(registration.inspect(repo,test,'unit','> Task :test\n')['kind'],'junit')
            for changed in [build.replace('useJUnitPlatform()','useJUnit()'),build.replace('1.9.22','unknown'),build+' resolutionStrategy { force("kotlin-test-junit") }']:
                (repo/'build.gradle.kts').write_text(changed)
                with self.assertRaises(ValueError): registration.inspect(repo,test,'unit','> Task :test\n')

    def test_closed_target_plan_has_executable_cases_setup_and_scope(self):
        doc=gate.manifest(ROOT)
        target=gate.target_catalog(ROOT,doc,'debug','plant-drinks')
        self.assertEqual(target['executable_scenarios'][0]['method'],'nativePlantDrinkUse')
        self.assertTrue(target['setup'] and target['acceptance_scope'] and target['candidate_binding_required'])
        for tier,name in [('debug','invented-target'),('dev','plant-drinks')]:
            with self.assertRaises(ValueError): gate.target_catalog(ROOT,doc,tier,name)

    def test_shader_cases_use_native_phase_and_signal_not_sequence_labels(self):
        obs=dict(label='shader-palette-3-true-991',native_day=False,native_night=True,third_party_shader_state={'enabled':True})
        self.assertEqual(gate.capture_case(obs),'shader-3-signal-night-frame')
        with self.assertRaises(ValueError): gate.capture_case(dict(obs,native_day=True))
        with self.assertRaises(ValueError): gate.capture_case(dict(obs,third_party_shader_state={'enabled':False}))

    def test_symlink_evidence_rejected(self):
        with scratch() as run:
            (run / 'target').write_text('x');(run / 'alias').symlink_to(run / 'target')
            with self.assertRaisesRegex(ValueError,'symlink'): gate.regular(run / 'alias',run)
            with self.assertRaisesRegex(ValueError,'traversal'): gate.regular(run / '..' / run.name / 'target',run)


if __name__ == '__main__':
    unittest.main()

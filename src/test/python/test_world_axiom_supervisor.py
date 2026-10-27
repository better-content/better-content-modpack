"""Pure full-wrapper regressions: real shell + projector, fake Forge/archive processes. NOT native acceptance."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

PACK = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location('axiom_projection', PACK / 'world-axioms.py')
AXIOMS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AXIOMS)
TEMP = Path('/home/dev/.tmp')
TEMP.mkdir(exist_ok=True)


class SupervisorFixture:
    def __init__(self, mode='success', laws=True, deep=False):
        self.root = Path(tempfile.mkdtemp(prefix='world-axiom-supervisor-', dir=TEMP))
        self.mode = mode
        self.control = self.root / '.better_world_management/control'
        self.control.mkdir(parents=True)
        for directory in ('mods', 'config', 'world/data/better_world_management'):
            (self.root / directory).mkdir(parents=True, exist_ok=True)
        for name in ('better-world-management-server.sh', 'world-axioms.py'):
            shutil.copy2(PACK / name, self.root / name)
        (self.root / 'mods/better-world-management-fixture.jar').write_bytes(b'PROCESS MOCK ONLY')
        (self.root / 'config/better_world_management-biomes.txt').write_text('minecraft:plains\n')
        (self.root / 'server.properties').write_text('level-name=world\nlevel-seed=7\nlevel-type=minecraft:normal\n')
        (self.root / 'world/level.dat').write_bytes(b'MOCK OLD LEVEL DATA')
        self.tsv(self.root / '.better_world_management/lineage-v5.tsv', 'BC_PRESTIGE_LINEAGE_V5',
                 lineage='lineage-test', total_prestiges=0, generation=0)
        identity = dict(lineage='lineage-test', base_generation=0, transaction='tx-test', world='world', old_seed=7,
                        biome_1='minecraft:plains', biome_2='-', biome_3='-')
        self.tsv(self.control / 'reset-request-v5.tsv', 'BC_PRESTIGE_RESET_V5', state='committed', **identity, seed_mode='random')
        self.tsv(self.root / 'world/data/better_world_management/reset-binding-v5.tsv', 'BC_PRESTIGE_WORLD_BINDING_V5', **identity)
        self.tsv(self.control / 'reset-perks-v2.tsv', 'BC_PRESTIGE_RESET_PERKS_V2', lineage='lineage-test', base_generation=0,
                 target_generation=1, transaction='tx-test', perks='biome_selection', biome_1='minecraft:plains', biome_2='-', biome_3='-')
        build = dict(schema='world_axioms_v1', phase='committed', lineage='lineage-test', base_generation=0, revision=0,
                     transaction='tx-test', source_world='world', selected=[], configuration={}, payloads={}, nominators={})
        if deep:
            build['selected'] = ['deep_compact']
            build['configuration'] = {'deep_compact': AXIOMS.config('deep_compact', {'band': 'shallow'})}
            # Synthetic owner-jar input exercises the REAL projection CLI, not native ore generation.
            native = {'feature': 'better_ore_geology:ironstone', 'placement': [
                {'type': 'minecraft:count', 'count': 3},
                {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform',
                 'min_inclusive': {'absolute': -48}, 'max_inclusive': {'absolute': 16}}}]}
            with zipfile.ZipFile(self.root / 'mods/better-ore-geology-fixture.jar', 'w') as jar:
                for variant in ('home', 'echo'):
                    jar.writestr('data/better_ore_geology/worldgen/placed_feature/ironstone_' + variant + '.json', json.dumps(native))
        build['sha256'] = hashlib.sha256(AXIOMS.compact(build)).hexdigest()
        self.build = build
        if laws:
            (self.control / 'world-axiom-reset-v1.json').write_bytes(AXIOMS.encoded(build))
        for name in ('world-axiom-draft-v1.json', 'world-axiom-staged-v1.json'):
            (self.control / name).write_text('draft evidence\n')
        self.java = self.root / 'java-mock'
        self.java.write_text('''#!/usr/bin/env python3
import pathlib,sys
args=sys.argv[1:]
if args==['-version']:
 print('openjdk version "17.0.17"',file=sys.stderr)
elif len(args)>5 and args[3]=='manifest':
 pathlib.Path(args[5]).write_text('MOCK ARCHIVE MANIFEST - NOT NATIVE VERIFICATION\\n')
''')
        self.java.chmod(0o755)
        launcher = self.root / 'run-forge.sh'
        launcher.write_text('''#!/usr/bin/env python3
import json,os,pathlib,time
root=pathlib.Path(__file__).parent
control=root/'.better_world_management/control'
request=control/'successor-request-v5.tsv'
with (root/'boots.jsonl').open('a') as out:
 out.write(json.dumps({'successor':request.exists(),'runtime':json.loads((root/'kubejs/config/world_axioms_runtime.json').read_text())})+'\\n')
if not request.exists(): raise SystemExit(0)
values=dict(line.split('\\t',1) for line in request.read_text().splitlines()[1:])
def tsv(path,magic,pairs): path.write_text(magic+'\\n'+''.join(k+'\\t'+str(v)+'\\n' for k,v in pairs))
world=root/'world'
assert (world/'data/better_world_management/world-axioms-v1.json').is_file(), 'prepare must precede Forge boot'
(world/'level.dat').write_bytes(b'MOCK NEW LEVEL DATA')
mode=os.environ.get('MOCK_MODE','success')
pairs=[('lineage',values['lineage']),('base_generation',values['base_generation']),('target_generation',values['target_generation']),
 ('transaction',values['transaction']),('successor_seed',values['successor_seed']),('actual_seed',values['successor_seed']),
 ('requested_biome_1',values['biome_1']),('requested_biome_2',values['biome_2']),('requested_biome_3',values['biome_3']),
 ('resolved_biome',values['biome_1']),('actual_biome',values['biome_1']),('attempt',values['attempt']),('world','world'),
 ('level_dat','true'),('fresh_players','true'),('status','healthy')]
if mode!='missing_legacy_health': tsv(control/'health-result-v5.tsv','BC_PRESTIGE_HEALTH_V5',pairs)
tsv(control/'perk-health-v3.tsv','BC_PRESTIGE_PERK_HEALTH_V3',[
 ('lineage',values['lineage']),('base_generation',values['base_generation']),('target_generation',values['target_generation']),
 ('transaction',values['transaction']),('attempt',values['attempt']),('resolved_biome',values['biome_1']),('spawn_x',0),('spawn_y',80),('spawn_z',0)])
build=json.loads((world/'data/better_world_management/world-axioms-v1.json').read_text())
health=dict(schema='world_axiom_health_v1',status='healthy',lineage=build['lineage'],transaction=build['transaction'],
 generation=build['base_generation']+1,build_sha256=build['sha256'])
if mode=='bad_axiom_digest': health['build_sha256']='0'*64
if mode!='missing_axiom_health' and not (mode=='retry_success' and values['attempt']=='1'): (control/'world-axiom-health-v1.json').write_text(json.dumps(health)+'\\n')
while request.exists() and not (control/'shutdown-request-v5.tsv').exists(): time.sleep(0.02)
''')
        launcher.chmod(0o755)

    @staticmethod
    def tsv(path, magic, **values):
        path.write_text(magic + '\n' + ''.join(str(k) + '\t' + str(v) + '\n' for k, v in values.items()))

    def run(self, interrupt=None):
        env = os.environ.copy()
        env.update(BC_JAVA=str(self.java), PRESTIGE_HEALTH_TIMEOUT_SECONDS='1', PRESTIGE_HEALTH_STABILITY_SECONDS='0',
                   PRESTIGE_MIN_FREE_RESERVE_BYTES='1', MOCK_MODE=self.mode, TMPDIR=str(TEMP))
        if interrupt:
            env['PRESTIGE_TEST_INTERRUPT_AT'] = interrupt
        else:
            env.pop('PRESTIGE_TEST_INTERRUPT_AT', None)
        invocation = self.root / ('invocation-' + str(len(list(self.root.glob('invocation-*.log')))) + '.log')
        with invocation.open('w') as output:
            result = subprocess.run(['bash', str(self.root / 'better-world-management-server.sh')], input='', text=True,
                                    stdout=output, stderr=output, env=env, timeout=65)
        result.stdout = ''
        result.stderr = invocation.read_text()
        with (self.root / 'test-invocations.log').open('a') as log:
            log.write(f'EXIT {result.returncode} INTERRUPT {interrupt}\n{result.stdout}\n{result.stderr}\n')
        return result

    @property
    def tx(self):
        return self.root / '.better_world_management/transactions/tx-test'

    def generation(self):
        return (self.root / '.better_world_management/lineage-v5.tsv').read_text().splitlines()[-1]


class WorldAxiomSupervisorTests(unittest.TestCase):
    def test_ordinary_boot_restores_projection_before_launcher(self):
        fixture = SupervisorFixture()
        for name in ('reset-request-v5.tsv', 'reset-perks-v2.tsv', 'world-axiom-reset-v1.json'):
            (fixture.control / name).unlink()
        (fixture.root / 'kubejs/config').mkdir(parents=True)
        (fixture.root / AXIOMS.RUNTIME).write_text('{"selected":["iron_law"],"generation":99}')
        (fixture.root / 'server.properties').write_text('level-name=world\nlevel-seed=7\nlevel-type=minecraft:large_biomes\n')
        result = fixture.run()
        self.assertEqual(result.returncode, 0, result.stderr)
        boot = json.loads((fixture.root / 'boots.jsonl').read_text())
        self.assertFalse(boot['successor'])
        self.assertEqual(boot['runtime']['generation'], 0)
        self.assertEqual(boot['runtime']['selected'], [])
        self.assertIn('level-type=minecraft:normal', (fixture.root / 'server.properties').read_text())

    def test_legacy_extra_line_still_rejected(self):
        fixture = SupervisorFixture()
        with (fixture.control / 'reset-request-v5.tsv').open('a') as stream:
            stream.write('extra\tforbidden\n')
        result = fixture.run()
        self.assertNotEqual(result.returncode, 0, result.stderr)
        self.assertIn('wrong line count', result.stderr)
        self.assertFalse((fixture.root / 'boots.jsonl').exists())

    def test_retry_prepares_same_frozen_laws_for_new_seed(self):
        fixture = SupervisorFixture(mode='retry_success')
        result = fixture.run()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(fixture.generation(), 'generation\t1')
        self.assertTrue((fixture.tx / 'failed-attempt-1/level.dat').exists())
        self.assertTrue((fixture.tx / 'failed-attempt-1-world-axiom-runtime-v1.json').exists())
        boots = [json.loads(line) for line in (fixture.root / 'boots.jsonl').read_text().splitlines()]
        self.assertEqual(len(boots), 2)
        self.assertTrue(all(boot['runtime']['build_sha256'] == fixture.build['sha256'] for boot in boots))

    def test_lineage_written_resume_promotes_without_double_generation(self):
        fixture = SupervisorFixture()
        interrupted = fixture.run('lineage-written')
        self.assertEqual(interrupted.returncode, 86, interrupted.stderr)
        self.assertEqual(fixture.generation(), 'generation\t1')
        result = fixture.run()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(fixture.generation(), 'generation\t1')
        self.assertFalse((fixture.control / 'world-axiom-reset-v1.json').exists())

    def test_committed_cleanup_resume_without_control_health(self):
        fixture = SupervisorFixture()
        interrupted = fixture.run('committed-cleanup-before-reset-release')
        self.assertEqual(interrupted.returncode, 86, interrupted.stderr)
        self.assertFalse((fixture.control / 'world-axiom-health-v1.json').exists())
        result = fixture.run()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(fixture.generation(), 'generation\t1')
        self.assertFalse((fixture.control / 'world-axiom-reset-v1.json').exists())

    def test_health_verified_tampered_projection_is_rejected(self):
        fixture = SupervisorFixture()
        interrupted = fixture.run('health-verified')
        self.assertEqual(interrupted.returncode, 86, interrupted.stderr)
        (fixture.root / AXIOMS.RUNTIME).write_text('{"schema":"world_axioms_runtime_v1","selected":["unknown"]}')
        result = fixture.run()
        self.assertNotEqual(result.returncode, 0, result.stderr)
        self.assertEqual(fixture.generation(), 'generation\t0')

    def test_unknown_or_wrong_identity_laws_fail_before_staging(self):
        for mutation in ('unknown', 'lineage', 'transaction', 'world', 'checksum'):
            with self.subTest(mutation=mutation):
                fixture = SupervisorFixture()
                build = dict(fixture.build)
                if mutation == 'unknown': build['selected'] = ['unapproved_world_law']
                elif mutation == 'lineage': build['lineage'] = 'wrong-lineage'
                elif mutation == 'transaction': build['transaction'] = 'wrong-tx'
                elif mutation == 'world': build['source_world'] = 'otherworld'
                del build['sha256']
                build['sha256'] = hashlib.sha256(AXIOMS.compact(build)).hexdigest() if mutation != 'checksum' else '0' * 64
                (fixture.control / 'world-axiom-reset-v1.json').write_bytes(AXIOMS.encoded(build))
                result = fixture.run()
                self.assertNotEqual(result.returncode, 0, result.stderr)
                self.assertTrue((fixture.root / 'world/level.dat').is_file())
                self.assertFalse((fixture.root / 'boots.jsonl').exists())

    def test_legacy_health_still_mandatory(self):
        fixture = SupervisorFixture(mode='missing_legacy_health')
        result = fixture.run()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(fixture.generation(), 'generation\t0')
        self.assertEqual((fixture.root / 'world/level.dat').read_bytes(), b'MOCK OLD LEVEL DATA')
        self.assertEqual(len(list(fixture.tx.glob('failed-attempt-*-world-axiom-health-v1.json'))), 8)

    def test_missing_laws_fail_before_world_staging(self):
        fixture = SupervisorFixture(laws=False)
        result = fixture.run()
        self.assertNotEqual(result.returncode, 0, result.stderr)
        self.assertTrue((fixture.root / 'world/level.dat').is_file())
        self.assertFalse((fixture.tx / 'archive-input/world').exists())
        self.assertFalse((fixture.root / 'boots.jsonl').exists())

    def test_success_projects_promotes_and_cleans_controls(self):
        fixture = SupervisorFixture()
        result = fixture.run()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(fixture.generation(), 'generation\t1')
        frozen = fixture.tx / 'world-axiom-reset-v1.json'
        self.assertEqual(frozen.read_bytes(), (fixture.root / 'world' / AXIOMS.SNAPSHOT).read_bytes())
        self.assertEqual(frozen.read_bytes(), (fixture.root / '.better_world_management/world-axioms-v1.json').read_bytes())
        self.assertTrue((fixture.tx / 'world-axiom-health-v1.json').is_file())
        self.assertFalse(list(fixture.control.glob('world-axiom-*.json')))
        self.assertEqual(json.loads((fixture.root / AXIOMS.RUNTIME).read_text())['generation'], 1)

    def test_health_verified_resume_repromotes_idempotently(self):
        fixture = SupervisorFixture()
        interrupted = fixture.run('health-verified')
        self.assertEqual(interrupted.returncode, 86, interrupted.stderr)
        self.assertEqual(fixture.generation(), 'generation\t0')
        result = fixture.run()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(fixture.generation(), 'generation\t1')
        self.assertTrue((fixture.root / '.better_world_management/world-axioms-v1.json').is_file())

    def test_changed_frozen_snapshot_is_rejected_on_resume(self):
        fixture = SupervisorFixture()
        interrupted = fixture.run('request-recorded')
        self.assertEqual(interrupted.returncode, 86, interrupted.stderr)
        (fixture.tx / 'world-axiom-reset-v1.json').write_text('{}\n')
        result = fixture.run()
        self.assertNotEqual(result.returncode, 0, result.stderr)
        self.assertEqual(fixture.generation(), 'generation\t0')
        self.assertFalse((fixture.root / 'boots.jsonl').exists())

    def test_missing_world_health_rolls_back_and_restores_normal_projection(self):
        fixture = SupervisorFixture(mode='missing_axiom_health', deep=True)
        result = fixture.run()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(fixture.generation(), 'generation\t0')
        self.assertEqual((fixture.root / 'world/level.dat').read_bytes(), b'MOCK OLD LEVEL DATA')
        runtime = json.loads((fixture.root / AXIOMS.RUNTIME).read_text())
        self.assertEqual(runtime['generation'], 0)
        self.assertEqual(runtime['selected'], [])
        self.assertFalse((fixture.root / 'world' / AXIOMS.PACK).exists())
        self.assertEqual(len(list(fixture.tx.glob('failed-attempt-*/datapacks/world_axioms_v1/pack.mcmeta'))), 8)
        self.assertEqual(len(list(fixture.tx.glob('failed-attempt-*-world-axiom-runtime-v1.json'))), 8)
        self.assertFalse((fixture.control / 'world-axiom-reset-v1.json').exists())

    def test_bad_health_is_retained_for_each_failed_attempt(self):
        fixture = SupervisorFixture(mode='bad_axiom_digest')
        result = fixture.run()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(fixture.generation(), 'generation\t0')
        failures = list(fixture.tx.glob('failed-attempt-*-world-axiom-health-v1.json'))
        self.assertEqual(len(failures), 8)
        self.assertTrue(all(json.loads(path.read_text())['build_sha256'] == '0' * 64 for path in failures))


if __name__ == '__main__':
    unittest.main()

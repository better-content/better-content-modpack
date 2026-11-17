"""Pure source-only regressions for the two independently approved KC fixed wok ports."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location('pack_cooking_recipes', ROOT / 'scripts/check_cooking_recipes.py')
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


class CookingFixedPortTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.plan = json.loads((ROOT / 'kubejs/config/cooking_recipes.json').read_text())

    def test_exact_distinct_source_cost_bowl_feast_and_preserved_chopping_assets(self):
        self.assertEqual(checker.validate(self.plan, ROOT), 2)
        (first, _), (second, _) = checker.PORTS.values()
        self.assertNotEqual(first, second)
        self.assertEqual(first.count({'tag': 'forge:grain/rice'}), 3)
        self.assertEqual(second.count({'tag': 'forge:grain/rice'}), 1)

    def test_paid_carrier_or_yield_drift_fails(self):
        plan = copy.deepcopy(self.plan)
        port = plan['definitions'][next(i for i, d in enumerate(plan['definitions']) if d['id'] == 'kubejs:food_eras/market_fried_rice')]['json']
        port.pop('carrier')
        with self.assertRaisesRegex(ValueError, 'divergent rice definition|rice carrier/input/yield'):
            checker.validate(plan, ROOT)

    def test_repeated_rice_and_native_quality_scope_do_not_silently_change(self):
        plan = copy.deepcopy(self.plan)
        recipe = plan['routes']['fruitsdelight:cooking/pineapple_fried_rice']['definitions'][0]['json']
        recipe['ingredients'].pop(2)
        with self.assertRaisesRegex(ValueError, 'divergent rice definition|rice carrier/input/yield'):
            checker.validate(plan, ROOT)

    def test_old_queued_fd_route_cannot_coexist_with_new_market_port(self):
        plan = copy.deepcopy(self.plan)
        plan['pending_source_ids'].append('kubejs:food_eras/market_fried_rice')
        with self.assertRaisesRegex(ValueError, 'old queued market'):
            checker.validate(plan, ROOT)


if __name__ == '__main__':
    unittest.main()

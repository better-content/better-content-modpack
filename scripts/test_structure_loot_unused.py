#!/usr/bin/env python3
"""Controls for the exact byte/name scope of the owning unused-consumer scan."""
import io
import unittest
import warnings
import zipfile
from structure_loot_inputs import fingerprint
from structure_loot_unused import scanned_entries, sha


def archive(entries, year=2020):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as jar:
        for name, data in entries:
            info = zipfile.ZipInfo(name, (year, 1, 1, 0, 0, 0))
            jar.writestr(info, data)
    return output.getvalue()


def identity(raw):
    with zipfile.ZipFile(io.BytesIO(raw)) as jar:
        return fingerprint([[name, sha(data)] for name, data in scanned_entries(jar)])


class ScanIdentityTest(unittest.TestCase):
    def setUp(self):
        self.entries = [('mod/Example.class', b'class bytes'),
                        ('data/mod/loot_tables/chest.json', b'{"pools":[]}'),
                        ('META-INF/better-content-source.properties', b'commit=old')]

    def test_zip_time_order_and_unsearched_provenance_are_not_consumer_bytes(self):
        original = identity(archive(self.entries))
        changed = self.entries[:2] + [(self.entries[2][0], b'commit=new')]
        self.assertNotEqual(archive(self.entries), archive(list(reversed(changed)), 2026))
        self.assertEqual(original, identity(archive(list(reversed(changed)), 2026)))

    def test_every_scanned_class_and_data_byte_is_bound(self):
        original = identity(archive(self.entries))
        for index in [0, 1]:
            mutated = list(self.entries)
            name, data = mutated[index]
            mutated[index] = (name, data + b'new consumer')
            self.assertNotEqual(original, identity(archive(mutated)))

    def test_added_removed_and_renamed_scanned_entries_change_identity(self):
        original = identity(archive(self.entries))
        for mutated in [self.entries + [('other/Consumer.class', b'caller')],
                        self.entries[1:],
                        [('other/Renamed.class', self.entries[0][1])] + self.entries[1:]]:
            self.assertNotEqual(original, identity(archive(mutated)))

    def test_duplicate_scanned_entries_fail_closed(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            raw = archive(self.entries + [self.entries[0]])
        with self.assertRaisesRegex(AssertionError, 'duplicate scanned archive entry'):
            identity(raw)

    def test_scope_matches_the_actual_consumer_search(self):
        with zipfile.ZipFile(io.BytesIO(archive(self.entries))) as jar:
            self.assertEqual(['data/mod/loot_tables/chest.json', 'mod/Example.class'],
                             [name for name, _ in scanned_entries(jar)])


if __name__ == '__main__':
    unittest.main()

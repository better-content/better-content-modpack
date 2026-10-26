#!/usr/bin/env python3
"""Documentation checks use executable mod identity rather than removed agent prose."""
from contextlib import redirect_stdout, redirect_stderr
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import check_documentation as docs


class DocumentationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.workspace = Path(self.directory.name)
        self.pack = self.workspace / 'pack'
        (self.pack / 'gradle').mkdir(parents=True)
        (self.pack / 'docs').mkdir()
        for name in ('gameplay-design-choices.md', 'aspirational-features.md'):
            (self.pack / 'docs' / name).write_text('# Design\n')
        self.repo = self.workspace / 'mod_source/example'
        (self.repo / '.git').mkdir(parents=True)
        self.metadata = self.repo / 'src/main/resources/META-INF/mods.toml'
        self.metadata.parent.mkdir(parents=True)
        self.metadata.write_text('[[mods]]\nmodId="${modId}"\n[[dependencies.${modId}]]\nmodId="forge"\n')
        (self.repo / 'gradle.properties').write_text('mod_id=example\n')
        self.manifest = {'mods': [{'repository': 'example', 'modId': 'example'}]}

    def check(self):
        (self.pack / 'gradle/active-custom-mods.json').write_text(json.dumps(self.manifest))
        with patch.object(docs, 'PACK', self.pack), patch.object(docs, 'WORKSPACE', self.workspace), \
                patch.object(docs, 'files', return_value=[]), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            return docs.check([self.pack, self.repo])

    def test_removed_agent_files_and_global_guidance_are_not_required(self):
        self.assertEqual(0, self.check())

    def test_literal_and_template_provider_identity(self):
        self.assertEqual({'example'}, docs.provider_ids(self.repo))
        self.metadata.write_text('[[mods]]\nmodId="example"\n')
        self.assertEqual({'example'}, docs.provider_ids(self.repo))

    def test_dependency_identity_does_not_qualify_provider(self):
        self.manifest['mods'][0]['modId'] = 'forge'
        self.assertEqual(1, self.check())

    def test_missing_metadata_is_actionable_failure(self):
        self.metadata.unlink()
        self.assertEqual(1, self.check())

    def test_unknown_dependency_and_cycle_fail(self):
        self.manifest['mods'][0]['dependsOn'] = ['missing']
        self.assertEqual(1, self.check())
        self.manifest['mods'][0]['dependsOn'] = ['example']
        self.assertEqual(1, self.check())

    def test_current_documents_remain_required(self):
        (self.pack / 'docs/gameplay-design-choices.md').unlink()
        self.assertEqual(1, self.check())


if __name__ == '__main__':
    unittest.main()

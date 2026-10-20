package com.bettercontent.tests

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration

@Tag("fast")
class DocumentationScopeFastTest {
    @Test
    fun retainedTaskReportsAreNotMaintainedDocumentationInputs() {
        val root = Path.of(System.getProperty("bc.repo.root"))
        BoundedCommand.capture(root, listOf("python3", "-B", "-c", """
            import runpy
            from pathlib import Path
            from types import SimpleNamespace
            from unittest.mock import patch

            files = runpy.run_path('scripts/check_documentation.py')['files']
            entries = [
                'README.md', 'docs/guide.md', 'workspace_artifacts/task/report.md',
                'workspace_artifacts/task/nested/report.md', 'workspace_artifacts.md',
                'docs/workspace_artifacts/guide.md', 'docs/not-markdown.txt',
            ]
            output = ('\0'.join(entries) + '\0').encode()
            repo = Path('/synthetic-doc-scope')
            with patch('subprocess.run', return_value=SimpleNamespace(stdout=output)), \
                    patch.object(Path, 'is_file', return_value=True):
                actual = [str(p.relative_to(repo)) for p in files(repo)]
            expected = sorted([
                'README.md', 'docs/guide.md', 'workspace_artifacts.md',
                'docs/workspace_artifacts/guide.md',
            ])
            assert actual == expected, (actual, expected)
        """.trimIndent()), Duration.ofSeconds(10))
    }
}
